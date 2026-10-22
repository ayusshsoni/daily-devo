package com.example.dailydevo.data

import android.content.Context
import androidx.room.*
import androidx.room.RoomDatabase
import com.example.dailydevo.domain.ScheduledClaimPolicy
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

@Dao
interface DevoDao {
    @Query("SELECT * FROM verses ORDER BY collection, reference") fun allVerses(): Flow<List<VerseEntity>>
    @Query("SELECT * FROM verses ORDER BY collection, reference") suspend fun versesOnce(): List<VerseEntity>
    @Query("SELECT * FROM verses WHERE id = :id") suspend fun verse(id: String): VerseEntity?
    @Query("SELECT * FROM translations WHERE verseId = :id AND languageCode = :language LIMIT 1") suspend fun translation(id: String, language: String): TranslationEntity?
    @Query("SELECT * FROM favorites ORDER BY savedAtEpochMillis DESC") fun favorites(): Flow<List<FavoriteEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE verseId = :id)") fun isFavorite(id: String): Flow<Boolean>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertVerses(items: List<VerseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTranslations(items: List<TranslationEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveFavorite(item: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE verseId = :id") suspend fun removeFavorite(id: String)
    @Query("SELECT * FROM wallpaper_history WHERE localDate = :date AND updateType = 'scheduled' LIMIT 1") suspend fun scheduledHistory(date: String): WallpaperHistoryEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveHistory(item: WallpaperHistoryEntity)
    @Transaction
    suspend fun claimScheduled(date: String, verseId: String, target: String, now: Long): Boolean {
        val previous = scheduledHistory(date)
        if (!ScheduledClaimPolicy.canClaim(previous?.status, previous?.updatedAtEpochMillis, now)) return false
        saveHistory(WallpaperHistoryEntity(date, "scheduled", verseId, target, "running", "Wallpaper update in progress", now))
        return true
    }
    @Query("SELECT * FROM wallpaper_history ORDER BY updatedAtEpochMillis DESC LIMIT 1") fun latestHistory(): Flow<WallpaperHistoryEntity?>
}

@Database(entities = [VerseEntity::class, TranslationEntity::class, FavoriteEntity::class, WallpaperHistoryEntity::class], version = 1, exportSchema = true)
abstract class DevoDatabase : RoomDatabase() {
    abstract fun dao(): DevoDao

    companion object {
        fun create(context: Context): DevoDatabase = Room.databaseBuilder(context, DevoDatabase::class.java, "daily-devo.db").build()

        suspend fun seedIfEmpty(context: Context, dao: DevoDao) {
            if (dao.versesOnce().isNotEmpty()) return
            val json = JSONObject(context.assets.open("content/seed_content.json").bufferedReader().use { it.readText() })
            val verses = mutableListOf<VerseEntity>()
            val translations = mutableListOf<TranslationEntity>()
            val array = json.getJSONArray("verses")
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                verses += VerseEntity(item.getString("id"), item.getString("collection"), item.getString("source"), item.getString("reference"), item.getString("original"), item.getString("meaning"))
                val translated = item.getJSONObject("translations")
                translated.keys().forEach { language -> translations += TranslationEntity(item.getString("id"), language, translated.getString(language)) }
            }
            dao.insertVerses(verses)
            dao.insertTranslations(translations)
        }
    }
}
