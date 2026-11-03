package com.jcapps.adbremote.utils

import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi

/** Expose a coroutine [Deferred] as a blocking [Future] (MaterialFiles Deferred.asFuture). */
fun <T> Deferred<T>.asFuture(): Future<T> = DeferredFuture(this)

private class DeferredFuture<T>(
    // obf: f5218
    private val deferred: Deferred<T>,
) : Future<T> {
    // obf: FutureC0935

    // obf: f5217
    private val latch = CountDownLatch(1)

    init {
        @OptIn(InternalCoroutinesApi::class)
        deferred.invokeOnCompletion(onCancelling = true, invokeImmediately = true) {
            latch.countDown()
        }
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        deferred.cancel()
        return deferred.isCancelled
    }

    override fun isCancelled(): Boolean = deferred.isCancelled

    override fun isDone(): Boolean = deferred.isCompleted

    override fun get(): T {
        latch.await()
        return getCompleted()
    }

    override fun get(timeout: Long, unit: TimeUnit): T {
        latch.await(timeout, unit)
        return getCompleted()
    }

    // obf: m2852
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun getCompleted(): T =
        try {
            deferred.getCompleted()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ExecutionException(e)
        }
}
