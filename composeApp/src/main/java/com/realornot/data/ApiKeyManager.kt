package com.realornot.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Securely stores and retrieves the Gemini API key using
 * Android's EncryptedSharedPreferences (AES256).
 */
object ApiKeyManager {

    private const val PREFS_FILE = "realornot_secure_prefs"
    private const val KEY_API_KEY = "gemini_api_key"

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Returns the stored API key, or null if none has been saved yet. */
    fun getApiKey(context: Context): String? {
        return getPrefs(context).getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
    }

    /** Saves the API key to encrypted storage. */
    fun saveApiKey(context: Context, apiKey: String) {
        getPrefs(context).edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    /** Returns true if an API key has been configured. */
    fun hasApiKey(context: Context): Boolean {
        return getApiKey(context) != null
    }

    /** Clears the stored API key. */
    fun clearApiKey(context: Context) {
        getPrefs(context).edit().remove(KEY_API_KEY).apply()
    }
}
