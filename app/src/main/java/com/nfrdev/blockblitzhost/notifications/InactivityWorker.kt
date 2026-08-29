package com.nfrdev.blockblitzhost.notifications

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.nfrdev.blockblitzhost.blockBlitzDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class InactivityWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val highScore = runBlocking {
            applicationContext.blockBlitzDataStore.data.first()[intPreferencesKey("blockblitz_high_score")] ?: 0
        }
        NotificationHelper.showInactivityNotification(applicationContext, highScore)
        return Result.success()
    }
}
