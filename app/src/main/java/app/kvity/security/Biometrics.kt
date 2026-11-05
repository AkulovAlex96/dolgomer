package app.kvity.security

import app.kvity.R
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object Biometrics {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun isAvailable(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(activity: FragmentActivity, onSuccess: () -> Unit) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.app_name))
            .setSubtitle(activity.getString(R.string.lock_biometric_subtitle))
            .setNegativeButtonText(activity.getString(R.string.lock_biometric_negative))
            .setAllowedAuthenticators(AUTH)
            .build()
        prompt.authenticate(info)
    }
}
