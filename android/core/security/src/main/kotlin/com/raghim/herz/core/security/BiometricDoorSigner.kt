// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.security

import android.os.Build
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.domain.door.DoorCommandSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.Signature
import javax.inject.Inject
import kotlin.coroutines.resume

/** Spec D11: `BiometricPrompt(CryptoObject(Signature))` with [DeviceKey], strong biometric on every use. */
internal class BiometricDoorSigner @Inject constructor(
    private val host: BiometricHost,
) : DoorCommandSigner {

    override suspend fun sign(doorName: String, payload: ByteArray): AppResult<ByteArray> =
        withContext(Dispatchers.Main.immediate) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return@withContext AppResult.Failure(AppError.AuthenticationUnavailable)
            }
            val activity = host.activity ?: return@withContext AppResult.Failure(AppError.AuthenticationUnavailable)
            if (BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
                return@withContext AppResult.Failure(AppError.AuthenticationUnavailable)
            }
            val signature = try {
                DeviceKey.ensure()
                DeviceKey.signatureForPrompt()
            } catch (_: KeyPermanentlyInvalidatedException) {
                DeviceKey.delete()
                DeviceKey.ensure()
                return@withContext AppResult.Failure(AppError.KeyInvalidated)
            } catch (e: GeneralSecurityException) {
                return@withContext AppResult.Failure(AppError.Unknown(e.javaClass.simpleName))
            }
            authenticate(activity, doorName, signature, payload)
        }

    private suspend fun authenticate(
        activity: FragmentActivity,
        doorName: String,
        signature: Signature,
        payload: ByteArray,
    ): AppResult<ByteArray> = suspendCancellableCoroutine { continuation ->
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val signed = try {
                    val unlocked = result.cryptoObject?.signature
                    if (unlocked == null) {
                        AppResult.Failure(AppError.Unknown("No signature"))
                    } else {
                        AppResult.Success(unlocked.apply { update(payload) }.sign())
                    }
                } catch (e: GeneralSecurityException) {
                    AppResult.Failure(AppError.Unknown(e.javaClass.simpleName))
                }
                if (continuation.isActive) continuation.resume(signed)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val error = when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED,
                    -> AppError.Cancelled
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    -> AppError.AuthenticationUnavailable
                    else -> AppError.Unknown(errString.toString())
                }
                if (continuation.isActive) continuation.resume(AppResult.Failure(error))
            }
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.security_open_door_title, doorName))
            .setSubtitle(activity.getString(R.string.security_open_door_subtitle))
            .setNegativeButtonText(activity.getString(R.string.security_cancel))
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        prompt.authenticate(info, BiometricPrompt.CryptoObject(signature))
    }
}
