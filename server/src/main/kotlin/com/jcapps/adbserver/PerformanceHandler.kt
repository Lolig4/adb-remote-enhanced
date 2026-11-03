package com.jcapps.adbserver

import android.app.ActivityManager
import android.content.Context
import com.google.gson.annotations.SerializedName
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import java.io.File

/**
 * GET /performance — per-CPU load (two `/proc/stat` samples 300 ms apart) and RAM usage.
 */
// obf: C0240 (f498 = context)
class PerformanceHandler(param: Param, private val context: Context) : BaseHandler(param) {

    // obf: mo143
    override fun handle(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        var cpuList: List<Performance.CPU> = emptyList() // obf: C0166.f339 (EmptyList)
        val firstLines = readProcStat()
        if (firstLines != null) {
            val first = firstLines.filter { it.startsWith("cpu") }.map { parseCpuLine(it) }
            try {
                Thread.sleep(300L)
            } catch (e: InterruptedException) {
            }
            val secondLines = readProcStat()
            if (secondLines != null) {
                val second = secondLines.filter { it.startsWith("cpu") }.map { parseCpuLine(it) }
                if (first.size == second.size) {
                    cpuList = first.mapIndexed { index, before ->
                        val after = second[index]
                        val restBefore = before?.rest ?: 0L
                        val restAfter = after?.rest ?: 0L
                        // BUG(original): Long division by zero (ArithmeticException, request dies)
                        // when a line's counters did not change between the two samples, and every
                        // unparsable line (null) counts as 0/0.
                        val percent = ((restAfter - restBefore) * 100L) /
                            ((restAfter + (after?.idle ?: 0L)) - (restBefore + (before?.idle ?: 0L)))
                        Performance.CPU(
                            name = if (index == 0) "CPU" else "CPU $index",
                            percent = percent.toInt(),
                        )
                    }
                }
            }
        }
        val activityManager = context.getSystemService("activity") as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val total = memoryInfo.totalMem
        val available = memoryInfo.availMem
        response.sendSuccess(
            Performance(cpuList, Performance.Memory(available = available, remaining = total - available, total = total)),
        )
    }

    // obf: C0011 ("Performance")
    data class Performance(
        @field:SerializedName("cpu") val cpuList: List<CPU>,
        @field:SerializedName("memory") val memory: Memory,
    ) {
        // obf: C0011.C0013 ("CPU")
        data class CPU(
            @field:SerializedName("name") val name: String,
            @field:SerializedName("percent") val percent: Int,
        )

        // obf: C0011.C0012 ("Memory")
        data class Memory(
            @field:SerializedName("available") val available: Long,
            @field:SerializedName("remaining") val remaining: Long,
            @field:SerializedName("total") val total: Long,
        )
    }

    /** One `/proc/stat` cpu line: [idle] = idle jiffies, [rest] = user+nice+system+iowait+irq+softirq. */
    // obf: C0280 ("CPUInfo"; f603 = rest, f602 = idle)
    data class CPUInfo(val rest: Long, val idle: Long)

    companion object {
        // obf: AbstractC0019.m44
        fun readProcStat(): List<String>? {
            return try {
                val file = File("/proc/stat")
                if (file.exists() && file.isFile) {
                    file.inputStream().bufferedReader(Charsets.UTF_8).use { it.readLines() }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        /** `cpu  u n s i w q sq ...` -> [CPUInfo]; null if fewer than 8 columns. */
        // obf: AbstractC0019.m45
        fun parseCpuLine(line: String): CPUInfo? {
            val columns = Regex(" {2}").replace(line, " ").split(" ")
            if (columns.size < 8) {
                return null
            }
            val user = columns[1].trim().toLongOrNull() ?: 0L
            val nice = columns[2].trim().toLongOrNull() ?: 0L
            val system = columns[3].trim().toLongOrNull() ?: 0L
            val idle = columns[4].trim().toLongOrNull() ?: 0L
            val iowait = columns[5].trim().toLongOrNull() ?: 0L
            val irq = columns[6].trim().toLongOrNull() ?: 0L
            val softirq = columns[7].trim().toLongOrNull() ?: 0L
            return CPUInfo(rest = user + nice + system + iowait + irq + softirq, idle = idle)
        }
    }
}
