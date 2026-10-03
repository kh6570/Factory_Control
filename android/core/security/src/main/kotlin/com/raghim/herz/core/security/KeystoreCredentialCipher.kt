// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/**
 * AES-256-GCM with a non-exportable AndroidKeyStore key. No user authentication is
 * required so the live wall can open streams in the background.
 */
class KeystoreCredentialCipher @Inject constructor() : CredentialCipher {

    private val lock = Any()

    @Volatile
    private var cachedKey: SecretKey? = null

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        check(iv.size == IV_LENGTH_BYTES) { "Unexpected IV length ${iv.size}" }
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val out = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(ciphertext, 0, out, iv.size, ciphertext.size)
        return Base64.getEncoder().encodeToString(out)
    }

    override fun decrypt(encoded: String): String {
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size > IV_LENGTH_BYTES) { "Ciphertext too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, bytes, 0, IV_LENGTH_BYTES))
        val plain = cipher.doFinal(bytes, IV_LENGTH_BYTES, bytes.size - IV_LENGTH_BYTES)
        return String(plain, Charsets.UTF_8)
    }

    private fun key(): SecretKey = cachedKey ?: synchronized(lock) {
        cachedKey ?: loadOrCreateKey().also { cachedKey = it }
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .build()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "herz_camera_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}
