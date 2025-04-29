package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class ManualActivity : AppCompatActivity() {

    private lateinit var feedButton: Button
    private lateinit var portionEditText: EditText
    private lateinit var database: DatabaseReference
    private var foodPortion: Double = 0.0
    private var currentFeedingKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual)

        // Initialize views
        feedButton = findViewById(R.id.feedButton)
        portionEditText = findViewById(R.id.portionEditText)

        // Initialize Firebase reference
        database = FirebaseDatabase.getInstance().getReference("ManualFeedings")

        // Check for existing manual feeding entry
        checkForExistingFeeding()

        // Check if food portion is passed from the schedule and pre-fill it
        foodPortion = intent.getDoubleExtra("FOOD_PORTION", 0.0)
        if (foodPortion >= 1.0) {
            portionEditText.setText(foodPortion.toString())
        }

        // Set button click listener
        feedButton.setOnClickListener {
            val portionText = portionEditText.text.toString()

            // Validate user input
            if (portionText.isNotEmpty()) {
                foodPortion = portionText.toDoubleOrNull() ?: 0.0
                if (foodPortion >= 1.0 && foodPortion < 100.1) {
                    updateFeedingInFirebase(foodPortion)
                } else {
                    Toast.makeText(this, "Please enter a valid portion size", Toast.LENGTH_SHORT).show() // Incorrect input
                }
            } else {
                Toast.makeText(this, "Please enter a portion size", Toast.LENGTH_SHORT).show() // First entering an input
            }
        }

        val dashboardButton: Button = findViewById(R.id.dashboardButton)
        // Set click listener
        dashboardButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java)) // Push to Dashboard
            finish() // Close ErrorsActivity
        }
    }

    private fun checkForExistingFeeding() {
        database.orderByChild("timestamp") // Check firebase for node
            .limitToLast(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (feedingSnapshot in snapshot.children) { // Replace the node value
                            currentFeedingKey = feedingSnapshot.key
                            break
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@ManualActivity,
                        "Error checking existing feeding: ${error.message}", // Possible error case
                        Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun updateFeedingInFirebase(foodPortion: Double) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // Reference to the existing "Manual_feedings" node inside "ManualFeedings"
        val feedingEntryRef = database.child("Manual_feedings")

        // Update the existing node with new values
        val updateData = mapOf(
            "foodPortion" to foodPortion,
            "timestamp" to timestamp,
            "status" to true // Update status to true
        )

        feedingEntryRef.updateChildren(updateData) // Update based on the listener
            .addOnSuccessListener {
                Toast.makeText(this,
                    "Feeding updated: $foodPortion grams at $timestamp",
                    Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this,
                    "Failed to update feeding: ${e.message}", // Possible error case
                    Toast.LENGTH_SHORT).show()
            }
    }
}



