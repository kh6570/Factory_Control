// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Device signing key for door commands. [ensure] and [signatureForPrompt] are spec D11 verbatim;
 * do not change them. `setUserAuthenticationParameters` needs Android 11, so door opening does too.
 */
@RequiresApi(Build.VERSION_CODES.R)
object DeviceKey {
    private const val ALIAS = "fsec_device_key"

    fun ensure() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(ALIAS)) return
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG) // every use
                .setInvalidatedByBiometricEnrollment(true)
                .build())
        }.generateKeyPair()
    }

    fun signatureForPrompt(): Signature {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return Signature.getInstance("SHA256withECDSA").apply {
            initSign(ks.getKey(ALIAS, null) as PrivateKey)
        }
    }

    /** The key registered with the server (or lock controller) to verify signatures. */
    fun publicKey(): PublicKey? {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return ks.getCertificate(ALIAS)?.publicKey
    }

    /** Removes an invalidated key so [ensure] creates a new one. The new key must be registered again. */
    fun delete() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
    }
}
