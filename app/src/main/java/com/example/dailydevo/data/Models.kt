package com.example.dailydevo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "verses")
data class VerseEntity(
    @PrimaryKey val id: String,
    val collection: String,
    val source: String,
    val reference: String,
    val originalText: String,
    val meaning: String
)

@Entity(tableName = "translations", primaryKeys = ["verseId", "languageCode"])
data class TranslationEntity(val verseId: String, val languageCode: String, val text: String, val editorialDraft: Boolean = true)

@Entity(tableName = "favorites", primaryKeys = ["verseId"])
data class FavoriteEntity(val verseId: String, val savedAtEpochMillis: Long)

@Entity(tableName = "wallpaper_history", primaryKeys = ["localDate", "updateType"])
data class WallpaperHistoryEntity(
    val localDate: String,
    val updateType: String,
    val verseId: String,
    val target: String,
    val status: String,
    val message: String,
    val updatedAtEpochMillis: Long
)

data class VerseWithTranslation(val verse: VerseEntity, val translation: TranslationEntity?)
