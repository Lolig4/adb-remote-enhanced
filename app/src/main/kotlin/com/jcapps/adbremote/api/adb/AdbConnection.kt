package com.jcapps.adbremote.api.adb

import com.jcapps.adbremote.api.adb.stream.Message
import com.jcapps.adbremote.api.adb.stream.MessageHeader
import com.jcapps.adbremote.api.adb.stream.bytesToInt
import com.jcapps.adbremote.api.adb.stream.createMessage
import com.jcapps.adbremote.model.AuthResult
import com.jcapps.adbremote.model.ConnectionResult
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher

/**
 * Low-level ADB protocol connection over a raw [Socket] (vendored, cgutman-style).
 *
 * Reads/writes [Message]s and performs the RSA AUTH handshake with the device's key pair.
 */
// obf: C0702
class AdbConnection(
    // obf: f4586
    val keyPair: KeyPair,
) : AutoCloseable {

    // obf: f4587
    var inputStream: InputStream? = null

    // obf: f4588
    var outputStream: OutputStream? = null

    // obf: f4589
    var socket: Socket? = null

    override fun close() {
        try {
            outputStream?.close()
            inputStream?.close()
            val s = socket
            if (s != null && !s.isClosed) {
                socket?.close()
            }
        } catch (t: Throwable) {
            // best-effort (original: Result.createFailure)
        }
    }

    /**
     * Runs the ADB AUTH handshake: signs the server token with the private key (RSA/ECB/PKCS1),
     * and if the device re-challenges, sends the public key (Android `adb` key-blob format plus the
     * `" user@adbremote.app\u0000"` comment). On success [onConnected] receives the CNXN message.
     *
     * Throws [AdbUnauthorizedException] when the user ignores the on-device USB-debugging prompt
     * (socket timeout).
     */
    // obf: m2491
    fun authenticate(authMessage: Message, keyPair: KeyPair, onConnected: (Message) -> Unit) {
        val authType = authMessage.messageHeader.arg0
        if (authType != 16777216) {
            throw IllegalStateException("Expected auth type 16777216, received $authType")
        }
        try {
            // SHA-1 DigestInfo prefix + token
            val digest = byteArrayOf(48, 33, 48, 9, 6, 5, 43, 14, 3, 2, 26, 5, 0, 4, 20) + authMessage.data
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keyPair.private)
            val signature = cipher.doFinal(digest)
            writeMessage(createMessage("AUTH", signature, 33554432, 0))
            val response = readMessage()
            if (response.commandString() != "AUTH") {
                if (response.commandString() != "CNXN") {
                    throw IllegalStateException("Expected CNXN command, received " + authMessage.commandString())
                }
                // NOTE: original returns here without invoking onConnected
                return
            }
            val reAuthType = response.messageHeader.arg0
            if (reAuthType != 16777216) {
                throw IllegalStateException("Expected auth type 16777216, received $reAuthType")
            }
            val publicKey = keyPair.public as RSAPublicKey
            val words = (publicKey.modulus.bitLength() / 8) / 4
            val modulus = publicKey.modulus
            val rr = BigInteger.ZERO.setBit(words * 32).modPow(BigInteger.valueOf(2L), modulus)
            val n0inv = modulus.remainder(BigInteger.ZERO.setBit(32))
                .modInverse(BigInteger.ZERO.setBit(32)).negate().toInt()
            val exponent = publicKey.publicExponent.toInt()
            val buffer = ByteBuffer.allocate((words * 8) + 12).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(words)
            buffer.putInt(n0inv)
            val size = words * 4
            buffer.put(modulus.toByteArray().reversedArray(), 0, size)
            buffer.put(rr.toByteArray().reversedArray(), 0, size)
            buffer.putInt(exponent)
            writeMessage(
                createMessage("AUTH", buffer.array() + " user@adbremote.app\u0000".toByteArray(Charsets.UTF_8), 50331648, 0),
            )
            socket?.soTimeout = 10000
            val connected = readMessage()
            socket?.soTimeout = 10000
            if (connected.commandString() != "CNXN") {
                throw IllegalStateException("Expected CNXN command, received " + response.commandString())
            }
            onConnected(connected)
        } catch (e: Exception) {
            if (e !is SocketTimeoutException) {
                throw IllegalStateException(e)
            }
            throw AdbUnauthorizedException("User ignored USB Debugging prompt.")
        }
    }

    /**
     * TCP connect + CNXN handshake (+ AUTH when requested). R8-inlined into AdbClient.processNext
     * (param-check names `ipAddress` on this class survived). RECON-UNSURE: name/signature.
     */
    // (inlined in C0180.m1876)
    fun connect(ipAddress: String, port: Int, connectionTimeout: Int, socketTimeout: Int): ConnectionResult {
        val address = InetSocketAddress(ipAddress, port)
        socket = Socket()
        socket!!.tcpNoDelay = true
        return try {
            socket?.connect(address, connectionTimeout)
            socket?.soTimeout = socketTimeout
            outputStream = socket?.getOutputStream()
            inputStream = socket?.getInputStream()
            val features = "host::features=shell_v2,cmd,stat_v2,ls_v2,fixed_push_mkdir,apex,abb," +
                "fixed_push_symlink_timestamp,abb_exec,remount_shell,track_app,sendrecv_v2," +
                "sendrecv_v2_brotli,sendrecv_v2_lz4,sendrecv_v2_zstd,sendrecv_v2_dry_run_send,openscreen_mdns"
            writeMessage(createMessage("CNXN", features.toByteArray(Charsets.UTF_8), 16777217, 4096))
            var message = readMessage()
            if (message.commandString() == "AUTH") {
                // obf: C2192 case 4
                authenticate(message, keyPair) { message = it }
            }
            ConnectionResult(AuthResult.SUCCESS, message.toString(), null)
        } catch (e: Exception) {
            if (e is AdbUnauthorizedException) {
                ConnectionResult(AuthResult.REQUIRES_AUTH, null, null)
            } else {
                ConnectionResult(AuthResult.FAILURE, null, e)
            }
        }
    }

    /** Reads one [Message] (24-byte header + payload) and validates magic + payload checksum. */
    // obf: m2492
    fun readMessage(): Message {
        val headerBytes = ByteArray(24)
        val input = inputStream!!
        val read = input.read(headerBytes)
        if (read != 24) throw IllegalStateException("Read $read header bytes, expected 24")
        val command = bytesToInt(headerBytes.copyOfRange(0, 4))
        val arg0 = bytesToInt(headerBytes.copyOfRange(4, 8))
        val arg1 = bytesToInt(headerBytes.copyOfRange(8, 12))
        val dataLength = bytesToInt(headerBytes.copyOfRange(12, 16).reversedArray())
        val dataCrc32 = bytesToInt(headerBytes.copyOfRange(16, 20).reversedArray())
        val magic = bytesToInt(headerBytes.copyOfRange(20, 24))
        if (command.inv() != magic) throw IllegalArgumentException("invert command must equal to magic.")
        val header = MessageHeader(command, arg0, arg1, dataLength, dataCrc32, magic)
        val data = ByteArray(dataLength)
        val dataRead = inputStream!!.read(data)
        if (dataRead != dataLength) {
            throw IllegalArgumentException("Read $dataRead data bytes, expected $dataLength")
        }
        var checksum = 0
        for (i in 0 until dataLength) checksum += data[i].toInt() and 0xFF
        if (checksum == header.dataCrc32 || header.dataCrc32 == 0) {
            return Message(header, data)
        }
        throw IllegalArgumentException("dataCrc32 not equals 0 or i4: $checksum")
    }

    /** Serialises and writes one [Message] (command/arg0/arg1/magic big-endian, lengths little-endian). */
    // obf: m2493
    fun writeMessage(message: Message) {
        val header = message.messageHeader
        val out = ByteBuffer.allocate(header.dataLength + 24)
        val headerBuffer = ByteBuffer.allocate(24)
        headerBuffer.putInt(header.command)
        headerBuffer.putInt(header.arg0)
        headerBuffer.putInt(header.arg1)
        val len = header.dataLength
        headerBuffer.put(
            byteArrayOf((len shr 24).toByte(), (len shr 16).toByte(), (len shr 8).toByte(), len.toByte())
                .reversedArray(),
        )
        val crc = header.dataCrc32
        headerBuffer.put(
            byteArrayOf((crc shr 24).toByte(), (crc shr 16).toByte(), (crc shr 8).toByte(), crc.toByte())
                .reversedArray(),
        )
        headerBuffer.putInt(header.magic)
        out.put(headerBuffer.array())
        out.put(message.data)
        val os = outputStream!!
        os.write(out.array())
        outputStream!!.flush()
    }
}
