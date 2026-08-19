package com.personalmoney.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricGate {

    enum class Availability { AVAILABLE, NO_HARDWARE, NOT_ENROLLED, UNAVAILABLE }

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun availability(context: Context): Availability {
        val mgr = BiometricManager.from(context)
        return when (mgr.canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Availability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NOT_ENROLLED
            else -> Availability.UNAVAILABLE
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cancelLabel: String,
        onSuccess: () -> Unit,
        onFailure: (errorCode: Int, message: CharSequence) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailure(errorCode, errString)
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .also {
                // setNegativeButtonText cannot be set when DEVICE_CREDENTIAL is allowed
                if (AUTHENTICATORS and BiometricManager.Authenticators.DEVICE_CREDENTIAL == 0) {
                    it.setNegativeButtonText(cancelLabel)
                }
            }
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info)
    }
}
