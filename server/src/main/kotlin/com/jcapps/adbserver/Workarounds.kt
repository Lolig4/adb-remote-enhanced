package com.jcapps.adbserver

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Looper

/**
 * Creates the process-wide `ActivityThread` that a plain `app_process` JVM lacks and registers it
 * as `ActivityThread.sCurrentActivityThread` (the scrcpy "Workarounds" approach).
 */
// obf: AbstractC0143 (static initializer)
internal object ActivityThreadHolder {
    @JvmField val activityThreadClass: Class<*> // obf: f305
    @JvmField val activityThread: Any           // obf: f304

    init {
        Looper.prepareMainLooper()
        val (cls, instance) = try {
            val cls = Class.forName("android.app.ActivityThread")
            val constructor = cls.getDeclaredConstructor()
            constructor.isAccessible = true
            val instance = constructor.newInstance()
            val field = cls.getDeclaredField("sCurrentActivityThread")
            field.isAccessible = true
            field.set(null, instance)
            cls to instance
        } catch (e: Exception) {
            throw AssertionError(e)
        }
        activityThreadClass = cls
        activityThread = instance
    }
}

/**
 * Patches the fake `ActivityThread` so that framework code that expects a real app process works,
 * and returns the shell [FakeContext]. Each step is independent and only logs on failure.
 * `ADBServer.main` calls this with `System.err` muted.
 */
// obf: C0191 (synthetic lambda class), mode 0 = mo100() case 0
object Workarounds {

    // obf: C0191.mo100 case 0
    fun apply(): Context? {
        val sdk = Build.VERSION.SDK_INT
        val activityThreadClass = ActivityThreadHolder.activityThreadClass
        val activityThread = ActivityThreadHolder.activityThread

        // fill configuration controller (Android 12+)
        if (sdk >= 31) {
            try {
                val constructor = Class.forName("android.app.ConfigurationController")
                    .getDeclaredConstructor(Class.forName("android.app.ActivityThreadInternal"))
                constructor.isAccessible = true
                val configurationController = constructor.newInstance(activityThread)
                val field = activityThreadClass.getDeclaredField("mConfigurationController")
                field.isAccessible = true
                field.set(activityThread, configurationController)
            } catch (th: Throwable) {
                println("Could not fill configuration: " + th.message)
            }
        }

        // fill app info (skipped on ONYX devices)
        if (!Build.BRAND.equals("ONYX", ignoreCase = true)) {
            try {
                val appBindDataClass = Class.forName("android.app.ActivityThread\$AppBindData")
                val constructor = appBindDataClass.getDeclaredConstructor()
                constructor.isAccessible = true
                val appBindData = constructor.newInstance()
                val applicationInfo = ApplicationInfo()
                applicationInfo.packageName = "com.android.shell"
                val appInfoField = appBindDataClass.getDeclaredField("appInfo")
                appInfoField.isAccessible = true
                appInfoField.set(appBindData, applicationInfo)
                val boundApplicationField = activityThreadClass.getDeclaredField("mBoundApplication")
                boundApplicationField.isAccessible = true
                boundApplicationField.set(activityThread, appBindData)
            } catch (th: Throwable) {
                println("Could not fill app info: " + th.message)
            }
        }

        // fill app context
        try {
            val application = android.app.Application()
            val baseField = android.content.ContextWrapper::class.java.getDeclaredField("mBase")
            baseField.isAccessible = true
            baseField.set(application, FakeContext)
            val initialApplicationField = activityThreadClass.getDeclaredField("mInitialApplication")
            initialApplicationField.isAccessible = true
            initialApplicationField.set(activityThread, application)
        } catch (th: Throwable) {
            println("Could not fill app context: " + th.message)
        }

        return FakeContext
    }
}
