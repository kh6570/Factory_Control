// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.onvif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class DigestAuthTest {

    @Test
    fun `digest with qop matches the RFC 2617 example`() {
        val challenge = AuthChallenge.Digest(
            realm = "testrealm@host.com",
            nonce = "dcd98b7102dd2f0e8b11d0f600bfb0c093",
            qop = "auth,auth-int",
            opaque = "5ccc069c403ebaf9f0171e9517f40e41",
        )

        val header = DigestAuth.authorization(
            challenge = challenge,
            username = "Mufasa",
            password = "Circle Of Life",
            method = "GET",
            uri = "/dir/index.html",
            cnonce = "0a4f113b",
            nc = "00000001",
        )

        assertEquals(
            "Digest username=\"Mufasa\", realm=\"testrealm@host.com\", " +
                "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", uri=\"/dir/index.html\", " +
                "response=\"6629fae49393a05397450978507c4ef1\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\", " +
                "qop=auth, nc=00000001, cnonce=\"0a4f113b\"",
            header,
        )
    }

    @Test
    fun `digest without qop uses the RFC 2069 response`() {
        val challenge = AuthChallenge.Digest(realm = "IP Camera", nonce = "n0nce", algorithm = "MD5")

        val header = DigestAuth.authorization(challenge, "admin", "pass", "DESCRIBE", "rtsp://10.0.0.5/live")

        val ha1 = md5("admin:IP Camera:pass")
        val ha2 = md5("DESCRIBE:rtsp://10.0.0.5/live")
        assertTrue(header.contains("response=\"${md5("$ha1:n0nce:$ha2")}\""))
        assertTrue(header.contains("algorithm=MD5"))
        assertFalse(header.contains("qop="))
        assertFalse(header.contains("cnonce="))
    }

    @Test
    fun `basic header is base64 of user and password`() {
        assertEquals("Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==", DigestAuth.basic("Aladdin", "open sesame"))
        assertEquals(
            "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==",
            DigestAuth.authorization(AuthChallenge.Basic("x"), "Aladdin", "open sesame", "DESCRIBE", "rtsp://h/"),
        )
    }

    @Test
    fun `parses a Hikvision style digest challenge`() {
        val challenge = DigestAuth.parseChallenge(
            "Digest realm=\"IP Camera(C2143)\", nonce=\"6b6a3c1a2f0e4d5c\", stale=\"FALSE\"",
        )

        assertEquals(AuthChallenge.Digest(realm = "IP Camera(C2143)", nonce = "6b6a3c1a2f0e4d5c"), challenge)
    }

    @Test
    fun `parses qop list, opaque, unquoted algorithm and commas inside quotes`() {
        val challenge = DigestAuth.parseChallenge(
            "Digest realm=\"Login to 4M0A1B2C, Inc\",qop=\"auth,auth-int\", nonce=\"abc\", " +
                "opaque=\"xyz\", algorithm=MD5",
        )

        assertEquals(
            AuthChallenge.Digest(
                realm = "Login to 4M0A1B2C, Inc",
                nonce = "abc",
                qop = "auth,auth-int",
                opaque = "xyz",
                algorithm = "MD5",
            ),
            challenge,
        )
    }

    @Test
    fun `parses basic and rejects unknown or incomplete challenges`() {
        assertEquals(AuthChallenge.Basic("Camera"), DigestAuth.parseChallenge("Basic realm=\"Camera\""))
        assertEquals(AuthChallenge.Basic(null), DigestAuth.parseChallenge("basic"))
        assertNull(DigestAuth.parseChallenge("Digest realm=\"no nonce\""))
        assertNull(DigestAuth.parseChallenge("Bearer token=1"))
        assertNull(DigestAuth.parseChallenge(""))
    }

    @Test
    fun `random cnonce is hex and changes`() {
        val first = DigestAuth.randomCnonce()

        assertTrue(first.matches(Regex("[0-9a-f]{16}")))
        assertTrue(first != DigestAuth.randomCnonce())
    }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
