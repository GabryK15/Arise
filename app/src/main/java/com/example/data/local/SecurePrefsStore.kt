package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.ariseDataStore: DataStore<Preferences> by preferencesDataStore(name = "arise_system_prefs")

enum class AiProviderType(
    val id: String,
    val displayName: String,
    val keyHint: String,
    val defaultModel: String
) {
    GEMINI_BUILTIN("GEMINI_BUILTIN", "Gemini (Sistema Integrato)", "Nessuna chiave richiesta", "gemini-3.8-flash"),
    GROQ("GROQ", "Groq Cloud (Ultra-Veloce)", "gsk_...", "llama-3.3-70b-versatile"),
    OPENAI("OPENAI", "OpenAI", "sk-...", "gpt-4o-mini"),
    ANTHROPIC("ANTHROPIC", "Anthropic Claude", "sk-ant-...", "claude-3-5-sonnet-latest"),
    GEMINI_BYOK("GEMINI_BYOK", "Google Gemini (Chiave Personale)", "AIza...", "gemini-2.5-flash");

    companion object {
        fun fromId(id: String?): AiProviderType =
            entries.firstOrNull { it.id == id } ?: GEMINI_BUILTIN

        fun detectFromApiKey(key: String): AiProviderType? {
            val trimmed = key.trim()
            return when {
                trimmed.startsWith("gsk_") -> GROQ
                trimmed.startsWith("sk-ant-") -> ANTHROPIC
                trimmed.startsWith("sk-") -> OPENAI
                trimmed.startsWith("AIza") -> GEMINI_BYOK
                else -> null
            }
        }
    }
}

data class SystemPreferences(
    val activeProvider: AiProviderType = AiProviderType.GEMINI_BUILTIN,
    val selectedModels: Map<AiProviderType, String> = emptyMap(),
    val encryptedKeysMask: Map<AiProviderType, String> = emptyMap(),
    val biometricLockEnabled: Boolean = false,
    val healthConnectAutoSync: Boolean = true,
    val dailyNotificationsEnabled: Boolean = true,
    val notificationHour: Int = 18,
    val notificationMinute: Int = 30
)

data class WidgetSnapshot(
    val rank: String = "E",
    val level: Int = 1,
    val xpProgress: Float = 0f,
    val todayVolumeKg: Double = 0.0,
    val dailyTargetKg: Double = 3000.0,
    val questCompleted: Boolean = false,
    val streakDays: Int = 0
)

class SecurePrefsStore(private val context: Context) {

    private object Keys {
        val ACTIVE_PROVIDER = stringPreferencesKey("active_ai_provider")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock_enabled")
        val HEALTH_SYNC = booleanPreferencesKey("health_connect_auto_sync")
        val NOTIF_ENABLED = booleanPreferencesKey("daily_notif_enabled")
        val NOTIF_HOUR = intPreferencesKey("daily_notif_hour")
        val NOTIF_MINUTE = intPreferencesKey("daily_notif_minute")

        val WIDGET_RANK = stringPreferencesKey("widget_rank")
        val WIDGET_LEVEL = intPreferencesKey("widget_level")
        val WIDGET_XP_PCT = doublePreferencesKey("widget_xp_pct")
        val WIDGET_TODAY_VOL = doublePreferencesKey("widget_today_vol")
        val WIDGET_TARGET_VOL = doublePreferencesKey("widget_target_vol")
        val WIDGET_QUEST_DONE = booleanPreferencesKey("widget_quest_done")
        val WIDGET_STREAK = intPreferencesKey("widget_streak")

        fun apiKeyFor(provider: AiProviderType) = stringPreferencesKey("enc_key_${provider.id}")
        fun modelFor(provider: AiProviderType) = stringPreferencesKey("model_${provider.id}")
    }

    val preferencesFlow: Flow<SystemPreferences> = context.ariseDataStore.data.map { prefs ->
        val models = AiProviderType.entries.associateWith { p ->
            prefs[Keys.modelFor(p)]?.takeIf { it.isNotBlank() } ?: p.defaultModel
        }
        val masks = AiProviderType.entries.associateWith { p ->
            val raw = decryptSecret(prefs[Keys.apiKeyFor(p)])
            if (raw.isBlank()) "" else maskKey(raw)
        }
        SystemPreferences(
            activeProvider = AiProviderType.fromId(prefs[Keys.ACTIVE_PROVIDER]),
            selectedModels = models,
            encryptedKeysMask = masks,
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            healthConnectAutoSync = prefs[Keys.HEALTH_SYNC] ?: true,
            dailyNotificationsEnabled = prefs[Keys.NOTIF_ENABLED] ?: true,
            notificationHour = prefs[Keys.NOTIF_HOUR] ?: 18,
            notificationMinute = prefs[Keys.NOTIF_MINUTE] ?: 30
        )
    }

    val widgetSnapshotFlow: Flow<WidgetSnapshot> = context.ariseDataStore.data.map { prefs ->
        WidgetSnapshot(
            rank = prefs[Keys.WIDGET_RANK] ?: "E",
            level = prefs[Keys.WIDGET_LEVEL] ?: 1,
            xpProgress = (prefs[Keys.WIDGET_XP_PCT] ?: 0.0).toFloat(),
            todayVolumeKg = prefs[Keys.WIDGET_TODAY_VOL] ?: 0.0,
            dailyTargetKg = prefs[Keys.WIDGET_TARGET_VOL] ?: 3000.0,
            questCompleted = prefs[Keys.WIDGET_QUEST_DONE] ?: false,
            streakDays = prefs[Keys.WIDGET_STREAK] ?: 0
        )
    }

    suspend fun getDecryptedApiKey(provider: AiProviderType): String {
        if (provider == AiProviderType.GEMINI_BUILTIN) return ""
        val prefs = context.ariseDataStore.data.first()
        return decryptSecret(prefs[Keys.apiKeyFor(provider)])
    }

    suspend fun getSelectedModel(provider: AiProviderType): String {
        val prefs = context.ariseDataStore.data.first()
        return prefs[Keys.modelFor(provider)]?.takeIf { it.isNotBlank() } ?: provider.defaultModel
    }

    suspend fun saveProviderConfig(provider: AiProviderType, apiKey: String?, modelId: String?) {
        context.ariseDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_PROVIDER] = provider.id
            if (apiKey != null) {
                if (apiKey.isBlank()) {
                    prefs.remove(Keys.apiKeyFor(provider))
                } else {
                    prefs[Keys.apiKeyFor(provider)] = encryptSecret(apiKey.trim())
                }
            }
            if (!modelId.isNullOrBlank()) {
                prefs[Keys.modelFor(provider)] = modelId.trim()
            }
        }
    }

    suspend fun setActiveProvider(provider: AiProviderType) {
        context.ariseDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_PROVIDER] = provider.id
        }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.ariseDataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setHealthConnectAutoSync(enabled: Boolean) {
        context.ariseDataStore.edit { it[Keys.HEALTH_SYNC] = enabled }
    }

    suspend fun setDailyNotificationConfig(enabled: Boolean, hour: Int, minute: Int) {
        context.ariseDataStore.edit {
            it[Keys.NOTIF_ENABLED] = enabled
            it[Keys.NOTIF_HOUR] = hour.coerceIn(0, 23)
            it[Keys.NOTIF_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun saveWidgetSnapshot(snapshot: WidgetSnapshot) {
        context.ariseDataStore.edit {
            it[Keys.WIDGET_RANK] = snapshot.rank
            it[Keys.WIDGET_LEVEL] = snapshot.level
            it[Keys.WIDGET_XP_PCT] = snapshot.xpProgress.toDouble()
            it[Keys.WIDGET_TODAY_VOL] = snapshot.todayVolumeKg
            it[Keys.WIDGET_TARGET_VOL] = snapshot.dailyTargetKg
            it[Keys.WIDGET_QUEST_DONE] = snapshot.questCompleted
            it[Keys.WIDGET_STREAK] = snapshot.streakDays
        }
    }

    private fun maskKey(raw: String): String {
        if (raw.length <= 10) return "••••••••"
        return "${raw.take(6)}••••${raw.takeLast(4)}"
    }

    private fun getOrCreateAesKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "arise_system_api_aes_key"
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encryptSecret(plainText: String): String {
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAesKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        }.getOrElse { "" }
    }

    private fun decryptSecret(encoded: String?): String {
        if (encoded.isNullOrBlank()) return ""
        return runCatching {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val ivSize = combined[0].toInt()
            val iv = combined.copyOfRange(1, 1 + ivSize)
            val cipherBytes = combined.copyOfRange(1 + ivSize, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateAesKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }.getOrElse { "" }
    }
}
