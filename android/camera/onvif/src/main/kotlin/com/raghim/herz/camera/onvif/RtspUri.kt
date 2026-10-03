// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.onvif

import com.raghim.herz.core.model.CameraCredentials
import java.net.URI
import java.net.URLDecoder

/** An RTSP URL split into a credential-free address and any userinfo found in it. Pure Kotlin. */
data class RtspUri(
    val sanitized: String,
    val credentials: CameraCredentials?,
    val host: String?,
    val port: Int,
) {
    companion object {
        const val DEFAULT_PORT = 554

        private val HOST_PORT = Regex("[A-Za-z0-9.\\-\\[\\]:]+")
        private val PERCENT_ENCODED = Regex("([^%@/?#]|%[0-9A-Fa-f]{2})*")

        /**
         * Removes `user:pass@` userinfo from [raw] so it never travels on the wire or is stored,
         * returning the extracted credentials separately. Users often paste passwords that are
         * not percent-encoded (`admin:p@ss/1@10.0.0.5`), so the last `@` that is followed by a
         * plausible `host[:port]` ends the userinfo.
         */
        fun parse(raw: String): RtspUri {
            val input = raw.trim()
            val schemeEnd = input.indexOf("://")
            if (schemeEnd < 0) return RtspUri(input, null, null, DEFAULT_PORT)
            val prefix = input.substring(0, schemeEnd + 3)
            val rest = input.substring(schemeEnd + 3)
            val at = userInfoEnd(rest)
            val sanitized = if (at < 0) input else prefix + rest.substring(at + 1)
            val address = try {
                URI(sanitized)
            } catch (e: Exception) {
                null
            }
            return RtspUri(
                sanitized = sanitized,
                credentials = if (at < 0) null else credentialsOf(rest.substring(0, at)),
                host = address?.host,
                port = address?.port?.takeIf { it > 0 } ?: DEFAULT_PORT,
            )
        }

        private fun userInfoEnd(rest: String): Int {
            var at = rest.lastIndexOf('@')
            while (at >= 0) {
                if (looksLikeHostPort(rest, at + 1) && looksLikeUserInfo(rest.substring(0, at))) return at
                at = if (at == 0) -1 else rest.lastIndexOf('@', at - 1)
            }
            return -1
        }

        private fun looksLikeHostPort(rest: String, start: Int): Boolean {
            val end = rest.indexOfAny(charArrayOf('/', '?', '#'), start).let { if (it < 0) rest.length else it }
            return end > start && HOST_PORT.matches(rest.substring(start, end))
        }

        /** Rejects `host/path@x`, where the `@` belongs to the path rather than a password. */
        private fun looksLikeUserInfo(candidate: String): Boolean {
            val slash = candidate.indexOf('/')
            return slash < 0 || candidate.indexOf(':') in 0 until slash
        }

        private fun credentialsOf(userInfo: String): CameraCredentials {
            val encoded = PERCENT_ENCODED.matches(userInfo)
            val name = userInfo.substringBefore(':')
            val secret = if (':' in userInfo) userInfo.substringAfter(':') else ""
            return if (encoded) CameraCredentials(decode(name), decode(secret)) else CameraCredentials(name, secret)
        }

        /** Percent-decoding only: a `+` in userinfo is a literal plus, not a space. */
        private fun decode(value: String): String = try {
            URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
        } catch (e: IllegalArgumentException) {
            value
        }
    }
}

/** Builds `rtsp://host[:port]<path>`. */
internal fun rtspUrl(host: String, port: Int, path: String): String {
    val literal = if (':' in host && !host.startsWith("[")) "[$host]" else host
    val authority = if (port == RtspUri.DEFAULT_PORT) literal else "$literal:$port"
    val suffix = if (path.startsWith("/")) path else "/$path"
    return "rtsp://$authority$suffix"
}
