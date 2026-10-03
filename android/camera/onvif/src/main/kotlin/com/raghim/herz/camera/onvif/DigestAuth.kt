// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.onvif

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** A parsed RTSP/HTTP `WWW-Authenticate` challenge. Pure Kotlin. */
sealed interface AuthChallenge {
    data class Basic(val realm: String?) : AuthChallenge

    data class Digest(
        val realm: String,
        val nonce: String,
        val qop: String? = null,
        val opaque: String? = null,
        val algorithm: String? = null,
    ) : AuthChallenge
}

/** Builds `Authorization` headers from a [WWW-Authenticate][AuthChallenge] challenge. */
object DigestAuth {
    private val random = SecureRandom()

    /** Parses the first supported scheme in a `WWW-Authenticate` header value. */
    fun parseChallenge(header: String): AuthChallenge? {
        val trimmed = header.trim()
        val scheme = trimmed.substringBefore(' ', trimmed).lowercase()
        val params = parseParams(trimmed.substringAfter(' ', ""))
        return when (scheme) {
            "digest" -> {
                val realm = params["realm"] ?: return null
                val nonce = params["nonce"] ?: return null
                AuthChallenge.Digest(
                    realm = realm,
                    nonce = nonce,
                    qop = params["qop"],
                    opaque = params["opaque"],
                    algorithm = params["algorithm"],
                )
            }
            "basic" -> AuthChallenge.Basic(params["realm"])
            else -> null
        }
    }

    fun authorization(
        challenge: AuthChallenge,
        username: String,
        password: String,
        method: String,
        uri: String,
        cnonce: String = randomCnonce(),
        nc: String = "00000001",
    ): String = when (challenge) {
        is AuthChallenge.Basic -> basic(username, password)
        is AuthChallenge.Digest -> digest(challenge, username, password, method, uri, cnonce, nc)
    }

    fun basic(username: String, password: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))

    private fun digest(
        challenge: AuthChallenge.Digest,
        username: String,
        password: String,
        method: String,
        uri: String,
        cnonce: String,
        nc: String,
    ): String {
        val ha1 = md5("$username:${challenge.realm}:$password")
        val ha2 = md5("$method:$uri")
        val qop = challenge.qop?.split(',')?.map { it.trim() }?.firstOrNull { it.equals("auth", ignoreCase = true) }
        val response = if (qop != null) {
            md5("$ha1:${challenge.nonce}:$nc:$cnonce:$qop:$ha2")
        } else {
            md5("$ha1:${challenge.nonce}:$ha2")
        }
        val fields = buildList {
            add("username=\"$username\"")
            add("realm=\"${challenge.realm}\"")
            add("nonce=\"${challenge.nonce}\"")
            add("uri=\"$uri\"")
            add("response=\"$response\"")
            challenge.algorithm?.let { add("algorithm=$it") }
            challenge.opaque?.let { add("opaque=\"$it\"") }
            if (qop != null) {
                add("qop=$qop")
                add("nc=$nc")
                add("cnonce=\"$cnonce\"")
            }
        }
        return "Digest " + fields.joinToString(", ")
    }

    fun randomCnonce(): String {
        val bytes = ByteArray(8)
        random.nextBytes(bytes)
        return bytes.toHex()
    }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8)).toHex()

    private fun ByteArray.toHex(): String {
        val hex = "0123456789abcdef"
        val out = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            out.append(hex[value ushr 4])
            out.append(hex[value and 0x0F])
        }
        return out.toString()
    }

    /**
     * Parses `key=value` / `key="value"` comma-separated auth parameters. Tolerant of commas
     * inside quoted values (for example a `qop="auth,auth-int"` list).
     */
    private fun parseParams(raw: String): Map<String, String> {
        val params = LinkedHashMap<String, String>()
        var i = 0
        val n = raw.length
        while (i < n) {
            while (i < n && (raw[i] == ',' || raw[i].isWhitespace())) i++
            val keyStart = i
            while (i < n && raw[i] != '=' && raw[i] != ',') i++
            if (i >= n || raw[i] != '=') break
            val key = raw.substring(keyStart, i).trim().lowercase()
            i++ // skip '='
            val value: String
            if (i < n && raw[i] == '"') {
                i++
                val start = i
                while (i < n && raw[i] != '"') i++
                value = raw.substring(start, i)
                if (i < n) i++ // skip closing quote
            } else {
                val start = i
                while (i < n && raw[i] != ',') i++
                value = raw.substring(start, i).trim()
            }
            if (key.isNotEmpty()) params[key] = value
        }
        return params
    }
}
