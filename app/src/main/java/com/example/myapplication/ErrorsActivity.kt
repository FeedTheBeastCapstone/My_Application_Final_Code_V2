package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class ErrorsActivity : AppCompatActivity() {

    private val TAG = "ErrorsActivity" // For logging

    // UI Elements
    private lateinit var feederErrorCard: CardView
    private lateinit var powerErrorCard: CardView
    private lateinit var connectionErrorCard: CardView
    private lateinit var feederStatusText: TextView
    private lateinit var powerStatusText: TextView
    private lateinit var connectionStatusText: TextView
    private lateinit var feederTimestampText: TextView
    private lateinit var powerTimestampText: TextView
    private lateinit var connectionTimestampText: TextView

    // Firebase References
    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private val feederErrorRef: DatabaseReference = firebaseDatabase.reference.child("Feeder_error")
    private val powerErrorRef: DatabaseReference = firebaseDatabase.reference.child("Power_error")
    private val connectionErrorRef: DatabaseReference = firebaseDatabase.reference.child("Connection_error")

    // Timestamps formatting
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private lateinit var notificationHelp: NotificationHelp // Helper for notifications

    // Connection monitoring
    private var lastStatusChangeTime: Long = System.currentTimeMillis()
    private val connectionTimeoutHandler = Handler(Looper.getMainLooper())
    private val connectionTimeoutRunnable = Runnable {
        checkConnectionTimeout()
    }
    private val monitorCheckHandler = Handler(Looper.getMainLooper())
    private val monitorCheckRunnable = Runnable {
        checkMonitorNodeChange()
    }
    private val connectiontimeoutms: Long = 5 * 60 * 1000 // 5 minutes in milliseconds

    // Monitor node tracking
    private var lastMonitorChangeTime: Long = System.currentTimeMillis()
    private var lastMonitorValue: Int = -1 // Will store either 0 or 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_errors)

        notificationHelp = NotificationHelp(applicationContext) // Helper is initialized
        initUIComponents()
        setupErrorCards()
        setupErrorListeners()
        startConnectionMonitoring()

        // Immediately check the monitor node and start monitoring it
        setupMonitorNodeTracking()

        val dashboardButton: Button = findViewById(R.id.dashboardButton) // Dashboard button
        // Set click listener
        dashboardButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java)) // Push to Dashboard
            finish() // Close ErrorsActivity
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove the timeout handlers when activity is destroyed
        connectionTimeoutHandler.removeCallbacks(connectionTimeoutRunnable)
        monitorCheckHandler.removeCallbacks(monitorCheckRunnable)
    }

    // Initialize UI
    private fun initUIComponents() {
        feederErrorCard = findViewById(R.id.feederErrorCard)
        powerErrorCard = findViewById(R.id.powerErrorCard)
        connectionErrorCard = findViewById(R.id.connectionErrorCard)

        feederStatusText = findViewById(R.id.feederStatusText)
        powerStatusText = findViewById(R.id.powerStatusText)
        connectionStatusText = findViewById(R.id.connectionStatusText)

        feederTimestampText = findViewById(R.id.feederTimestampText)
        powerTimestampText = findViewById(R.id.powerTimestampText)
        connectionTimestampText = findViewById(R.id.connectionTimestampText)
    }

    // On click listeners for the errors
    private fun setupErrorCards() {
        feederErrorCard.setOnClickListener {
            // Resolve dialogue only appears of the error is red
            if (feederErrorCard.cardBackgroundColor.defaultColor == Color.RED) {
                showResolveDialog(feederErrorRef, "Feeder Error")
            }
        }

        powerErrorCard.setOnClickListener {
            if (powerErrorCard.cardBackgroundColor.defaultColor == Color.RED) {
                showResolveDialog(powerErrorRef, "Power Error")
            }
        }
        connectionErrorCard.setOnClickListener {
            if (connectionErrorCard.cardBackgroundColor.defaultColor == Color.RED) {
                showResolveDialog(connectionErrorRef, "Connection Error")
            }
        }
    }

    // Firebase listener for each error
    private fun setupErrorListeners() {
        setupErrorListener(feederErrorRef, feederErrorCard, feederStatusText, feederTimestampText, "Feeder Error")
        setupErrorListener(powerErrorRef, powerErrorCard, powerStatusText, powerTimestampText, "Power Error")
        setupConnectionErrorListener()
    }

    // Start monitoring the connection status
    private fun startConnectionMonitoring() {
        // Schedule the first check
        connectionTimeoutHandler.postDelayed(connectionTimeoutRunnable, connectiontimeoutms)
    }

    // Setup tracking for the monitor node - binary flip version
    private fun setupMonitorNodeTracking() {
        // First, get the current value of the monitor node
        connectionErrorRef.child("monitor").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                lastMonitorValue = snapshot.getValue(Int::class.java) ?: 0
                lastMonitorChangeTime = System.currentTimeMillis()

                // Now set up continuous monitoring
                setupMonitorNodeListener()

                // Start checking for monitor node changes
                monitorCheckHandler.postDelayed(monitorCheckRunnable, connectiontimeoutms)

                Log.d(TAG, "Initial monitor value: $lastMonitorValue, time: $lastMonitorChangeTime")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to read monitor node: ${error.message}")
                // Set connection error since we can't even read the initial value
                setConnectionError("Failed to read monitor node")
            }
        })
    }

    // Setup listener for the monitor node - binary flip version
    private fun setupMonitorNodeListener() {
        connectionErrorRef.child("monitor").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentValue = snapshot.getValue(Int::class.java) ?: 0
                val currentTime = System.currentTimeMillis()

                Log.d(TAG, "Monitor node changed to: $currentValue (previous: $lastMonitorValue)")

                // Only consider it a valid change if the value flipped (0 to 1 or 1 to 0)
                if ((lastMonitorValue == 0 && currentValue == 1) ||
                    (lastMonitorValue == 1 && currentValue == 0)) {

                    Log.d(TAG, "Monitor value flipped from $lastMonitorValue to $currentValue")
                    lastMonitorValue = currentValue
                    lastMonitorChangeTime = currentTime

                    // If there was a connection error but monitor changed, update error checking status
                    connectionErrorRef.child("status").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(statusSnapshot: DataSnapshot) {
                            val status = statusSnapshot.getValue(Int::class.java) ?: 0
                            if (status == 1) {
                                Log.d(TAG, "Monitor flipped but status was 1, error still active")
                                // Don't auto-reset, let user manually clear error
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {
                            Log.e(TAG, "Failed to read status: ${error.message}")
                        }
                    })

                    // Update Error_checking field
                    val timeString = dateFormat.format(Date())
                    connectionErrorRef.child("Error_checking").setValue("Monitor flipped to $currentValue at $timeString")
                } else if (currentValue != 0 && currentValue != 1) {
                    // Handle invalid values that aren't 0 or 1
                    Log.w(TAG, "Monitor node has invalid value: $currentValue (should be 0 or 1)")
                    connectionErrorRef.child("Error_checking").setValue("Invalid monitor value: $currentValue")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Monitor node listener cancelled: ${error.message}")
                setConnectionError("Monitor node listener cancelled")
            }
        })
    }

    // Check if the monitor node has flipped recently
    private fun checkMonitorNodeChange() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastChange = currentTime - lastMonitorChangeTime

        Log.d(TAG, "Checking monitor node change. Time since last flip: ${timeSinceLastChange/1000} seconds")

        if (timeSinceLastChange > connectiontimeoutms) {
            Log.w(TAG, "Monitor node hasn't flipped in ${timeSinceLastChange/1000} seconds, setting error")
            setConnectionError("Monitor node hasn't flipped in 5 minutes")
        }

        // Schedule next check
        monitorCheckHandler.postDelayed(monitorCheckRunnable, connectiontimeoutms)
    }

    // Check if connection has timed out
    private fun checkConnectionTimeout() {
        val currentTime = System.currentTimeMillis()
        if ((currentTime - lastStatusChangeTime) > connectiontimeoutms) {
            // Connection timeout detected, set error state
            setConnectionError("Connection timeout")
        }
        // Schedule the next check
        connectionTimeoutHandler.postDelayed(connectionTimeoutRunnable, connectiontimeoutms)
    }

    // Helper to set connection error state
    private fun setConnectionError(reason: String) {
        Log.w(TAG, "Setting connection error: $reason")
        connectionErrorRef.child("status").setValue(1)
        connectionErrorRef.child("timestamp").setValue(getCurrentTimestamp())
        connectionErrorRef.child("Error_checking").setValue("$reason at ${getCurrentTimestamp()}")

        // Check notification status
        connectionErrorRef.child("notified").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isNotified = snapshot.getValue(Boolean::class.java) ?: false
                if (!isNotified) {
                    notificationHelp.sendErrorNotification("Connection Error: $reason")
                    connectionErrorRef.child("notified").setValue(true)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to check notified status: ${error.message}")
            }
        })
    }

    // Special listener for connection error to monitor changes in status
    private fun setupConnectionErrorListener() {
        // Listen for any changes to the Connection_error node
        connectionErrorRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Fixed getValue calls to use proper approach
                val errorState = snapshot.child("status").getValue(Int::class.java) ?: 0
                val timestamp = snapshot.child("timestamp").getValue(String::class.java) ?: ""

                // Update last status change time whenever data changes
                lastStatusChangeTime = System.currentTimeMillis()

                // Reset the timer for connection monitoring
                connectionTimeoutHandler.removeCallbacks(connectionTimeoutRunnable)
                connectionTimeoutHandler.postDelayed(connectionTimeoutRunnable, connectiontimeoutms)

                if (errorState == 1) {
                    if (timestamp.isEmpty()) {
                        val newTimestamp = getCurrentTimestamp()
                        connectionErrorRef.child("timestamp").setValue(newTimestamp)
                        connectionTimestampText.text = newTimestamp
                    } else {
                        connectionTimestampText.text = timestamp
                    }

                    // Check notification status properly
                    snapshot.child("notified").getValue(Boolean::class.java)?.let { isNotified ->
                        if (!isNotified) {
                            notificationHelp.sendErrorNotification("Connection Error")
                            connectionErrorRef.child("notified").setValue(true)
                        }
                    } ?: connectionErrorRef.child("notified").setValue(true) // If null, set to true
                } else {
                    connectionErrorRef.child("notified").setValue(false)
                }

                // Update UI
                updateCardState(connectionErrorCard, connectionStatusText, connectionTimestampText, errorState == 1)
            }

            override fun onCancelled(error: DatabaseError) {
                // If database access is cancelled, this could also indicate a connection problem
                setConnectionError("Connection error listener cancelled")
            }
        })

        // Specific listener for the status field to detect any changes
        connectionErrorRef.child("status").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Update timestamp of last status change
                lastStatusChangeTime = System.currentTimeMillis()

                // Reset timeout checker with the new time
                connectionTimeoutHandler.removeCallbacks(connectionTimeoutRunnable)
                connectionTimeoutHandler.postDelayed(connectionTimeoutRunnable, connectiontimeoutms)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // Generalized listener setup function for each error type
    private fun setupErrorListener(
        errorRef: DatabaseReference,
        card: CardView,
        statusText: TextView,
        timestampText: TextView,
        errorType: String
    ) {
        errorRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Fixed getValue calls
                val errorState = snapshot.child("status").getValue(Int::class.java) ?: 0
                val timestamp = snapshot.child("timestamp").getValue(String::class.java) ?: ""

                if (errorState == 1) {
                    if (timestamp.isEmpty()) {
                        // If timestamp is not already set, generate a new one
                        val newTimestamp = getCurrentTimestamp()
                        errorRef.child("timestamp").setValue(newTimestamp)
                        timestampText.text = newTimestamp
                    } else {
                        timestampText.text = timestamp
                    }

                    // Check notification status safely
                    val isNotified = snapshot.child("notified").getValue(Boolean::class.java) ?: false
                    if (!isNotified) {
                        notificationHelp.sendErrorNotification(errorType)
                        errorRef.child("notified").setValue(true) // Ensure notification is sent only once
                    }
                } else {
                    // Reset notification state when error clears
                    errorRef.child("notified").setValue(false) // Reset notification trigger when error resolves
                }

                // Update card UI to reflect error status
                updateCardState(card, statusText, timestampText, errorState == 1)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // Update UI color and text based on error presence
    private fun updateCardState(card: CardView, statusText: TextView, timestampText: TextView, hasError: Boolean) {
        if (hasError) {
            card.setCardBackgroundColor(Color.RED)
            statusText.text = "ERROR"
            statusText.setTextColor(Color.WHITE)
        } else {
            card.setCardBackgroundColor(Color.GREEN)
            statusText.text = "OK"
            statusText.setTextColor(Color.BLACK)
            timestampText.text = "" // Clear timestamp on resolution
        }
    }

    // Get the current time
    private fun getCurrentTimestamp(): String {
        return dateFormat.format(Date())
    }

    // Display confirmation dialog for resolving an error
    private fun showResolveDialog(errorRef: DatabaseReference, errorType: String) {
        AlertDialog.Builder(this)
            .setTitle("Resolve $errorType")
            .setMessage("Do you want to resolve this $errorType?")
            .setPositiveButton("Resolve") { _, _ ->
                resolveError(errorRef)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Clear error status, timestamp, and notification flag in Firebase
    private fun resolveError(errorRef: DatabaseReference) {
        errorRef.child("status").setValue(0)
        errorRef.child("timestamp").removeValue()
        errorRef.child("notified").setValue(false) // Reset notification flag

        // If resolving connection error, also update Error_checking
        if (errorRef == connectionErrorRef) {
            connectionErrorRef.child("Error_checking").setValue("Error resolved at ${getCurrentTimestamp()}")
        }
    }
}

// Helper class for sending error notifications
class NotificationHelp(private val context: Context) {

    private val channelId = "error_channel"
    private val channelName = "Error Notifications"

    init {
        createNotificationChannel()
    }

    // Create a notification channel
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for error alerts."
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    // Send a notification for an error
    fun sendErrorNotification(errorType: String) {
        // Intent to open ErrorsActivity when the notification is tapped
        val intent = Intent(context, ErrorsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        // Create a pending intent for notification tap action
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Error Alert")
            .setContentText("$errorType detected!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        // Sends the Notification
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(0, notification)
    }
}