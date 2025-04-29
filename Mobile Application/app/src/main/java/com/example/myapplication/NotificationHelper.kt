package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import android.os.Build

class NotificationHelper(private val context: Context) {

    private val channelId = "feeding_channel"
    private val channelName = "Feeding Notifications"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH // Set importance high for better visibility
            ).apply {
                description = "Notifications for cat feeding schedule."
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun sendNotification(scheduleId: Int, time: String, day: String) {
        // Intent to open the app when the notification is clicked
        val intent = Intent(context, UpdatesActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // For Android 12+
        )

        // Build the notification
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder) // Placeholder icon
            .setContentTitle("Cat Feeding Reminder")
            .setContentText("Scheduled Feeding Complete")
            .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority for visibility
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Dismiss notification on tap
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Ensure it's visible on the lock screen
            .build()

        // Show the notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(scheduleId, notification) // Unique ID for each notification
    }
}



