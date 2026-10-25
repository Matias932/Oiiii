package com.finanzas.inteligentes.data

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Estado global de seguridad y apariencia (se refleja en la interfaz). */
object AppState {
    var themeMode by mutableIntStateOf(0) // 0 sistema, 1 claro, 2 oscuro
    var locked by mutableStateOf(false)
    var pinSet by mutableStateOf(false)
    var biometric by mutableStateOf(false)
}

/** El PIN se guarda solo como hash PBKDF2 con sal; nunca en texto. */
object Security {
    private fun sp(c: Context) = c.getSharedPreferences("finanzas_prefs", Context.MODE_PRIVATE)

    fun load(c: Context) {
        val p = sp(c)
        AppState.themeMode = p.getInt("theme", 0)
        AppState.pinSet = p.contains("pin_hash")
        AppState.biometric = p.getBoolean("biometric", false) && AppState.pinSet
        AppState.locked = AppState.pinSet
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)).encoded

    fun setPin(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp(c).edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            .apply()
        AppState.pinSet = true
    }

    fun checkPin(c: Context, pin: String): Boolean {
        val p = sp(c)
        val salt = Base64.decode(p.getString("pin_salt", null) ?: return false, Base64.NO_WRAP)
        val expected = Base64.decode(p.getString("pin_hash", null) ?: return false, Base64.NO_WRAP)
        return MessageDigest.isEqual(hash(pin, salt), expected)
    }

    fun clearPin(c: Context) {
        sp(c).edit().remove("pin_salt").remove("pin_hash").remove("biometric").apply()
        AppState.pinSet = false
        AppState.biometric = false
    }

    fun setBiometric(c: Context, on: Boolean) {
        sp(c).edit().putBoolean("biometric", on).apply()
        AppState.biometric = on
    }

    fun setTheme(c: Context, mode: Int) {
        sp(c).edit().putInt("theme", mode).apply()
        AppState.themeMode = mode
    }
}
