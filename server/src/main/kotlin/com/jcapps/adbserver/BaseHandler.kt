package com.jcapps.adbserver

import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import com.koushikdutta.async.http.server.HttpServerRequestCallback

/**
 * Base of every endpoint. Logs the request to stdout, checks the `token` header against
 * [Param.token] and only then dispatches to [handle].
 *
 * In the dex, R8 devirtualized AndroidAsync's `HttpServerRequestCallback.onRequest` into [onRequest]
 * (obf m144) and the router stores `AbstractC0106` directly.
 */
// obf: AbstractC0106 (f224 = param)
abstract class BaseHandler(val param: Param) : HttpServerRequestCallback {

    // obf: m144
    final override fun onRequest(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        // Query lambda: obf C0191(1) (per entry) + C0025(key) (per value)
        val query = request.query.entries.joinToString("&") { entry ->
            val key = entry.key
            val values = checkNotNull(entry.value)
            values.joinToString("&") { "$key=$it" }
        }
        var path = request.path
        if (query.isNotEmpty()) {
            path = "$path?$query"
        }
        println("\n" + request.method + ": " + path + '\n')
        if (request.headers.get("token") == param.token) {
            handle(request, response)
        } else {
            response.sendError("Invalid token.")
        }
    }

    // obf: mo143
    abstract fun handle(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse)

    // obf: m145
    protected fun AsyncHttpServerResponse.sendError(message: String) {
        sendResponse(this, BaseResponse(null, false, message))
    }

    // obf: m146
    protected fun AsyncHttpServerResponse.sendSuccess(data: Any) {
        sendResponse(this, BaseResponse(data, true, null))
    }

    /** "Missing a query. Missing b query." */
    // obf: m147
    protected fun AsyncHttpServerResponse.sendMissingQueries(vararg queries: String) {
        sendError(queries.joinToString(" ") { "Missing $it query." })
    }

    /**
     * R8 inlined `Gson.toJson(Object, Type)` (StringWriter + `newJsonWriter`) and
     * `AsyncHttpServerResponse.send(String)` (Content-Type header, else "text/html; charset=utf-8",
     * then UTF-8 bytes) here; this is the source-level equivalent.
     */
    // obf: m148
    private fun sendResponse(response: AsyncHttpServerResponse, body: BaseResponse<*>) {
        response.headers.set("Content-Type", "application/json; charset=utf-8")
        response.send(param.gson.toJson(body, BaseResponse::class.java))
    }
}
