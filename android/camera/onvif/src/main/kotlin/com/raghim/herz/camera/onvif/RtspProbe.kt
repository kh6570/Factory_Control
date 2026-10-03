// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.onvif

import com.raghim.herz.core.model.CameraCredentials
import kotlinx.coroutines.runInterruptible
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

sealed interface ProbeResult {
    data class Ok(val sdp: String) : ProbeResult

    data object Unauthorized : ProbeResult

    /** The server answered but has no video stream at this path. */
    data object NotFound : ProbeResult

    /** Connection refused, timed out or broke. */
    data object Unreachable : ProbeResult
}

/**
 * Checks one RTSP URL with `DESCRIBE`, answering one 401 challenge with Digest or Basic auth.
 * Pure Kotlin (java.net only). Call from an IO dispatcher.
 */
class RtspProbe(
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS,
) {
    /** [uri] must not contain userinfo; pass [credentials] separately. */
    suspend fun describe(uri: String, credentials: CameraCredentials?): ProbeResult {
        val target = RtspUri.parse(uri)
        val host = target.host ?: return ProbeResult.NotFound
        val requestUri = target.sanitized
        return try {
            Socket().useCancellable<Socket, ProbeResult> { socket ->
                val connection = connect(socket, host, target.port)
                val first = connection.send(request(requestUri, cseq = 1, authorization = null))
                if (first.status != STATUS_UNAUTHORIZED) return@useCancellable first.toResult()
                val challenge = first.challenge()
                if (credentials == null || challenge == null) return@useCancellable ProbeResult.Unauthorized
                val authorization = DigestAuth.authorization(
                    challenge = challenge,
                    username = credentials.username,
                    password = credentials.password,
                    method = METHOD,
                    uri = requestUri,
                )
                val retry = request(requestUri, cseq = 2, authorization = authorization)
                val second = try {
                    connection.send(retry)
                } catch (e: IOException) {
                    null
                }
                (second ?: sendOnNewConnection(host, target.port, retry)).toResult()
            }
        } catch (e: IOException) {
            ProbeResult.Unreachable
        }
    }

    /** Some cameras close the connection after a 401, so the authorized request may need a new one. */
    private suspend fun sendOnNewConnection(host: String, port: Int, request: String): RtspResponse =
        Socket().useCancellable { socket -> connect(socket, host, port).send(request) }

    private suspend fun connect(socket: Socket, host: String, port: Int): Connection {
        runInterruptible {
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.soTimeout = readTimeoutMs
        }
        return Connection(socket)
    }

    private fun request(uri: String, cseq: Int, authorization: String?): String = buildString {
        append("$METHOD $uri RTSP/1.0\r\n")
        append("CSeq: $cseq\r\n")
        append("Accept: application/sdp\r\n")
        authorization?.let { append("Authorization: $it\r\n") }
        append("User-Agent: Herz\r\n")
        append("\r\n")
    }

    private class Connection(socket: Socket) {
        private val input = BufferedInputStream(socket.getInputStream())
        private val output = socket.getOutputStream()

        suspend fun send(request: String): RtspResponse = runInterruptible {
            output.write(request.toByteArray(Charsets.UTF_8))
            output.flush()
            RtspResponse.read(input)
        }
    }

    private fun RtspResponse.toResult(): ProbeResult = when {
        status == STATUS_OK && hasVideo(body) -> ProbeResult.Ok(body)
        status == STATUS_UNAUTHORIZED -> ProbeResult.Unauthorized
        else -> ProbeResult.NotFound
    }

    /** Digest is preferred when a server offers several schemes. */
    private fun RtspResponse.challenge(): AuthChallenge? {
        val challenges = headers("WWW-Authenticate").mapNotNull(DigestAuth::parseChallenge)
        return challenges.firstOrNull { it is AuthChallenge.Digest } ?: challenges.firstOrNull()
    }

    private fun hasVideo(sdp: String): Boolean =
        sdp.lineSequence().any { it.trim().startsWith("m=video", ignoreCase = true) }

    private companion object {
        const val METHOD = "DESCRIBE"
        const val STATUS_OK = 200
        const val STATUS_UNAUTHORIZED = 401
        const val CONNECT_TIMEOUT_MS = 2_500
        const val READ_TIMEOUT_MS = 3_000
    }
}

/** A parsed RTSP response. Status 0 means the status line was not RTSP. */
internal class RtspResponse(
    val status: Int,
    private val headerLines: List<Pair<String, String>>,
    val body: String,
) {
    fun headers(name: String): List<String> =
        headerLines.filter { it.first.equals(name, ignoreCase = true) }.map { it.second }

    companion object {
        private const val MAX_LINE = 8 * 1024
        private const val MAX_HEADERS = 100
        private const val MAX_BODY = 64 * 1024

        fun read(input: InputStream): RtspResponse {
            val statusLine = readLine(input) ?: throw IOException("Connection closed")
            val parts = statusLine.split(' ', limit = 3)
            val status = if (parts.size >= 2 && parts[0].startsWith("RTSP/")) parts[1].toIntOrNull() ?: 0 else 0
            val headers = ArrayList<Pair<String, String>>()
            while (headers.size < MAX_HEADERS) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) headers += line.substring(0, colon).trim() to line.substring(colon + 1).trim()
            }
            val length = headers.firstOrNull { it.first.equals("Content-Length", ignoreCase = true) }
                ?.second?.toIntOrNull()?.coerceIn(0, MAX_BODY) ?: 0
            return RtspResponse(status, headers, readBody(input, length))
        }

        private fun readBody(input: InputStream, length: Int): String {
            val bytes = ByteArray(length)
            var read = 0
            while (read < length) {
                val count = input.read(bytes, read, length - read)
                if (count < 0) break
                read += count
            }
            return String(bytes, 0, read, Charsets.UTF_8)
        }

        /** Reads one CRLF- or LF-terminated line, or null at end of stream. */
        private fun readLine(input: InputStream): String? {
            val line = StringBuilder()
            while (line.length < MAX_LINE) {
                val byte = input.read()
                if (byte < 0) return if (line.isEmpty()) null else line.toString()
                if (byte == '\n'.code) return line.toString().removeSuffix("\r")
                line.append(byte.toChar())
            }
            return line.toString()
        }
    }
}
