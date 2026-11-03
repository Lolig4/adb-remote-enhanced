package com.jcapps.adbserver

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.guichaguri.minimalftp.FTPConnection
import com.guichaguri.minimalftp.FTPServer
import com.guichaguri.minimalftp.api.IFileSystem
import com.guichaguri.minimalftp.api.IUserAuthenticator
import com.guichaguri.minimalftp.impl.NativeFileSystem
import com.koushikdutta.async.http.body.AsyncHttpRequestBody
import com.koushikdutta.async.http.body.JSONObjectBody
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket

/**
 * POST /ftp/start (JSON body [FTPParams]) and POST /ftp/stop — runs a MinimalFTP server on the TV.
 *
 * In the dex this is one branch of the merged handler class C0171 (its constructor for EnumC0177).
 * MinimalFTP itself is R8-shrunk and inlined into the dex (C0317 = FTPServer, C0229 = FTPConnection
 * with ConnectionHandler/FileHandler inlined, C0053 = ServerThread); here the real library is used and
 * only the app's own glue/modifications are ported.
 */
// obf: C0171 (EnumC0177 branch of mo143)
class FtpHandler(param: Param, private val action: Action) : BaseHandler(param) { // obf: f355

    // obf: EnumC0177 (START = f371, STOP = f370)
    enum class Action {
        START, // POST /ftp/start
        STOP,  // POST /ftp/stop
    }

    override fun handle(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        when (action) {
            Action.START -> start(request, response)
            Action.STOP -> {
                MiscUtils.ignoreExceptions { FtpServerManager.stop() } // obf: m354(C0191(2))
                response.sendSuccess("FTP server stopped.")
            }
        }
    }

    private fun start(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        val body = request.getBody<AsyncHttpRequestBody<*>>()
        if (body !is JSONObjectBody) {
            response.sendError("Body must be JSON.")
            return
        }
        val json = body.get()
        // A fresh Gson (obf: new C0344()), not param.gson.
        val params: FTPParams? = try {
            Gson().fromJson(json?.toString(), FTPParams::class.java)
        } catch (e: JsonSyntaxException) { // obf: C0036 — RECON-UNSURE: JsonSyntaxException vs JsonParseException
            null
        }
        if (params == null) {
            response.sendError("FTP server needs FTP params.")
            return
        }
        // BUG(original): a body without "ip_address" leaves the non-null Kotlin field null (Gson
        // bypasses the constructor); the parameter null-check then throws an uncaught NPE here.
        if (!MiscUtils.isValidIpAddress(params.ipAddress)) {
            response.sendError("Invalid IP address.")
            return
        }
        if (params.port !in 0..65535) { // obf: C0255 = IntRange(0, 65535)
            response.sendError("Invalid port.")
            return
        }
        try {
            val fallbackDir = File(params.fallbackDir ?: "/sdcard")
            val dir = MiscUtils.resolveDirectory(params.defaultDir) { fallbackDir }
            val rootDir = if (dir.canRead()) dir else fallbackDir
            val username = params.username?.takeUnless { it.isBlank() }
            FtpServerManager.start(
                params.ipAddress, params.port, rootDir, params.readOnly, params.passiveModeEnabled,
                username, params.password,
            )
            response.sendSuccess("FTP server started.")
        } catch (e: Exception) {
            response.sendError(e.toString())
        }
    }
}

/** JSON body of POST /ftp/start (names as sent by the app). */
// obf: C0066 (toString "FTPParams(ipAddress=...")
data class FTPParams(
    @field:SerializedName("ip_address") val ipAddress: String,          // obf: f149 / m97
    @field:SerializedName("port") val port: Int,                         // obf: f148 / m98
    @field:SerializedName("default_dir") val defaultDir: String?,        // obf: f153 / m93
    @field:SerializedName("fallback_dir") val fallbackDir: String?,      // obf: f151 / m92
    @field:SerializedName("read_only") val readOnly: Boolean,            // obf: f155 / m96
    @field:SerializedName("passive_mode") val passiveModeEnabled: Boolean, // obf: f154 / m95
    @field:SerializedName("username") val username: String?,             // obf: f150 / m94
    @field:SerializedName("password") val password: String?,             // obf: f152 / m99
)

/** Holds the single running FTP server. */
internal object FtpServerManager {

    // obf: AbstractC0353.f785
    private var server: FtpServer? = null

    // obf: AbstractC0353.m362 (FTPServer.listen(InetAddress, int) inlined)
    @Throws(IOException::class)
    fun start(
        ipAddress: String,
        port: Int,
        dir: File,
        readOnly: Boolean,
        passiveModeEnabled: Boolean,
        username: String?,
        password: String?,
    ) {
        MiscUtils.ignoreExceptions { stop() } // obf: m354(C0191(2))
        val fileSystem = FtpFileSystem(dir, readOnly)
        val authenticator = FtpAuthenticator(fileSystem, username, password)
        val ftpServer = FtpServer(authenticator)
        server = ftpServer
        ftpServer.passiveModeEnabled = passiveModeEnabled
        ftpServer.listen(InetAddress.getByName(ipAddress), port)
    }

    // obf: C0191 mode 2 (mo100)
    fun stop() {
        server?.close()
        server = null // Fixed (rebuild): the original kept the closed instance
    }
}

/**
 * MinimalFTP server with the app's extra "passive mode" switch (obf: C0317.f703). The app ships a
 * modified MinimalFTP that registers PASV, LPSV, EPSV and the "EPSV" feature only when it is on.
 * Stock MinimalFTP 1.0.6 has no such switch, so [FtpConnection] removes them after construction.
 */
// obf: C0317 (FTPServer; idleTimeout 300000, bufferSize 1024, explicitSecurity true = library defaults)
internal class FtpServer(authenticator: IUserAuthenticator) : FTPServer(authenticator) {

    var passiveModeEnabled = true // obf: f703

    override fun createConnection(socket: Socket): FTPConnection =
        FtpConnection(this, socket, idleTimeout, bufferSize, passiveModeEnabled)
}

// obf: C0229 (FTPConnection; ConnectionHandler commands inlined)
internal class FtpConnection(
    server: FTPServer,
    socket: Socket,
    idleTimeout: Int,
    bufferSize: Int,
    passiveModeEnabled: Boolean,
) : FTPConnection(server, socket, idleTimeout, bufferSize) {
    init {
        if (!passiveModeEnabled) {
            // RECON-UNSURE: emulation of the modified library. The original never registers these;
            // here they are removed right after the super constructor, which has already started the
            // connection thread and sent the greeting (theoretical race with a very fast client).
            commands.remove("PASV")
            commands.remove("LPSV")
            commands.remove("EPSV")
            features.remove("EPSV")
        }
    }
}

/**
 * Single-user authenticator: the configured username/password, or anonymous login without
 * username (then "Ready!" right after connecting).
 */
// obf: C0086 (fields f179 = fileSystem, f178 = username, f180 = password; methods inlined into C0229)
internal class FtpAuthenticator(
    private val fileSystem: FtpFileSystem,
    private val username: String?,
    private val password: String?,
) : IUserAuthenticator {

    override fun needsUsername(con: FTPConnection): Boolean = username != null

    // RECON-UNSURE: the inlined check tests the USER argument (`str != null`), so a password is
    // always requested after USER; reconstructed as such.
    override fun needsPassword(con: FTPConnection, username: String?, address: InetAddress?): Boolean =
        username != null

    // obf: m116
    override fun authenticate(
        con: FTPConnection,
        address: InetAddress?,
        username: String?,
        password: String?,
    ): IFileSystem<File> {
        if (this.username == username && this.password == password) {
            return fileSystem
        }
        throw IUserAuthenticator.AuthException() // obf: C0281
    }
}

/**
 * NativeFileSystem plus a read-only mode and a recursive delete.
 * (The write checks for mkdirs/rename/chmod/touch were inlined into C0229 in the dex.)
 */
// obf: C0047 (f114 = root, f113 = readOnly, f115 = message)
internal class FtpFileSystem(root: File, private val readOnly: Boolean) : NativeFileSystem(root) {

    private val readOnlyMessage = "Permission denied. FTP server is configured for read-only access."

    // obf: m79
    private fun throwReadOnly(): Nothing = throw IOException(readOnlyMessage)

    // obf: m81
    override fun writeFile(file: File, start: Long): OutputStream {
        if (readOnly) throwReadOnly()
        return super.writeFile(file, start)
    }

    override fun mkdirs(file: File) {
        if (readOnly) throwReadOnly()
        super.mkdirs(file)
    }

    // obf: m78 — recursive, unlike the library
    override fun delete(file: File) {
        if (readOnly) throwReadOnly()
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    delete(child)
                }
            }
        }
        if (file.delete()) {
            return
        }
        throw IOException("Couldn't delete file: " + file.absolutePath)
    }

    override fun rename(from: File, to: File) {
        if (readOnly) throwReadOnly()
        super.rename(from, to)
    }

    override fun chmod(file: File, perms: Int) {
        if (readOnly) throwReadOnly()
        super.chmod(file, perms)
    }

    override fun touch(file: File, time: Long) {
        if (readOnly) throwReadOnly()
        super.touch(file, time)
    }
}
