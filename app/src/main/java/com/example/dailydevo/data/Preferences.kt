package com.example.dailydevo.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.dataStore by preferencesDataStore(name = "daily_devo_preferences")
data class UserPreferences(
    val language: String = "en",
    val automatic: Boolean = true,
    val target: String = "home",
    val darkWallpaper: Boolean = false,
    val gitaEnabled: Boolean = true
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val language = stringPreferencesKey("language")
        val automatic = booleanPreferencesKey("automatic")
        val target = stringPreferencesKey("target")
        val dark = booleanPreferencesKey("dark_wallpaper")
        val gitaEnabled = booleanPreferencesKey("collection_gita_enabled")
    }
    val preferences = context.dataStore.data.map { p ->
        val lang = p[Keys.language].orEmpty().ifBlank { "en" }.takeIf { it in setOf("en", "hi", "gu", "sa") } ?: "en"
        val target = p[Keys.target].orEmpty().takeIf { it in setOf("home", "lock", "both") } ?: "home"
        UserPreferences(lang, p[Keys.automatic] ?: true, target, p[Keys.dark] ?: false, p[Keys.gitaEnabled] ?: true)
    }
    suspend fun language(value: String) { context.dataStore.edit { it[Keys.language] = value.takeIf { lang -> lang in setOf("en", "hi", "gu", "sa") } ?: "en" } }
    suspend fun automatic(value: Boolean) { context.dataStore.edit { it[Keys.automatic] = value } }
    suspend fun target(value: String) { context.dataStore.edit { it[Keys.target] = value.takeIf { t -> t in setOf("home", "lock", "both") } ?: "home" } }
    suspend fun dark(value: Boolean) { context.dataStore.edit { it[Keys.dark] = value } }
    suspend fun gitaEnabled(value: Boolean) { context.dataStore.edit { it[Keys.gitaEnabled] = value } }
}
