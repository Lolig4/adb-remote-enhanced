package com.jcapps.adbserver

import android.graphics.Rect
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Rebuild addition: live screen mirroring (like scrcpy) on its own TCP port [PORT].
 *
 * Protocol (big endian):
 * - client -> server: `token\n`, `maxSize bitRate\n`
 * - server -> client: `int width, int height`, then packets `int flags, int size, bytes`
 *   (H.264 Annex B; flags: [FLAG_CONFIG] = SPS/PPS, [FLAG_KEY] = key frame)
 * - client -> server (any time): `byte 1, byte action, int x, int y` touch in video pixels
 *   (action 0 down, 1 up, 2 move), `byte 2, int keyCode` key press.
 *
 * One client at a time; a new connection ends the previous stream.
 *
 * Capture, encoder setup and event injection follow scrcpy (Genymobile, Apache License 2.0):
 * virtual display via DisplayManager/SurfaceControl, MediaCodec surface input, InputManager.
 */
object MirrorServer {
    const val PORT = 3126
    const val FLAG_CONFIG = 1
    const val FLAG_KEY = 2

    @Volatile private var current: Socket? = null

    fun start(token: String) {
        thread(name = "mirror-accept", isDaemon = true) {
            val server = try { ServerSocket(PORT) } catch (e: IOException) { println("Mirror: port $PORT busy"); return@thread }
            while (true) {
                val socket = try { server.accept() } catch (e: IOException) { continue }
                runCatching { current?.close() }
                current = socket
                thread(name = "mirror-session", isDaemon = true) {
                    try { session(socket, token) } catch (e: Throwable) { println("Mirror: ${e.message}") }
                    finally { runCatching { socket.close() } }
                }
            }
        }
    }

    private fun session(socket: Socket, token: String) {
        socket.tcpNoDelay = true
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        if (readLine(input) != token) return
        val params = readLine(input)?.split(' ') ?: return
        val maxSize = params.getOrNull(0)?.toIntOrNull() ?: 1280
        val bitRate = params.getOrNull(1)?.toIntOrNull() ?: 4_000_000

        val info = displayInfo()
        val (w, h) = scaled(info.width, info.height, maxSize)
        val out = DataOutputStream(socket.getOutputStream())
        out.writeInt(w); out.writeInt(h); out.flush()

        val injector = Injector(info.width, info.height, w, h)
        thread(name = "mirror-control", isDaemon = true) {
            try { control(input, injector) } catch (_: IOException) { runCatching { socket.close() } }
        }
        stream(out, info, w, h, bitRate)
    }

    // ---------------------------------------------------------------- video

    private fun stream(out: DataOutputStream, info: DisplayData, w: Int, h: Int, bitRate: Int) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, 60)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10)
            // a still screen produces no frames; repeat the last one so the client keeps up
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        val display = Display.create(surface, info, w, h)
        codec.start()
        val bufferInfo = MediaCodec.BufferInfo()
        try {
            var chunk = ByteArray(0)
            while (true) {
                val index = codec.dequeueOutputBuffer(bufferInfo, 1_000_000)
                if (index < 0) continue
                val buffer = codec.getOutputBuffer(index)
                if (buffer != null && bufferInfo.size > 0) {
                    if (chunk.size < bufferInfo.size) chunk = ByteArray(bufferInfo.size)
                    buffer.position(bufferInfo.offset)
                    buffer.get(chunk, 0, bufferInfo.size)
                    var flags = 0
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) flags = flags or FLAG_CONFIG
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) flags = flags or FLAG_KEY
                    out.writeInt(flags); out.writeInt(bufferInfo.size); out.write(chunk, 0, bufferInfo.size)
                    out.flush()
                }
                codec.releaseOutputBuffer(index, false)
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            display.release()
            surface.release()
        }
    }

    private class DisplayData(val width: Int, val height: Int, val layerStack: Int)

    /** Size of display 0 via the hidden DisplayManagerGlobal (no Activity context here). */
    private fun displayInfo(): DisplayData {
        val dmg = Class.forName("android.hardware.display.DisplayManagerGlobal")
        val instance = dmg.getMethod("getInstance").invoke(null)
        val di = dmg.getMethod("getDisplayInfo", Int::class.javaPrimitiveType).invoke(instance, 0)
        val c = di.javaClass
        return DisplayData(c.getField("logicalWidth").getInt(di), c.getField("logicalHeight").getInt(di), c.getField("layerStack").getInt(di))
    }

    /** Longest side at most [maxSize], both sides multiples of 8 (encoder requirement). */
    private fun scaled(w: Int, h: Int, maxSize: Int): Pair<Int, Int> {
        val scale = minOf(1.0, maxSize.toDouble() / maxOf(w, h))
        fun r8(v: Double) = (v.toInt() + 4) / 8 * 8
        return r8(w * scale) to r8(h * scale)
    }

    /** A virtual display mirroring display 0 into the encoder surface. */
    private class Display private constructor(private val virtual: VirtualDisplay?, private val token: IBinder?) {
        fun release() {
            virtual?.release()
            if (token != null) runCatching { surfaceControl().getMethod("destroyDisplay", IBinder::class.java).invoke(null, token) }
        }

        companion object {
            fun surfaceControl(): Class<*> = Class.forName("android.view.SurfaceControl")

            fun create(surface: Surface, info: DisplayData, w: Int, h: Int): Display {
                // Android 14+: hidden static DisplayManager.createVirtualDisplay(name, w, h, displayIdToMirror, surface)
                try {
                    val m = android.hardware.display.DisplayManager::class.java.getMethod("createVirtualDisplay",
                        String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Surface::class.java)
                    return Display(m.invoke(null, "adbremote-mirror", w, h, 0, surface) as VirtualDisplay, null)
                } catch (e: Exception) {
                    if (Build.VERSION.SDK_INT >= 34) println("Mirror: createVirtualDisplay failed, trying SurfaceControl: $e")
                }
                // older: a SurfaceControl display showing display 0's layer stack
                val sc = surfaceControl()
                val secure = Build.VERSION.SDK_INT < 30 || (Build.VERSION.SDK_INT == 30 && Build.VERSION.CODENAME != "S")
                val token = sc.getMethod("createDisplay", String::class.java, Boolean::class.javaPrimitiveType)
                    .invoke(null, "adbremote-mirror", secure) as IBinder
                sc.getMethod("openTransaction").invoke(null)
                try {
                    sc.getMethod("setDisplaySurface", IBinder::class.java, Surface::class.java).invoke(null, token, surface)
                    sc.getMethod("setDisplayProjection", IBinder::class.java, Int::class.javaPrimitiveType, Rect::class.java, Rect::class.java)
                        .invoke(null, token, 0, Rect(0, 0, info.width, info.height), Rect(0, 0, w, h))
                    sc.getMethod("setDisplayLayerStack", IBinder::class.java, Int::class.javaPrimitiveType).invoke(null, token, info.layerStack)
                } finally {
                    sc.getMethod("closeTransaction").invoke(null)
                }
                return Display(null, token)
            }
        }
    }

    // ---------------------------------------------------------------- input

    private fun control(input: DataInputStream, injector: Injector) {
        while (true) {
            when (input.readByte().toInt()) {
                1 -> {
                    val action = input.readByte().toInt()
                    val x = input.readInt(); val y = input.readInt()
                    injector.touch(action, x, y)
                }
                2 -> injector.key(input.readInt())
                else -> return
            }
        }
    }

    /** Injects touches/keys like the real devices (shell has INJECT_EVENTS). */
    private class Injector(private val dw: Int, private val dh: Int, private val vw: Int, private val vh: Int) {
        private val manager: Any
        private val inject: java.lang.reflect.Method
        private var downTime = 0L

        init {
            // Android 14+: InputManagerGlobal, older: InputManager.getInstance()
            val (obj, cls) = try {
                val c = Class.forName("android.hardware.input.InputManagerGlobal")
                c.getMethod("getInstance").invoke(null) to c
            } catch (e: Exception) {
                val c = Class.forName("android.hardware.input.InputManager")
                c.getMethod("getInstance").invoke(null) to c
            }
            manager = obj!!
            inject = cls.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        }

        private fun send(event: InputEvent) {
            runCatching { inject.invoke(manager, event, 0) } // 0 = INJECT_INPUT_EVENT_MODE_ASYNC
        }

        fun touch(action: Int, x: Int, y: Int) {
            val now = SystemClock.uptimeMillis()
            val a = when (action) { 0 -> { downTime = now; MotionEvent.ACTION_DOWN }; 1 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
            val px = x.toFloat() * dw / vw
            val py = y.toFloat() * dh / vh
            val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
            val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = px; this.y = py; pressure = 1f; size = 1f })
            val e = MotionEvent.obtain(downTime, now, a, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            send(e)
            e.recycle()
        }

        fun key(code: Int) {
            val now = SystemClock.uptimeMillis()
            for (a in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                send(KeyEvent(now, now, a, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD))
            }
        }
    }

    private fun readLine(input: DataInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return null
            if (b == '\n'.code) return sb.toString()
            sb.append(b.toChar())
            if (sb.length > 512) return null
        }
    }
}
