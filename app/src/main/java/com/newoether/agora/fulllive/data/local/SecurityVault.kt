package com.newoether.agora.fulllive.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Loi du Sceau : Protection matérielle des secrets et clés d'API (OpenAI, Gemini, xAI)
 * et des données mémorisées de l'utilisateur via Android KeyStore (AES-256 GCM).
 * Inclut un fallback transparent pour les environnements de test JVM locaux.
 */
class SecurityVault(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("fullive_secure_vault", Context.MODE_PRIVATE)
    private var keyStore: KeyStore? = null
    private var fallbackKey: SecretKey? = null

    init {
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            keyStore = ks
            ensureMasterKey()
        } catch (e: Exception) {
            // Fallback for JVM unit test environments without AndroidKeyStore provider
            keyStore = null
            val keyBytes = "12345678901234567890123456789012".toByteArray(Charsets.UTF_8)
            fallbackKey = SecretKeySpec(keyBytes, "AES")
        }
    }

    private fun ensureMasterKey() {
        val ks = keyStore ?: return
        if (!ks.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        return try {
            (keyStore?.getKey(KEY_ALIAS, null) as? SecretKey) ?: fallbackKey!!
        } catch (e: Exception) {
            fallbackKey ?: SecretKeySpec("12345678901234567890123456789012".toByteArray(Charsets.UTF_8), "AES")
        }
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Combine IV (12 bytes) and ciphertext
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < 12) return ""
            val iv = ByteArray(12)
            System.arraycopy(combined, 0, iv, 0, 12)
            val ciphertext = ByteArray(combined.size - 12)
            System.arraycopy(combined, 12, ciphertext, 0, ciphertext.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val decryptedBytes = cipher.doFinal(ciphertext)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            return ""
        }
    }

    // Key accessors with hardware-backed encryption
    fun getGeminiKey(): String {
        val app = context.applicationContext as? com.newoether.agora.AgoraApplication
        val repo = app?.requireContainer()?.settingsRepository
        if (repo != null) {
            val key = repo.resolveActiveKey("google") ?: repo.resolveActiveKey("gemini")
            if (!key.isNullOrBlank()) return key
        }
        val encrypted = prefs.getString(PREF_GEMINI_KEY, "") ?: ""
        return decrypt(encrypted)
    }

    fun setGeminiKey(key: String) {
        prefs.edit().putString(PREF_GEMINI_KEY, encrypt(key.trim())).apply()
    }

    fun getOpenAiKey(): String {
        val app = context.applicationContext as? com.newoether.agora.AgoraApplication
        val repo = app?.requireContainer()?.settingsRepository
        if (repo != null) {
            val key = repo.resolveActiveKey("openai")
            if (!key.isNullOrBlank()) return key
        }
        val encrypted = prefs.getString(PREF_OPENAI_KEY, "") ?: ""
        return decrypt(encrypted)
    }

    fun setOpenAiKey(key: String) {
        prefs.edit().putString(PREF_OPENAI_KEY, encrypt(key.trim())).apply()
    }

    fun getXaiKey(): String {
        val app = context.applicationContext as? com.newoether.agora.AgoraApplication
        val repo = app?.requireContainer()?.settingsRepository
        if (repo != null) {
            val key = repo.resolveActiveKey("xai") ?: repo.resolveActiveKey("grok")
            if (!key.isNullOrBlank()) return key
        }
        val encrypted = prefs.getString(PREF_XAI_KEY, "") ?: ""
        return decrypt(encrypted)
    }

    fun setXaiKey(key: String) {
        prefs.edit().putString(PREF_XAI_KEY, encrypt(key.trim())).apply()
    }

    fun getCustomOpenAiKey(): String {
        val app = context.applicationContext as? com.newoether.agora.AgoraApplication
        val repo = app?.requireContainer()?.settingsRepository
        if (repo != null) {
            val key = repo.resolveActiveKey("openrouter")
                ?: repo.resolveActiveKey("zenmux")
                ?: repo.resolveActiveKey("groq")
                ?: repo.resolveActiveKey("deepseek")
            if (!key.isNullOrBlank()) return key
        }
        val encrypted = prefs.getString(PREF_CUSTOM_OPENAI_KEY, "") ?: ""
        return decrypt(encrypted)
    }

    fun setCustomOpenAiKey(key: String) {
        prefs.edit().putString(PREF_CUSTOM_OPENAI_KEY, encrypt(key.trim())).apply()
    }

    fun getCustomOpenAiBaseUrl(): String {
        val app = context.applicationContext as? com.newoether.agora.AgoraApplication
        val repo = app?.requireContainer()?.settingsRepository
        if (repo != null) {
            val customUrls = repo.providerBaseUrls.value
            val url = customUrls["openrouter"]
                ?: customUrls["zenmux"]
                ?: customUrls["groq"]
                ?: customUrls["deepseek"]
            if (!url.isNullOrBlank()) return url
        }
        return prefs.getString(PREF_CUSTOM_OPENAI_BASE_URL, "https://openrouter.ai/api/v1") ?: "https://openrouter.ai/api/v1"
    }

    fun setCustomOpenAiBaseUrl(url: String) {
        prefs.edit().putString(PREF_CUSTOM_OPENAI_BASE_URL, url.trim()).apply()
    }

    fun getCustomOpenAiModelId(): String {
        return prefs.getString(PREF_CUSTOM_OPENAI_MODEL_ID, "google/gemini-2.5-flash") ?: "google/gemini-2.5-flash"
    }

    fun setCustomOpenAiModelId(modelId: String) {
        prefs.edit().putString(PREF_CUSTOM_OPENAI_MODEL_ID, modelId.trim()).apply()
    }

    // General app settings
    fun getBudgetLimit(): Float? {
        val limit = prefs.getFloat(PREF_BUDGET_LIMIT, -1f)
        return if (limit > 0f) limit else null
    }

    fun setBudgetLimit(limit: Float?) {
        if (limit != null && limit > 0f) {
            prefs.edit().putFloat(PREF_BUDGET_LIMIT, limit).apply()
        } else {
            prefs.edit().remove(PREF_BUDGET_LIMIT).apply()
        }
    }

    fun getDefaultModel(): String {
        return prefs.getString(PREF_DEFAULT_MODEL, "gemini-3.8-live") ?: "gemini-3.8-live"
    }

    fun setDefaultModel(model: String) {
        prefs.edit().putString(PREF_DEFAULT_MODEL, model).apply()
    }

    fun getConversationLayout(): String {
        return prefs.getString(PREF_LAYOUT, "classic") ?: "classic"
    }

    fun setConversationLayout(layout: String) {
        prefs.edit().putString(PREF_LAYOUT, layout).apply()
    }

    fun getTheme(): String {
        return prefs.getString(PREF_THEME, "dark") ?: "dark"
    }

    fun setTheme(theme: String) {
        prefs.edit().putString(PREF_THEME, theme).apply()
    }

    fun getTargetTranslateLanguage(): String {
        return prefs.getString(PREF_TARGET_LANG, "en") ?: "en"
    }

    fun setTargetTranslateLanguage(lang: String) {
        prefs.edit().putString(PREF_TARGET_LANG, lang).apply()
    }

    fun getUserIdentityNotes(): String {
        val encrypted = prefs.getString(PREF_USER_NOTES, "") ?: ""
        return decrypt(encrypted)
    }

    fun setUserIdentityNotes(notes: String) {
        prefs.edit().putString(PREF_USER_NOTES, encrypt(notes.trim())).apply()
    }

    companion object {
        private const val KEY_ALIAS = "FullLiveMasterKey_v1"
        private const val PREF_GEMINI_KEY = "sec_gemini_key"
        private const val PREF_OPENAI_KEY = "sec_openai_key"
        private const val PREF_XAI_KEY = "sec_xai_key"
        private const val PREF_CUSTOM_OPENAI_KEY = "sec_custom_openai_key"
        private const val PREF_CUSTOM_OPENAI_BASE_URL = "setting_custom_openai_base_url"
        private const val PREF_CUSTOM_OPENAI_MODEL_ID = "setting_custom_openai_model_id"
        private const val PREF_BUDGET_LIMIT = "setting_budget_limit"
        private const val PREF_DEFAULT_MODEL = "setting_default_model"
        private const val PREF_LAYOUT = "setting_layout"
        private const val PREF_THEME = "setting_theme"
        private const val PREF_TARGET_LANG = "setting_target_lang"
        private const val PREF_USER_NOTES = "sec_user_notes"
    }
}
