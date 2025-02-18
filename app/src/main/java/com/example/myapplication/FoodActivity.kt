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

// Activity to display and update the food level for the automatic feeder
class FoodActivity : AppCompatActivity() {

    // UI components for displaying food level and refreshing data
    private lateinit var foodProgressBar: ProgressBar
    private lateinit var foodLevelText: TextView
    private lateinit var refreshButton: Button

    // Firebase database reference to store and update the food level
    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private val databaseRef: DatabaseReference = firebaseDatabase.reference.child("food_level")

    // Variable to simulate the current food level (initially set to 50%)
    private var currentFoodLevel: Int = 50

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_food)

        // Initialize UI components
        foodProgressBar = findViewById(R.id.foodProgressBar)
        foodLevelText = findViewById(R.id.foodLevelText)
        refreshButton = findViewById(R.id.refreshFoodButton)

        // Set the initial food level display
        updateFoodLevel(currentFoodLevel)

        // Handle refresh button click to simulate and update food level
        refreshButton.setOnClickListener {
            currentFoodLevel = (currentFoodLevel + 10) % 101 // Simulate food level changes (0-100%)
            updateFoodLevel(currentFoodLevel)  // Update the UI
            sendFoodLevelToFirebase(currentFoodLevel) // Send the updated level to Firebase
        }
    }

    // Update the ProgressBar and TextView with the current food level
    private fun updateFoodLevel(level: Int) {
        if (level in 0..100) {
            foodProgressBar.progress = level // Update ProgressBar with new level
            foodLevelText.text = "Food Level: $level%" // Update TextView with new level
        } else {
            Log.e("FoodActivity", "Invalid food level received: $level") // Log an error for invalid levels
        }
    }

    // Send the current food level and timestamp to Firebase
    private fun sendFoodLevelToFirebase(level: Int) {
        val timestamp = getCurrentTimestamp() // Get the current time as a formatted string
        val statusUpdate = mapOf(
            "level" to level, // Food level percentage
            "lastChecked" to timestamp // Time of update
        )

        // Update Firebase with the food level and timestamp
        databaseRef.setValue(statusUpdate)
            .addOnSuccessListener {
                Log.d("FoodActivity", "Successfully updated food level in Firebase")
            }
            .addOnFailureListener { error ->
                Log.e("FoodActivity", "Failed to update food level: ${error.message}") // Log errors
            }
    }

    // Get the current timestamp in a readable format for logging and Firebase updates
    private fun getCurrentTimestamp(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date()) // Return the current date and time as a string
    }
}





