package com.example.myapplication

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
                    Toast.makeText(this, "Please enter a valid portion size", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please enter a portion size", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkForExistingFeeding() {
        database.orderByChild("timestamp")
            .limitToLast(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (feedingSnapshot in snapshot.children) {
                            currentFeedingKey = feedingSnapshot.key
                            break
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@ManualActivity,
                        "Error checking existing feeding: ${error.message}",
                        Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun updateFeedingInFirebase(foodPortion: Double) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val feedingData = mapOf(
            "foodPortion" to foodPortion,
            "timestamp" to timestamp
        )

        // If we have an existing key, update it; otherwise create new entry
        val databaseRef = if (currentFeedingKey != null) {
            database.child("Manual_feedings")
        } else {
            database.push()
        }

        databaseRef.setValue(feedingData)
            .addOnSuccessListener {
                Toast.makeText(this,
                    "Feeding of $foodPortion grams updated at $timestamp",
                    Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this,
                    "Failed to update feeding: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            }
    }
}



