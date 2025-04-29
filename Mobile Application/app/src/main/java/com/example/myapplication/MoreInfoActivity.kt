package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MoreInfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_more_info)

        val dashboardButton: Button = findViewById(R.id.dashboardButton) // Dashboard button
        // Set click listener
        dashboardButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java)) // Push to Dashboard
            finish() // Close MoreInfoActivity
        }
    }
}