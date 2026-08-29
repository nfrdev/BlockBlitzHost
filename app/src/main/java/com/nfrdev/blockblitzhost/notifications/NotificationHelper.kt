package com.nfrdev.blockblitzhost.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.nfrdev.blockblitzhost.MainActivity
import com.nfrdev.blockblitzhost.R

object NotificationHelper {
    private const val CHANNEL_DAILY = "daily_challenge"
    private const val CHANNEL_REMINDERS = "game_reminders"
    private const val CHANNEL_ACHIEVEMENTS = "achievements"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val channels = listOf(
                NotificationChannel(CHANNEL_DAILY, "Daily Challenge", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Alerts for new daily block puzzle challenges"
                },
                NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Personalized reminders and high score updates"
                },
                NotificationChannel(CHANNEL_ACHIEVEMENTS, "Achievements", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Celebrations for game milestones"
                }
            )
            manager.createNotificationChannels(channels)
        }
    }

    fun showDailyChallengeNotification(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New Daily Challenge!")
            .setContentText("A new challenge is ready for you. Can you top the leaderboard today?")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1001, notification)
    }

    fun showInactivityNotification(context: Context, highScore: Int) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Come back and play!")
            .setContentText("Your high score of $highScore is waiting to be beaten. Brick by brick!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1002, notification)
    }

    fun showAchievementNotification(context: Context, achievementTitle: String, icon: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 2, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL_ACHIEVEMENTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Achievement Unlocked! $icon")
            .setContentText("Congratulations! You've earned the '$achievementTitle' badge.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(achievementTitle.hashCode(), notification)
    }
}
