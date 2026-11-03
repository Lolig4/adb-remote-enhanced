package com.jcapps.adbserver

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import com.google.gson.Gson
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.koushikdutta.async.http.body.AsyncHttpRequestBody
import com.koushikdutta.async.http.body.JSONObjectBody
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import java.io.File
import java.lang.reflect.Method

/**
 * POST /intent (JSON body [IntentParams]): starts an activity either via `am start` (run_type
 * "shell", the default) or via `IActivityManager.startActivityAsUser` (run_type "normal", falling
 * back to `am start` on a negative result).
 */
// obf: C0118
class IntentHandler(param: Param) : BaseHandler(param) {

    override fun handle(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        val body = request.getBody<AsyncHttpRequestBody<*>>()
        if (body !is JSONObjectBody) {
            response.sendError("Body must be JSON.")
            return
        }
        val json = body.get()
        var uri: Uri? = null
        // A fresh Gson (obf: new C0344()), not param.gson.
        val params: IntentParams? = try {
            Gson().fromJson(json?.toString(), IntentParams::class.java)
        } catch (e: JsonSyntaxException) { // obf: C0036 — RECON-UNSURE: JsonSyntaxException vs JsonParseException
            null
        }
        if (params == null) {
            response.sendError("Intent request needs params.")
            return
        }
        if (params.dataUri != null) {
            try {
                uri = Uri.parse(params.dataUri)
            } catch (e: Exception) {
            }
        } else if (params.filePath != null) {
            val filePath = checkNotNull(params.filePath)
            // obf: inlined extension on FilePath (receiver "<this>")
            val parent = MiscUtils.resolveDirectory(filePath.parent) { File(filePath.fallbackParent) }
            uri = Uri.fromFile(File(parent, filePath.child))
        }
        // obf: AbstractC0307.f675 (when-mapping: SHELL -> 1, NORMAL -> 2)
        when (params.runType ?: IntentParams.RunType.SHELL) {
            IntentParams.RunType.SHELL -> runWithShell(params, uri, response)
            IntentParams.RunType.NORMAL -> {
                val intent = Intent()
                intent.setDataAndType(uri, params.mimeType)
                params.action?.let { intent.action = it }
                params.category?.let { intent.addCategory(it) }
                params.componentName?.let { name ->
                    ComponentName.unflattenFromString(name)?.let { intent.component = it }
                }
                params.packageName?.let { intent.setPackage(it) }
                params.flags?.let { intent.flags = it }
                params.extras?.let { extras ->
                    for ((key, value) in extras) {
                        // Fixed (rebuild): Gson yields LazilyParsedNumber, which matched none of the
                        // original's branches, so numeric extras were dropped. Classify by the JSON text.
                        if (value.isString) {
                            intent.putExtra(key, value.asString)
                        } else if (value.isBoolean) {
                            intent.putExtra(key, value.asBoolean)
                        } else if (value.isNumber) {
                            when (val number = typedNumber(value)) {
                                is Int -> intent.putExtra(key, number)
                                is Long -> intent.putExtra(key, number)
                                is Double -> intent.putExtra(key, number)
                            }
                        }
                    }
                }
                try {
                    val resultCode = ActivityManagerProxy.get().startActivity(intent)
                    if (resultCode >= 0) {
                        response.sendSuccess("Launched intent successfully. Result code : $resultCode")
                    } else {
                        println("Failed to launch intent. Result code : $resultCode")
                        runWithShell(params, uri, response)
                    }
                } catch (e: Exception) {
                    response.sendError(e.toString())
                }
            }
        }
    }

    /** Builds and runs `am start ...` with single-quoted arguments. */
    // obf: m150
    private fun runWithShell(params: IntentParams, uri: Uri?, response: AsyncHttpServerResponse) {
        val sb = StringBuilder()
        sb.append("am start ")
        params.action?.let { sb.append("-a '" + escape(it) + "' ") }
        if (uri != null) {
            sb.append("-d '" + escape(uri.toString()) + "' ")
        }
        params.mimeType?.let { sb.append("-t '" + escape(it) + "' ") }
        params.category?.let { sb.append("-c '" + escape(it) + "' ") }
        params.componentName?.let { sb.append("-n '" + escape(it) + "' ") }
        params.packageName?.let { sb.append("-p '" + escape(it) + "' ") }
        params.extras?.let { extras ->
            for ((key, value) in extras) {
                if (value.isString) {
                    sb.append("--es '" + escape(key) + "' '" + escape(value.asString) + "' ")
                } else if (value.isBoolean) {
                    sb.append("--ez '" + escape(key) + "' " + value.asBoolean + ' ')
                } else if (value.isNumber) {
                    // Fixed (rebuild): same LazilyParsedNumber problem as above.
                    when (val number = typedNumber(value)) {
                        is Int -> sb.append("--ei '" + escape(key) + "' " + number + ' ')
                        is Long -> sb.append("--el '" + escape(key) + "' " + number + ' ')
                        is Double -> sb.append("--ef '" + escape(key) + "' " + number.toFloat() + ' ')
                    }
                }
            }
        }
        params.flags?.let { sb.append("-f " + it + ' ') }
        // Fixed (rebuild): the original accepted every exit code (`>= 0`) and never read the output,
        // so the app always got success with "".
        val result = Shell.run(sb.toString().trim(), true)
        if (result.succeeded()) {
            response.sendSuccess(result.output)
        } else {
            // am prints "Starting: Intent {...}" on stdout and the reason on stderr -> prefer stderr
            response.sendError(result.error.ifEmpty { result.output }.ifEmpty { "Failed to start intent." })
        }
    }


    private companion object {
        /** Escapes `'` for use inside a single-quoted shell argument. */
        // obf: m149
        fun escape(value: String): String = value.replace("'", "'\\''")
    }
}

/** JSON body of POST /intent (names as sent by the app). */
// obf: C0119 (toString "IntentParams(runType=...")
data class IntentParams(
    @field:SerializedName("run_type") val runType: RunType?,           // obf: f237 / m153
    @field:SerializedName("action") val action: String?,               // obf: f235 / m154
    @field:SerializedName("data_uri") val dataUri: String?,            // obf: f241 / m156
    @field:SerializedName("file_path") val filePath: FilePath?,        // obf: f239 / m159
    @field:SerializedName("mime_type") val mimeType: String?,          // obf: f243 / m157
    @field:SerializedName("category") val category: String?,           // obf: f242 / m152
    @field:SerializedName("component_name") val componentName: String?, // obf: f238 / m158
    @field:SerializedName("package_name") val packageName: String?,    // obf: f240 / m151
    @field:SerializedName("flags") val flags: Int?,                    // obf: f234 / m155
    @field:SerializedName("extras") val extras: Map<String, JsonPrimitive>?, // obf: f236 / m160 (C0197 = JsonPrimitive)
) {
    // obf: C0119.EnumC0120
    enum class RunType {
        @SerializedName("shell") SHELL,   // obf: f245
        @SerializedName("normal") NORMAL, // obf: f244
    }

    /** File URI built as `File(parent ?: external storage ?: fallback_parent, child)`. */
    // obf: C0119.C0121 (toString "FilePath(parent=...")
    data class FilePath(
        @field:SerializedName("parent") val parent: String?,                 // obf: f248 / m163
        @field:SerializedName("fallback_parent") val fallbackParent: String, // obf: f247 / m161
        @field:SerializedName("child") val child: String,                    // obf: f249 / m162
    )
}

/** Int / Long / Double for a JSON number (Gson's LazilyParsedNumber), by its literal text. */
private fun typedNumber(value: com.google.gson.JsonPrimitive): Any? {
    val text = value.asString
    return if (text.contains('.') || text.contains('e', ignoreCase = true)) {
        text.toDoubleOrNull()
    } else {
        text.toIntOrNull() ?: text.toLongOrNull()
    }
}
