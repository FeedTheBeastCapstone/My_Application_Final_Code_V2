package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BatteryActivity : AppCompatActivity() {

    // UI components for battery level
    private lateinit var batteryProgressBar: ProgressBar
    private lateinit var batteryLevelText: TextView
    private lateinit var refreshButton: Button

    // Firebase reference to the "battery_level" node in the database
    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private val databaseRef: DatabaseReference = firebaseDatabase.reference.child("battery_level")

    // Variable to hold the current battery level (initialized to 75 for testing)
    private var currentBatteryLevel: Int = 75

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battery)

        // Linking UI elements from the layout
        batteryProgressBar = findViewById(R.id.batteryProgressBar)
        batteryLevelText = findViewById(R.id.batteryLevelText)
        refreshButton = findViewById(R.id.refreshBatteryButton)

        // Initialize the UI with the current battery level
        updateBatteryLevel(currentBatteryLevel)

        // Set a click listener for the refresh button
        refreshButton.setOnClickListener {
            // Simulate a battery level change for testing purposes
            currentBatteryLevel = (currentBatteryLevel + 5) % 101 // Increment by 5, loop back to 0 after 100
            updateBatteryLevel(currentBatteryLevel) // Update the UI with the new battery level
            sendBatteryLevelToFirebase(currentBatteryLevel) // Send the new battery level to Firebase
        }
    }

    // Function to update the ProgressBar and TextView with the battery level
    private fun updateBatteryLevel(level: Int) {
        if (level in 0..100) {
            // Update the ProgressBar value
            batteryProgressBar.progress = level
            // Update the TextView to display the battery level as a percentage
            batteryLevelText.text = "Battery Level: $level%"
        } else {
            // Log an error if an invalid battery level is received
            Log.e("BatteryActivity", "Invalid battery level received: $level")
        }
    }

    // Function to send the current battery level to Firebase
    private fun sendBatteryLevelToFirebase(level: Int) {
        // Get the current timestamp to include in the Firebase update
        val timestamp = getCurrentTimestamp()
        val statusUpdate = mapOf(
            "level" to level,         // Battery level
            "lastChecked" to timestamp // Timestamp of the update
        )

        // Update Firebase with the battery level and timestamp
        databaseRef.setValue(statusUpdate)
            .addOnSuccessListener {
                // Log success message if the update is successful
                Log.d("BatteryActivity", "Successfully updated battery level in Firebase")
            }
            .addOnFailureListener { error ->
                // Log an error message if the update fails
                Log.e("BatteryActivity", "Failed to update battery level: ${error.message}")
            }
    }

    // Function to get the current timestamp in the format "yyyy-MM-dd HH:mm:ss"
    private fun getCurrentTimestamp(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date()) // Return the formatted current date and time
    }
}





