package com.jcapps.adbremote.qa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * QA only. In-process UI driver, the counterpart of uiautomator that cannot leak into other apps:
 * every command acts on this process's own windows. Driven by tools/ar.sh:
 *   adb shell am broadcast -n com.jcapps.adbremote/.qa.DebugBridge --es cmd <cmd> --es arg <arg>
 * Commands: fg | tree | click <sel> | pref <key> | back | start <class> | scroll <dy> | shot | crash | crash-clear
 * Selector: text:<exact> | desc:<exact> | id:<entryName>
 */
class DebugBridge : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: "fg"
        val arg = intent.getStringExtra("arg") ?: ""
        val pending = goAsync()
        Thread {
            val out = try {
                if (cmd == "crash") DebugInit.crashFile?.takeIf { it.exists() }?.readText() ?: "no crash recorded"
                else if (cmd == "crash-clear") { DebugInit.crashFile?.delete(); "cleared" }
                else onMain { run(context, cmd, arg) }
            } catch (e: Throwable) {
                "ERROR " + android.util.Log.getStackTraceString(e)
            }
            pending.resultData = out
            pending.finish()
        }.start()
    }

    private fun onMain(block: () -> String): String {
        var result = "TIMEOUT"
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            result = try { block() } catch (e: Throwable) { "ERROR " + android.util.Log.getStackTraceString(e) }
            latch.countDown()
        }
        latch.await(8, TimeUnit.SECONDS)
        return result
    }

    private fun roots(): List<View> = WindowInspector.getGlobalWindowViews().filter { it.isShown }

    private fun run(context: Context, cmd: String, arg: String): String {
        val act = DebugInit.resumed
        return when (cmd) {
            "fg" -> "resumed=${act?.javaClass?.name} windows=${roots().map { it.javaClass.simpleName }}"
            "tree" -> buildString { roots().forEach { dump(it, 0, this) } }
            "click" -> {
                val v = find(arg) ?: return "NOTFOUND $arg"
                var c: View? = v
                while (c != null && !c.isClickable) c = c.parent as? View
                if (c == null) return "NOTCLICKABLE $arg"
                c.performClick(); "clicked ${describe(c)}"
            }
            "pref" -> {
                val f = (act as? FragmentActivity)?.supportFragmentManager?.let { prefFragment(it) }
                    ?: return "NO PreferenceFragment in ${act?.javaClass?.simpleName}"
                val p = f.findPreference<androidx.preference.Preference>(arg) ?: return "NOTFOUND pref $arg"
                f.scrollToPreference(p); p.performClick(); "clicked pref $arg (${p.title})"
            }
            "back" -> {
                val dialogRoot = roots().lastOrNull()
                if (act == null) return "no resumed activity"
                if (dialogRoot != null && dialogRoot !== act.window.decorView) {
                    dialogRoot.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK))
                    dialogRoot.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK))
                    "back (window ${dialogRoot.javaClass.simpleName})"
                } else {
                    (act as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.onBackPressed() ?: act.onBackPressed()
                    "back (activity ${act.javaClass.simpleName})"
                }
            }
            "start" -> {
                val cls = if (arg.startsWith(".")) context.packageName + arg else arg
                val i = Intent().setClassName(context.packageName, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                (act ?: context).startActivity(i); "started $cls"
            }
            "scroll" -> {
                val rv = roots().lastOrNull()?.let { firstRecycler(it) } ?: return "no RecyclerView"
                rv.scrollBy(0, arg.toIntOrNull() ?: 800); "scrolled ${describe(rv)}"
            }
            "shot" -> {
                val root = roots().lastOrNull() ?: return "no window"
                val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bmp))
                val f = File(context.getExternalFilesDir(null), "qa_shot.png")
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                "wrote ${f.absolutePath}"
            }
            else -> "unknown cmd $cmd"
        }
    }

    private fun prefFragment(fm: FragmentManager): PreferenceFragmentCompat? {
        for (f in fm.fragments) {
            if (f is PreferenceFragmentCompat && f.isResumed) return f
            prefFragment(f.childFragmentManager)?.let { return it }
        }
        return null
    }

    private fun firstRecycler(v: View): RecyclerView? {
        if (v is RecyclerView && v.isShown) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) firstRecycler(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun find(sel: String): View? {
        val (kind, value) = sel.split(":", limit = 2).let { if (it.size == 2) it[0] to it[1] else "text" to sel }
        fun match(v: View): Boolean = when (kind) {
            "text" -> (v as? TextView)?.text?.toString() == value
            "desc" -> v.contentDescription?.toString() == value
            "id" -> v.id != View.NO_ID && runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull() == value
            else -> false
        }
        fun walk(v: View): View? {
            if (!v.isShown) return null
            if (match(v)) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))?.let { return it }
            return null
        }
        // topmost window first (dialogs, menus)
        for (r in roots().asReversed()) walk(r)?.let { return it }
        return null
    }

    private fun describe(v: View): String {
        val id = if (v.id != View.NO_ID) runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull() else null
        val loc = IntArray(2).also { v.getLocationOnScreen(it) }
        val txt = (v as? TextView)?.text?.toString()?.take(60)
        return buildString {
            append(v.javaClass.simpleName)
            id?.let { append(" #").append(it) }
            txt?.takeIf { it.isNotEmpty() }?.let { append(" \"").append(it).append('"') }
            v.contentDescription?.let { append(" desc=\"").append(it).append('"') }
            if (v.isClickable) append(" [click]")
            if (!v.isEnabled) append(" [disabled]")
            append(" @").append(loc[0]).append(',').append(loc[1]).append(' ').append(v.width).append('x').append(v.height)
        }
    }

    private fun dump(v: View, depth: Int, sb: StringBuilder) {
        if (!v.isShown) return
        sb.append("  ".repeat(depth)).append(describe(v)).append('\n')
        if (v is ViewGroup) for (i in 0 until v.childCount) dump(v.getChildAt(i), depth + 1, sb)
    }
}
