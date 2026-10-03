// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.security

/**
 * Encrypts small secrets (camera usernames and passwords) for storage at rest.
 * The encoded form is Base64 of IV + ciphertext.
 */
interface CredentialCipher {
    fun encrypt(plain: String): String

    /** Throws when [encoded] is malformed or the key no longer matches (for example after a reinstall). */
    fun decrypt(encoded: String): String
}
