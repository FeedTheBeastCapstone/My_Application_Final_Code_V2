package com.example.myapplication

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        return try {
            // Perform the sync logic
            performSync()
            Result.success() // If successful
        } catch (e: Exception) {
            // Log the error if needed
            e.printStackTrace()
            Result.failure() // If there was an error
        }
    }

    private fun performSync() {
        // Get the instance of ScheduleDatabase
        val database = ScheduleDatabase.getInstance(applicationContext)

        // Call the method to send schedules to Firebase
        database.sendSchedulesToFirebase()
    }
}

