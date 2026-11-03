package com.jcapps.adbserver

import com.koushikdutta.async.http.body.AsyncHttpRequestBody
import com.koushikdutta.async.http.body.UrlEncodedFormBody
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse

/**
 * GET /ntp, POST /ntp (form field `server`), POST /ntp/delete — reads / writes the global setting
 * `ntp_server` through `settings`.
 *
 * In the dex this is one branch of the merged handler class C0171 (its constructor for EnumC0288).
 */
// obf: C0171 (EnumC0288 branch of mo143)
class NtpHandler(param: Param, private val action: Action) : BaseHandler(param) { // obf: f355

    // obf: EnumC0288 (GET = f614, SET = f613, DEL = f616)
    enum class Action {
        GET,    // GET  /ntp
        SET,    // POST /ntp
        DELETE, // POST /ntp/delete (original constant name: DEL)
    }

    override fun handle(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        when (action) {
            Action.GET -> {
                val result = Shell.run("settings get global ntp_server", true)
                if (result.exitCode == 0) {
                    response.sendSuccess(result.output)
                } else {
                    response.sendError(result.error)
                }
            }
            Action.SET -> setNtpServer(request, response)
            Action.DELETE -> {
                val result = Shell.run("settings delete global ntp_server", true)
                if (result.exitCode == 0) {
                    response.sendSuccess(result.output)
                } else {
                    response.sendError(result.error)
                }
            }
        }
    }

    private fun setNtpServer(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        val body = request.getBody<AsyncHttpRequestBody<*>>()
        if (body !is UrlEncodedFormBody) {
            response.sendError("Body must be Form URL encoded.")
            return
        }
        val server = body.get()?.getString("server")
        if (server == null) {
            response.sendError("Missing server param.")
            return
        }
        if (!MiscUtils.isValidNtpServer(server)) {
            response.sendError("NTP server name or address is invalid.")
            return
        }
        val result = Shell.run("settings put global ntp_server " + server, true)
        if (result.exitCode == 0) {
            response.sendSuccess("Set NTP server successfully.")
        } else {
            response.sendError(result.error)
        }
    }
}
