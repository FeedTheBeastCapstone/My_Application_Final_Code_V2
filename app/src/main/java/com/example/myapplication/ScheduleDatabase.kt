package com.example.myapplication

// Necessary imports for database handling, Firebase, and threading
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Define the Room database with FeedingSchedule as the entity
// Version is set to 2, and exportSchema is false to avoid exporting schema metadata
@Database(entities = [FeedingSchedule::class], version = 2, exportSchema = false)
abstract class ScheduleDatabase : RoomDatabase() {

    // Abstract method to get the DAO instance
    abstract fun feedingScheduleDao(): FeedingScheduleDao

    // Firebase reference to the "FeedingSchedules" node in the database
    private val firebaseDatabase: DatabaseReference = FirebaseDatabase.getInstance().getReference("FeedingSchedules")

    companion object {
        // Volatile instance ensures thread-safe singleton access
        @Volatile
        private var INSTANCE: ScheduleDatabase? = null

        // Thread pool for database operations
        val databaseWriteExecutor: ExecutorService = Executors.newFixedThreadPool(4)

        // Function to retrieve the singleton instance of the database
        fun getInstance(context: Context): ScheduleDatabase {
            // If the instance is null, synchronize and create it
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScheduleDatabase::class.java,
                    "schedule_database" // Database file name
                )
                    // Use destructive migration to reset the database when schema changes
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    // Function to send all schedules stored locally to the Firebase database
    fun sendSchedulesToFirebase() {
        // Use the thread pool to run the operation in the background
        databaseWriteExecutor.execute {
            try {
                // Get the DAO instance and retrieve all schedules
                val dao = feedingScheduleDao()
                val schedules = dao.getAllSchedules().value // Retrieve the schedules list as LiveData

                // Ensure schedules are not null before iterating over them
                schedules?.let { scheduleList ->
                    for (schedule in scheduleList) {
                        // Add each schedule to the Firebase database with its unique ID
                        firebaseDatabase.child(schedule.id.toString()).setValue(schedule)
                            .addOnSuccessListener {
                                // Optional: Handle success (e.g., logging or user feedback)
                            }
                            .addOnFailureListener { error ->
                                // Handle failure by printing the stack trace or logging
                                error.printStackTrace()
                            }
                    }
                }
            } catch (e: Exception) {
                // Handle any unexpected exceptions
                e.printStackTrace()
            }
        }
    }
}





