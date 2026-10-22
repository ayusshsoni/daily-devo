package com.example.dailydevo.worker

import android.content.Context
import androidx.work.*
import com.example.dailydevo.DailyDevoApp
import com.example.dailydevo.data.WallpaperHistoryEntity
import com.example.dailydevo.domain.TranslationResolver
import com.example.dailydevo.wallpaper.ApplyResult
import com.example.dailydevo.wallpaper.WallpaperContent
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class DailyWallpaperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as DailyDevoApp
        val prefs = app.preferences.preferences.first()
        if (!prefs.automatic) return Result.success()
        val date = LocalDate.now(ZoneId.systemDefault())
        val dao = app.database.dao()
        val verse = app.selection.forDate(date, prefs.gitaEnabled)
        if (verse == null) {
            dao.saveHistory(WallpaperHistoryEntity(date.toString(), "scheduled", "", prefs.target, "failure", "No eligible collection is enabled", System.currentTimeMillis()))
            return Result.failure()
        }
        val startedAt = System.currentTimeMillis()
        if (!dao.claimScheduled(date.toString(), verse.id, prefs.target, startedAt)) return Result.success()
        val translation = if (prefs.language == "sa") null else dao.translation(verse.id, prefs.language)
        val text = TranslationResolver.resolve(verse, prefs.language, translation).text
        val bitmap = try { app.renderer.render(WallpaperContent(verse, text, prefs.language), prefs.darkWallpaper) }
        catch (e: Exception) {
            dao.saveHistory(WallpaperHistoryEntity(date.toString(), "scheduled", verse.id, prefs.target, "failure", "Image rendering failed: ${e.localizedMessage ?: "unknown error"}", System.currentTimeMillis()))
            return Result.retry()
        }
        val result = app.applier.apply(bitmap, prefs.target)
        bitmap.recycle()
        val success = result is ApplyResult.Applied
        val message = if (result is ApplyResult.Failed) result.reason else "Wallpaper applied"
        dao.saveHistory(WallpaperHistoryEntity(date.toString(), "scheduled", verse.id, prefs.target, if (success) "success" else "failure", message, System.currentTimeMillis()))
        return if (success) Result.success() else Result.retry()
    }
}

object WallpaperScheduler {
    private const val UNIQUE = "daily-wallpaper"
    fun update(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) { manager.cancelUniqueWork(UNIQUE); return }
        val now = LocalDateTime.now(ZoneId.systemDefault())
        var next = now.toLocalDate().atTime(LocalTime.of(6, 0))
        if (!now.isBefore(next)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<DailyWallpaperWorker>(Duration.ofHours(24), Duration.ofHours(3))
            .setInitialDelay(Duration.between(now, next)).build()
        manager.enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
