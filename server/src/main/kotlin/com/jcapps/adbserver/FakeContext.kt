package com.jcapps.adbserver

import android.content.AttributionSource
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.IContentProvider
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import java.lang.reflect.Method

/** obf: C0244 constructor body — `ActivityThread.getSystemContext()` on the fake ActivityThread. */
private fun loadSystemContext(): Context? {
    var context: Context? = null
    try {
        context = ActivityThreadHolder.activityThreadClass.getDeclaredMethod("getSystemContext")
            .invoke(ActivityThreadHolder.activityThread) as Context?
    } catch (th: Throwable) {
        println("Could not get system context: " + th.message)
    }
    return context
}

/**
 * The system context, presented as package `com.android.shell` / uid 2000 (the identity the
 * server really has when started over adb). This is the `context` handed to the handlers.
 */
// obf: C0244 (singleton f508)
object FakeContext : ContextWrapper(loadSystemContext()) {

    private val resolver: ContentResolver = FakeContentResolver(this) // obf: contentResolver

    override fun getAttributionSource(): AttributionSource {
        // obf: AbstractC0220.m233 = AttributionSource.Builder(2000)
        val builder = AttributionSource.Builder(2000)
        builder.setPackageName("com.android.shell")
        return builder.build()
    }

    override fun getContentResolver(): ContentResolver = resolver

    override fun getDeviceId(): Int = 0

    override fun getOpPackageName(): String = "com.android.shell"

    override fun getPackageName(): String = "com.android.shell"

    override fun getApplicationContext(): Context = this
}

/**
 * ContentResolver that resolves providers through `IActivityManager.getContentProviderExternal`
 * (no app process / ApplicationThread is available under app_process).
 *
 * The five methods below implement the *hidden* abstract methods of [ContentResolver]; they are
 * not visible in the SDK stubs, hence no `override` keyword. The JVM signatures must stay exactly
 * like this (see the stub [android.content.IContentProvider]).
 */
// obf: C0085
class FakeContentResolver(context: Context) : ContentResolver(context) {

    /** Name + token of each acquired provider, needed to release it again. */
    private val acquired = java.util.IdentityHashMap<IContentProvider, Pair<String, Binder>>()

    fun acquireProvider(context: Context, name: String): IContentProvider? {
        val token = Binder()
        val provider = ActivityManagerProxy.get().getContentProviderExternal(name, token) ?: return null
        synchronized(acquired) { acquired[provider] = name to token }
        return provider
    }

    fun acquireUnstableProvider(context: Context, name: String): IContentProvider? = null

    /** Fixed (rebuild): the original never released providers (no-op returning false). */
    fun releaseProvider(provider: IContentProvider): Boolean {
        val entry = synchronized(acquired) { acquired.remove(provider) } ?: return false
        ActivityManagerProxy.get().removeContentProviderExternal(entry.first, entry.second)
        return true
    }

    fun releaseUnstableProvider(provider: IContentProvider): Boolean = false

    fun unstableProviderDied(provider: IContentProvider) {
    }
}
