package com.jcapps.adbserver

import android.content.IContentProvider
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import java.lang.reflect.Method

/**
 * Reflection wrapper around the hidden `IActivityManager`.
 *
 * The original had one class (C0296) that the decompiled groups copied three times (clipboard,
 * FakeContext, IntentHandler); merged here (rebuild cleanup).
 */
// obf: C0296 (instance via AbstractC0063.m91 / f143)
internal class ActivityManagerProxy private constructor(private val manager: IInterface) { // obf: f636

    private var getContentProviderExternalMethod: Method? = null    // obf: f635
    private var getContentProviderExternalHasTag = true             // obf: f638
    private var removeContentProviderExternalMethod: Method? = null // obf: f637
    private var startActivityAsUserMethod: Method? = null           // obf: f639

    // obf: m303
    private fun getGetContentProviderExternalMethod(): Method {
        getContentProviderExternalMethod?.let { return it }
        val method = try {
            manager.javaClass.getMethod(
                "getContentProviderExternal",
                String::class.java, Integer.TYPE, IBinder::class.java, String::class.java,
            )
        } catch (e: NoSuchMethodException) {
            getContentProviderExternalHasTag = false
            manager.javaClass.getMethod(
                "getContentProviderExternal",
                String::class.java, Integer.TYPE, IBinder::class.java,
            )
        }
        getContentProviderExternalMethod = method
        return method
    }

    /**
     * Acquires the provider [name] for user 0 under [token]. Every successful call must be paired
     * with [removeContentProviderExternal] (same name and token), else the provider stays referenced.
     */
    // obf: m304
    fun getContentProviderExternal(name: String, token: IBinder): IContentProvider? {
        return try {
            val method = getGetContentProviderExternalMethod()
            val holder = if (getContentProviderExternalHasTag) {
                method.invoke(manager, name, 0, token, null)
            } else {
                method.invoke(manager, name, 0, token)
            } ?: return null
            val providerField = holder.javaClass.getDeclaredField("provider")
            providerField.isAccessible = true
            providerField.get(holder) as IContentProvider?
        } catch (e: ReflectiveOperationException) {
            println("Could not invoke method. $e")
            null
        }
    }

    /** Releases a provider acquired with [getContentProviderExternal]. */
    fun removeContentProviderExternal(name: String, token: IBinder) {
        try {
            val method = removeContentProviderExternalMethod
                ?: manager.javaClass.getMethod("removeContentProviderExternal", String::class.java, IBinder::class.java)
                    .also { removeContentProviderExternalMethod = it }
            method.invoke(manager, name, token)
        } catch (e: ReflectiveOperationException) {
            println("Could not invoke method. $e")
        }
    }

    // obf: m306
    private fun getStartActivityAsUserMethod(): Method {
        startActivityAsUserMethod?.let { return it }
        val applicationThreadClass = Class.forName("android.app.IApplicationThread")
        val profilerInfoClass = Class.forName("android.app.ProfilerInfo")
        val int = Integer.TYPE
        return manager.javaClass.getMethod(
            "startActivityAsUser",
            applicationThreadClass, String::class.java, Intent::class.java, String::class.java,
            IBinder::class.java, String::class.java, int, int, profilerInfoClass, Bundle::class.java, int,
        ).also { startActivityAsUserMethod = it }
    }

    /**
     * `startActivityAsUser(null, "com.android.shell", intent, null, null, null, 0, 0, null, null,
     * USER_CURRENT = -2)`; -1 if the call is not possible on this Android version.
     *
     * Fixed (rebuild): the original reported every failure (also a missing method on this Android
     * version) as 0 = START_SUCCESS, so /intent never fell back to `am start`.
     */
    // obf: m305
    fun startActivity(intent: Intent): Int {
        return try {
            getStartActivityAsUserMethod().invoke(
                manager, null, "com.android.shell", intent, null, null, null, 0, 0, null, null, -2,
            ) as Int
        } catch (th: Throwable) {
            println("Could not invoke method. $th")
            -1
        }
    }

    companion object {
        @Volatile
        private var instance: ActivityManagerProxy? = null // obf: AbstractC0063.f143

        // obf: AbstractC0063.m91
        fun get(): ActivityManagerProxy {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: try {
                    ActivityManagerProxy(
                        Class.forName("android.app.ActivityManagerNative")
                            .getDeclaredMethod("getDefault")
                            .invoke(null) as IInterface,
                    ).also { instance = it }
                } catch (e: ReflectiveOperationException) {
                    throw AssertionError(e)
                }
            }
        }
    }
}
