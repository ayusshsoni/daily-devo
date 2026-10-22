package com.example.dailydevo

import android.app.Application
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.dailydevo.data.DevoDatabase
import com.example.dailydevo.data.PreferencesRepository
import com.example.dailydevo.domain.DailySelection
import com.example.dailydevo.wallpaper.WallpaperApplier
import com.example.dailydevo.wallpaper.WallpaperRenderer
import com.example.dailydevo.worker.DailyWallpaperWorker
import com.example.dailydevo.worker.WallpaperScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DailyDevoApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val databaseReady = CompletableDeferred<Unit>()
    lateinit var database: DevoDatabase; private set
    lateinit var preferences: PreferencesRepository; private set
    lateinit var selection: DailySelection; private set
    lateinit var renderer: WallpaperRenderer; private set
    lateinit var applier: WallpaperApplier; private set

    override fun onCreate() {
        super.onCreate()
        database = DevoDatabase.create(this)
        preferences = PreferencesRepository(this)
        selection = DailySelection(database.dao())
        renderer = WallpaperRenderer(this)
        applier = WallpaperApplier(this)
        appScope.launch {
            DevoDatabase.seedIfEmpty(this@DailyDevoApp, database.dao())
            databaseReady.complete(Unit)
            preferences.preferences.collect { WallpaperScheduler.update(this@DailyDevoApp, it.automatic) }
        }
    }

    fun recoverMissedUpdate() {
        appScope.launch {
            databaseReady.await()
            val request = OneTimeWorkRequestBuilder<DailyWallpaperWorker>().build()
            WorkManager.getInstance(this@DailyDevoApp).enqueueUniqueWork("daily-wallpaper-open-check", ExistingWorkPolicy.KEEP, request)
        }
    }
}
