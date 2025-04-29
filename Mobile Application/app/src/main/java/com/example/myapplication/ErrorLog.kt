package com.example.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "error_log")
data class ErrorLog(
    @PrimaryKey(autoGenerate = true) var id: Int = 0,  // Local Room ID
    var firebaseId: String = "",                      // Firebase ID
    val errorType: String,                              // Type of error message
    val errorMessage: String,                           // The error message itself
    val severity: Int,                                // Severity rating
    val timestamp: Date                                 // Timestamp for referencing later
)

