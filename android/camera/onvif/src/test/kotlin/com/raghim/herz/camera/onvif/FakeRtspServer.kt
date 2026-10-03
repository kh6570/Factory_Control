// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.onvif

import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.util.Collections
import kotlin.concurrent.thread

/**
 * Minimal RTSP server on 127.0.0.1 for tests. Answers `DESCRIBE` with 401 Digest until the
 * client authorizes as [username]/[password], then 200 + SDP for [videoPaths], a 200 without
 * video for [audioOnlyPaths], and 404 for everything else.
 */
class FakeRtspServer(
    private val videoPaths: Set<String>,
    private val audioOnlyPaths: Set<String> = emptySet(),
    private val username: String = "admin",
    private val password: String = "secret",
    private val closeAfterChallenge: Boolean = false,
) : Closeable {
    private val server = ServerSocket(0, 50, InetAddress.getByName(HOST))
    private val clients = Collections.synchronizedList(mutableListOf<Socket>())

    /** Every request line received, in order. */
    val requestLines: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Every `Authorization` header received, in order. */
    val authorizations: MutableList<String> = Collections.synchronizedList(mutableListOf())

    val port: Int get() = server.localPort

    init {
        thread(isDaemon = true, name = "fake-rtsp-accept") {
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (e: Exception) {
                    break
                }
                clients += client
                thread(isDaemon = true, name = "fake-rtsp-client") { serve(client) }
            }
        }
    }

    private fun serve(client: Socket) {
        try {
            client.use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
                val output = socket.getOutputStream()
                while (true) {
                    val requestLine = reader.readLine() ?: break
                    if (requestLine.isBlank()) continue
                    requestLines += requestLine
                    val headers = generateSequence { reader.readLine() }
                        .takeWhile { it.isNotEmpty() }
                        .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
                    if (!respond(requestLine, headers, output)) break
                }
            }
        } catch (e: Exception) {
            // Client went away.
        }
    }

    /** Returns false when the connection should be closed. */
    private fun respond(requestLine: String, headers: Map<String, String>, output: OutputStream): Boolean {
        val cseq = headers["cseq"] ?: "0"
        val (method, uri) = requestLine.split(' ').let { it[0] to it[1] }
        val parsed = URI(uri)
        val path = parsed.rawPath + (parsed.rawQuery?.let { "?$it" } ?: "")
        val authorization = headers["authorization"]
        authorization?.let { authorizations += it }
        if (!isAuthorized(method, uri, authorization)) {
            output.write(
                ("RTSP/1.0 401 Unauthorized\r\nCSeq: $cseq\r\n" +
                    "WWW-Authenticate: Basic realm=\"$REALM\"\r\n" +
                    "WWW-Authenticate: Digest realm=\"$REALM\", nonce=\"$NONCE\", qop=\"auth\"\r\n" +
                    "Content-Length: 0\r\n\r\n").toByteArray(Charsets.ISO_8859_1),
            )
            output.flush()
            return !closeAfterChallenge
        }
        val body = when (path) {
            in videoPaths -> VIDEO_SDP
            in audioOnlyPaths -> AUDIO_SDP
            else -> null
        }
        val response = if (body == null) {
            "RTSP/1.0 404 Not Found\r\nCSeq: $cseq\r\nContent-Length: 0\r\n\r\n"
        } else {
            val bytes = body.toByteArray(Charsets.UTF_8)
            "RTSP/1.0 200 OK\r\nCSeq: $cseq\r\nContent-Type: application/sdp\r\n" +
                "Content-Length: ${bytes.size}\r\n\r\n$body"
        }
        output.write(response.toByteArray(Charsets.UTF_8))
        output.flush()
        return true
    }

    private fun isAuthorized(method: String, uri: String, authorization: String?): Boolean {
        if (authorization == null || !authorization.startsWith("Digest ")) return false
        val params = PARAM.findAll(authorization.removePrefix("Digest "))
            .associate { it.groupValues[1] to (it.groupValues[3].ifEmpty { it.groupValues[4] }) }
        if (params["username"] != username || params["uri"] != uri || params["nonce"] != NONCE) return false
        val ha1 = md5("$username:$REALM:$password")
        val ha2 = md5("$method:$uri")
        val expected = md5("$ha1:$NONCE:${params["nc"]}:${params["cnonce"]}:${params["qop"]}:$ha2")
        return params["response"] == expected
    }

    override fun close() {
        server.close()
        synchronized(clients) { clients.forEach { runCatching { it.close() } } }
    }

    companion object {
        const val HOST = "127.0.0.1"
        private const val REALM = "Herz Test"
        private const val NONCE = "4f2a9c0d17e3"
        private val PARAM = Regex("(\\w+)=(\"([^\"]*)\"|([^,\\s]+))")

        private val VIDEO_SDP = listOf(
            "v=0",
            "o=- 0 0 IN IP4 127.0.0.1",
            "s=Herz test",
            "t=0 0",
            "m=video 0 RTP/AVP 96",
            "a=rtpmap:96 H264/90000",
            "a=control:trackID=1",
        ).joinToString("\r\n", postfix = "\r\n")

        private val AUDIO_SDP = listOf(
            "v=0",
            "o=- 0 0 IN IP4 127.0.0.1",
            "s=Herz audio",
            "t=0 0",
            "m=audio 0 RTP/AVP 0",
        ).joinToString("\r\n", postfix = "\r\n")

        private fun md5(value: String): String =
            MessageDigest.getInstance("MD5").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
