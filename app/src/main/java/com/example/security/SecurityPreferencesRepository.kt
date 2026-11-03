package com.example.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nexus_hud_security_prefs"
)

data class SecuritySettings(
    val isConfigured: Boolean,
    val biometricEnabled: Boolean
)

class SecurityPreferencesRepository(private val context: Context) {

    private object Keys {
        val PASSWORD_HASH = stringPreferencesKey("master_password_hash")
        val PASSWORD_SALT = stringPreferencesKey("master_password_salt")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    }

    val securitySettingsFlow: Flow<SecuritySettings> = context.securityDataStore.data.map { prefs ->
        val hash = prefs[Keys.PASSWORD_HASH]
        SecuritySettings(
            isConfigured = !hash.isNullOrEmpty(),
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false
        )
    }

    suspend fun saveInitialCredentials(password: String, enableBiometrics: Boolean) {
        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        val saltHex = saltBytes.toHex()
        val hashHex = hashPassword(password, saltHex)

        context.securityDataStore.edit { prefs ->
            prefs[Keys.PASSWORD_SALT] = saltHex
            prefs[Keys.PASSWORD_HASH] = hashHex
            prefs[Keys.BIOMETRIC_ENABLED] = enableBiometrics
        }
    }

    suspend fun verifyPassword(inputPassword: String): Boolean {
        val prefs = context.securityDataStore.data.first()
        val storedHash = prefs[Keys.PASSWORD_HASH] ?: return false
        val storedSalt = prefs[Keys.PASSWORD_SALT] ?: return false
        val computed = hashPassword(inputPassword, storedSalt)
        return MessageDigest.isEqual(
            storedHash.toByteArray(Charsets.UTF_8),
            computed.toByteArray(Charsets.UTF_8)
        )
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.securityDataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_ENABLED] = enabled
        }
    }

    private fun hashPassword(password: String, saltHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val combined = "$saltHex:$password:NEXUS_HUD_V1".toByteArray(Charsets.UTF_8)
        var current = digest.digest(combined)
        repeat(4096) {
            digest.reset()
            current = digest.digest(current)
        }
        return current.toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

object BiometricAuthHelper {

    fun canAuthenticateWithBiometrics(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun getBiometricStatusSummary(context: Context): String {
        val manager = BiometricManager.from(context)
        return when (
            manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
        ) {
            BiometricManager.BIOMETRIC_SUCCESS -> "BIOMETRIC SENSOR READY"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "NO FINGERPRINT ENROLLED ON DEVICE"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "NO BIOMETRIC HARDWARE DETECTED"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "SENSOR TEMPORARILY UNAVAILABLE"
            else -> "BIOMETRIC FALLBACK TO CIPHER"
        }
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Fingerprint signature not recognized. Try again.")
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(promptInfo)
    }
}
