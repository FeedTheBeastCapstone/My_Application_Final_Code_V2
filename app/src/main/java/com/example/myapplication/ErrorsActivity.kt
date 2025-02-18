package com.example.myapplication

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ValueEventListener
import java.util.*

class ErrorsActivity : AppCompatActivity() {

    // ViewModel to manage error logs in the app
    private val errorViewModel: ErrorViewModel by viewModels()

    // UI elements for displaying and interacting with error logs
    private lateinit var errorListView: ListView
    private lateinit var errorAdapter: ArrayAdapter<String>

    // Maps ListView positions to corresponding error logs for easy access
    private val errorLogMap = mutableMapOf<Int, ErrorLog>()

    // Firebase database references
    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private val databaseRef: DatabaseReference = firebaseDatabase.reference.child("errors") // Error logs location
    private val connectionRef: DatabaseReference = firebaseDatabase.reference.child(".info/connected") // Connection status
    private val errorCheckingRef: DatabaseReference = firebaseDatabase.reference.child("Error_checking") // Connection log

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_errors)

        // Set up a crash logger to handle uncaught exceptions
        setupCrashLogger()

        // Initialize the ListView and its adapter for displaying error logs
        errorListView = findViewById(R.id.errorListView)
        errorAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        errorListView.adapter = errorAdapter

        // Observe changes in the ViewModel's error logs and update the UI
        errorViewModel.allErrors.observe(this) { errors ->
            errorAdapter.clear()
            errorLogMap.clear()

            // Sort errors by timestamp in descending order (most recent first)
            val sortedErrors = errors.sortedByDescending { it.timestamp }
            sortedErrors.forEachIndexed { index, errorLog ->
                errorLogMap[index] = errorLog // Map the ListView position to the error log
            }

            // Add formatted error details to the adapter for display
            errorAdapter.addAll(sortedErrors.map {
                "${it.timestamp}: ${it.errorType} - ${it.errorMessage} (Severity: ${it.severity})"
            })
        }

        // Enable tap-to-delete functionality for error logs
        setupTapToDelete()

        // Sync errors from Firebase and monitor connection status
        observeErrorsFromFirebase()
        monitorFirebaseConnection()
    }

    // Sets up functionality to delete an error log when tapped
    private fun setupTapToDelete() {
        errorListView.setOnItemClickListener { _, _, position, _ ->
            val selectedError = errorLogMap[position]
            if (selectedError != null) {
                // Show a confirmation dialog for deletion
                AlertDialog.Builder(this)
                    .setTitle("Delete Error Log")
                    .setMessage("Are you sure you want to delete this error log?")
                    .setPositiveButton("Delete") { _, _ ->
                        // Delete the error log from Firebase and ViewModel
                        deleteError(selectedError.id.toString())
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    // Deletes an error log from Firebase and updates the ViewModel
    private fun deleteError(errorId: String) {
        databaseRef.child(errorId).removeValue().addOnSuccessListener {
            // Notify ViewModel of the successful deletion
            errorViewModel.removeErrorById(errorId.toInt())
            Toast.makeText(this, "Error log deleted.", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { exception ->
            // Log the failure to delete the error log
            errorViewModel.logError(
                errorType = "Error Deletion",
                errorMessage = "Failed to delete error: ${exception.message}",
                severity = 2
            )
        }
    }

    // Observes error logs stored in Firebase and syncs them with the ViewModel
    private fun observeErrorsFromFirebase() {
        databaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val errors = mutableListOf<ErrorLog>()
                for (errorSnapshot in snapshot.children) {
                    val error = errorSnapshot.getValue(ErrorLog::class.java)
                    if (error != null) {
                        // Assign Firebase key as the error ID (convert safely to Int)
                        error.id = errorSnapshot.key?.toIntOrNull() ?: 0
                        errors.add(error)
                    }
                }
                // Update the ViewModel with the fetched errors
                syncErrorsToViewModel(errors)
            }

            override fun onCancelled(error: DatabaseError) {
                // Log Firebase read failure
                errorViewModel.logError(
                    errorType = "Firebase Error",
                    errorMessage = "Failed to read from Firebase: ${error.message}",
                    severity = 3
                )
            }
        })
    }

    // Monitors the Firebase connection and logs connectivity changes
    private fun monitorFirebaseConnection() {
        connectionRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    // Log successful connection
                    errorCheckingRef.setValue("Connected at ${Date()}")
                } else {
                    // Log disconnection as an error
                    errorViewModel.logError(
                        errorType = "Firebase Disconnection",
                        errorMessage = "App lost connection to Firebase.",
                        severity = 2
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Log failure to monitor connection
                errorViewModel.logError(
                    errorType = "Connection Monitoring Error",
                    errorMessage = "Failed to monitor Firebase connection: ${error.message}",
                    severity = 3
                )
            }
        })
    }

    // Sets up a crash logger to handle uncaught exceptions
    private fun setupCrashLogger() {
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            // Log the crash details in the ViewModel
            errorViewModel.logError(
                errorType = "App Crash",
                errorMessage = "Forced app termination: ${exception.localizedMessage}",
                severity = 1
            )
            exception.printStackTrace() // Print the stack trace for debugging
            // Forward the exception to the default handler
            Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(thread, exception)
        }
    }

    // Syncs a list of error logs with the ViewModel
    private fun syncErrorsToViewModel(errors: List<ErrorLog>) {
        errors.forEach { error ->
            errorViewModel.logError(
                errorType = error.errorType,
                errorMessage = error.errorMessage,
                severity = error.severity
            )
        }
    }
}






