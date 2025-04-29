package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class ErrorMonitoringService : Service() {

    // Helper class to send notifications
    private lateinit var notificationHelp: NotificationHelp

    // Firebase database reference
    private lateinit var database: FirebaseDatabase

    // References to specific error nodes in Firebase
    private lateinit var feederRef: DatabaseReference
    private lateinit var powerRef: DatabaseReference
    private lateinit var connectionRef: DatabaseReference

    // Tracks last sent thresholds to avoid duplicate notifications
    private var lastBatteryThreshold = 100
    private var lastFoodThreshold = 100

    override fun onCreate() {
        super.onCreate()
        notificationHelp = NotificationHelp(applicationContext)
        database = FirebaseDatabase.getInstance()

        // Initialize database references for error monitoring
        feederRef = database.reference.child("Feeder_error")
        powerRef = database.reference.child("Power_error")
        connectionRef = database.reference.child("Connection_error")

        // Start listening for error triggers
        setupErrorListener(feederRef, "Feeder Error")
        setupErrorListener(powerRef, "Power Error")
        setupErrorListener(connectionRef, "Connection Error")

        // Start monitoring battery and food levels
        setupBatteryLevelListener()
        setupFoodLevelListener()

        // Start service as a foreground service to ensure persistence
        startForeground(1, createForegroundNotification())
    }

    // Builds the persistent foreground notification shown to the user
    private fun createForegroundNotification(): Notification {
        val notificationChannelId = "monitor_channel"

        // Create a channel for Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Error Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        // Return a notification
        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Monitoring for errors...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .build()
    }

    // Listens for a specific error node and sends a notification if triggered
    private fun setupErrorListener(ref: DatabaseReference, type: String) {
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Read error status, whether it's already notified, and timestamp
                val status = snapshot.child("status").getValue(Int::class.java) ?: 0
                val notified = snapshot.child("notified").getValue(Boolean::class.java) ?: false
                val timestamp = snapshot.child("timestamp").getValue(String::class.java) ?: ""

                // If the error is new and hasn't been notified
                if (status == 1 && !notified) {
                    val time = if (timestamp.isEmpty()) {
                        // Create and store timestamp if missing
                        val newTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                        ref.child("timestamp").setValue(newTime)
                        newTime
                    } else timestamp

                    // Send a push notification
                    notificationHelp.sendErrorNotification(type)

                    // Mark as notified and store timestamp
                    ref.child("notified").setValue(true)
                    ref.child("timestamp").setValue(time)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // Listen to battery level and issue notifications at thresholds
    private fun setupBatteryLevelListener() {
        database.reference.child("battery_level").child("level")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val level = snapshot.getValue(Double::class.java) ?: return
                    checkAndNotifyLevel("Battery", level, 2001)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // Listen to food level and issue notifications at thresholds
    private fun setupFoodLevelListener() {
        database.reference.child("food_level").child("level")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val level = snapshot.getValue(Double::class.java) ?: return
                    checkAndNotifyLevel("Food", level, 2002)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // Determines which threshold has been crossed and sends a notification
    private fun checkAndNotifyLevel(type: String, level: Double, notificationId: Int) {
        // Determine current threshold
        val threshold = when {
            level <= 1 -> 1
            level < 25 -> 25
            level < 50 -> 50
            level < 75 -> 75
            else -> 100 // No notification needed
        }

        // Only notify if it crossed into a lower threshold
        val lastThreshold = if (type == "Battery") lastBatteryThreshold else lastFoodThreshold
        if (threshold >= lastThreshold) return

        // Choose appropriate message
        val message = when (threshold) {
            1 -> "$type critically low! Immediate action required."
            25 -> "$type level is below 25%."
            50 -> "$type level is below 50%."
            75 -> "$type level is below 75%."
            else -> return
        }

        // Update the last threshold that was sent
        if (type == "Battery") lastBatteryThreshold = threshold
        else lastFoodThreshold = threshold

        // Send the notification
        // sendThresholdNotification(type, message, notificationId)
    }

    /*
    // Builds and sends the battery/food level notification
    private fun sendThresholdNotification(title: String, message: String, notificationId: Int) {
        val builder = NotificationCompat.Builder(this, "monitor_channel")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

     */

    // This service is not meant to be bound to an activity
    override fun onBind(intent: Intent?): IBinder? = null
}

