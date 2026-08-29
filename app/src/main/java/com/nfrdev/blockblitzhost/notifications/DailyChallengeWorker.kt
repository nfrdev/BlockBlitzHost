package com.nfrdev.blockblitzhost.notifications

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class DailyChallengeWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        NotificationHelper.showDailyChallengeNotification(applicationContext)
        return Result.success()
    }
}
