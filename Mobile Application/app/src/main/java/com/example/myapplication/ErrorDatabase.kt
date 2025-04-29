package com.example.myapplication

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.Date

// Converter class to handle the transformation between Date and Long for Room database
class DateConverters {
    // Converts a Long timestamp to a Date object
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    // Converts a Date object to a Long timestamp
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

// Defines the Room database for storing error logs
@Database(entities = [ErrorLog::class], version = 3, exportSchema = false) // Database version is updated to 3
@TypeConverters(DateConverters::class) // Includes the DateConverters for Date/Long conversions
abstract class ErrorDatabase : RoomDatabase() {

    // Provides access to the DAO for interacting with the database
    abstract fun errorLogDao(): ErrorLogDao

    companion object {
        // Singleton instance of the database to ensure only one instance is created
        @Volatile
        private var INSTANCE: ErrorDatabase? = null

        // Method to get the singleton instance of the database
        fun getInstance(context: Context): ErrorDatabase {
            // Return the existing instance if it exists; otherwise, create a new one
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ErrorDatabase::class.java, // Specify the database class
                    "error_database" // Name of the database file
                )
                    .fallbackToDestructiveMigration() // Drops existing data and recreates the database if the version is incremented
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}




