package com.jcapps.adbremote.qa

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import java.io.File

/** QA only: tracks the resumed activity and records uncaught exceptions before the process dies. */
class DebugInit : ContentProvider() {

    override fun onCreate(): Boolean {
        val app = context!!.applicationContext as Application
        crashFile = File(app.getExternalFilesDir(null), "qa_crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { crashFile?.writeText("thread=${thread.name}\n" + android.util.Log.getStackTraceString(e)) }
            previous?.uncaughtException(thread, e)
        }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { resumed = activity }
            override fun onActivityPaused(activity: Activity) { if (resumed === activity) resumed = null }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) { last = activity }
            override fun onActivityStarted(activity: Activity) { last = activity }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) { if (last === activity) last = null }
        })
        return true
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(uri: Uri) = null
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        @Volatile var resumed: Activity? = null
        @Volatile var last: Activity? = null
        @Volatile var crashFile: File? = null
    }
}
