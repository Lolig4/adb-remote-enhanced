package com.jcapps.adbremote.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.jcapps.adbremote.NativeConfig
import com.jcapps.adbremote.R
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Rebuild addition: live mirroring of the device screen (protocol: see the server's MirrorServer,
 * port 3126). Shows the H.264 stream full screen; taps and swipes on the picture go to the device,
 * the buttons send Back / Home.
 */
class MirrorActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private lateinit var status: TextView
    private lateinit var frame: FrameLayout

    @Volatile private var socket: Socket? = null
    @Volatile private var out: DataOutputStream? = null
    @Volatile private var running = false
    private var videoW = 0
    private var videoH = 0
    private val sender = Executors.newSingleThreadExecutor()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val dp = resources.displayMetrics.density
        frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        surfaceView = SurfaceView(this)
        // full size until the video size is known (a 0x0 SurfaceView never gets a surface)
        frame.addView(surfaceView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        status = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 16f; text = getString(R.string.mirror_connecting)
        }
        frame.addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
        }
        fun button(icon: Int, desc: Int, onClick: () -> Unit) = ImageButton(this).apply {
            setImageResource(icon); contentDescription = getString(desc)
            setBackgroundResource(R.drawable.bg_mirror_button)
            setColorFilter(Color.WHITE)
            alpha = 0.7f
            setOnClickListener { onClick() }
            buttons.addView(this, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()).apply { bottomMargin = (8 * dp).toInt() })
        }
        // close the stream (top), then the device's Back / Home
        button(R.drawable.ic_close, R.string.mirror_close) { finish() }.also {
            (it.layoutParams as LinearLayout.LayoutParams).bottomMargin = (32 * dp).toInt()
        }
        button(R.drawable.ic_back, R.string.back) { sendKey(KeyEvent.KEYCODE_BACK) }
        button(R.drawable.ic_home, R.string.home) { sendKey(KeyEvent.KEYCODE_HOME) }
        frame.addView(buttons, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL))
        setContentView(frame)
        surfaceView.holder.addCallback(this)
        surfaceView.setOnTouchListener { v, e -> onTouch(v, e); true }
        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitSurface() }
    }

    /** Largest rect with the video's aspect ratio, centered. */
    private fun fitSurface() {
        if (videoW == 0 || frame.width == 0) return
        val scale = minOf(frame.width.toFloat() / videoW, frame.height.toFloat() / videoH)
        val w = (videoW * scale).toInt(); val h = (videoH * scale).toInt()
        val lp = surfaceView.layoutParams as FrameLayout.LayoutParams
        if (lp.width != w || lp.height != h) { lp.width = w; lp.height = h; surfaceView.layoutParams = lp }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (running) return
        running = true
        thread(name = "mirror") { runStream(holder) }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) = stop()

    override fun onDestroy() {
        stop()
        sender.shutdownNow()
        super.onDestroy()
    }

    private fun stop() {
        running = false
        runCatching { socket?.close() }
    }

    private fun showStatus(text: String?) = runOnUiThread {
        status.text = text ?: ""
        status.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    private fun runStream(holder: SurfaceHolder) {
        val ip = PreferenceManager.getDefaultSharedPreferences(this).getString("current_device_ip", null)
        if (ip == null) { showStatus(getString(R.string.mirror_failed)); return }
        // the very first connection may fail while the server is still starting: try a few times
        var s: Socket? = null
        for (attempt in 0 until 3) {
            try {
                s = Socket().apply { connect(InetSocketAddress(ip, PORT), 4000); tcpNoDelay = true }
                break
            } catch (e: IOException) {
                if (!running) return
                Thread.sleep(700)
            }
        }
        if (s == null) { showStatus(getString(R.string.mirror_failed)); return }
        socket = s
        var codec: MediaCodec? = null
        try {
            val o = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
            out = o
            val maxSize = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels).coerceAtMost(1920)
            o.write((NativeConfig.apiInterceptorToken + "\n" + maxSize + " 6000000\n").toByteArray()); o.flush()
            val input = DataInputStream(BufferedInputStream(s.getInputStream(), 1 shl 16))
            videoW = input.readInt(); videoH = input.readInt()
            runOnUiThread { fitSurface() }
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoW, videoH)
            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, holder.surface, null, 0)
                start()
            }
            val c = codec
            // render every decoded frame right away (lowest latency)
            thread(name = "mirror-render") {
                val info = MediaCodec.BufferInfo()
                try {
                    while (running) {
                        val i = c.dequeueOutputBuffer(info, 100_000)
                        if (i >= 0) c.releaseOutputBuffer(i, true)
                    }
                } catch (_: Exception) {}
            }
            var first = true
            var buf = ByteArray(1 shl 16)
            while (running) {
                val flags = input.readInt()
                val size = input.readInt()
                if (buf.size < size) buf = ByteArray(size)
                input.readFully(buf, 0, size)
                val index = c.dequeueInputBuffer(500_000)
                if (index < 0) continue
                c.getInputBuffer(index)!!.apply { clear(); put(buf, 0, size) }
                val codecFlags = if (flags and FLAG_CONFIG != 0) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0
                c.queueInputBuffer(index, 0, size, System.nanoTime() / 1000, codecFlags)
                if (first && codecFlags == 0) { first = false; showStatus(null) }
            }
        } catch (e: Exception) {
            if (running) showStatus(getString(R.string.mirror_lost))
        } finally {
            running = false
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { s.close() }
            out = null
        }
    }

    // ---------------------------------------------------------------- input

    private fun onTouch(v: View, e: MotionEvent) {
        if (videoW == 0 || v.width == 0) return
        val action = when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> 0
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 1
            MotionEvent.ACTION_MOVE -> 2
            else -> return
        }
        val x = (e.x / v.width * videoW).toInt().coerceIn(0, videoW - 1)
        val y = (e.y / v.height * videoH).toInt().coerceIn(0, videoH - 1)
        send { it.writeByte(1); it.writeByte(action); it.writeInt(x); it.writeInt(y) }
    }

    private fun sendKey(code: Int) = send { it.writeByte(2); it.writeInt(code) }

    private fun send(block: (DataOutputStream) -> Unit) {
        if (sender.isShutdown) return
        sender.execute {
            val o = out ?: return@execute
            try { synchronized(o) { block(o); o.flush() } } catch (_: IOException) {}
        }
    }

    companion object {
        const val PORT = 3126
        const val FLAG_CONFIG = 1
    }
}
