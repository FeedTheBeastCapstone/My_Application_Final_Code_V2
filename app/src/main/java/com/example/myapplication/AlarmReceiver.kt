package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

// A BroadcastReceiver to handle feeding time alarms
class FeedingAlarmReceiver : BroadcastReceiver() {

    // This method is triggered when the alarm is received
    override fun onReceive(context: Context, intent: Intent) {
        // Display a toast notification to inform the user it's feeding time
        Toast.makeText(context, "Feeding time!", Toast.LENGTH_SHORT).show()

        // Extract data passed with the alarm intent
        val scheduleId = intent.getIntExtra("scheduleId", -1) // Unique ID of the feeding schedule
        val feedingTime = intent.getStringExtra("feedingTime") ?: "Unknown time" // Feeding time (e.g., "10:00 AM")
        val dayOfWeek = intent.getStringExtra("dayOfWeek") ?: "Unknown day" // Day of the week (e.g., "Monday")

        // Send a notification to the user using a helper class
        val notificationHelper = NotificationHelper(context)
        notificationHelper.sendNotification(scheduleId, feedingTime, dayOfWeek)

        // Launch the UpdatesActivity to display additional information
        val updatesIntent = Intent(context, UpdatesActivity::class.java)
        updatesIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Ensures the activity starts in a new task
        context.startActivity(updatesIntent)
    }
}



