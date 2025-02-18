package com.example.myapplication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData

// ViewModel class for managing and storing UI-related data for the Feeding Schedule
class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    private val feedingScheduleDao: FeedingScheduleDao // DAO for interacting with the FeedingSchedule table
    private val database = ScheduleDatabase.getInstance(application) // Database instance

    init {
        feedingScheduleDao = database.feedingScheduleDao() // Get the FeedingSchedule DAO
    }

    // Method to retrieve feeding schedules for a specific day, returns LiveData for UI observation
    fun getFeedingSchedulesForDay(day: String?): LiveData<List<FeedingSchedule>> {
        return feedingScheduleDao.getFeedingSchedulesForDay(day) // Query the DAO for schedules
    }

    // Method to insert a new feeding schedule into the database
    fun insert(schedule: FeedingSchedule) {
        ScheduleDatabase.databaseWriteExecutor.execute { feedingScheduleDao.insert(schedule) } // Execute insert on a background thread
    }

    // Method to update an existing feeding schedule in the database
    fun update(schedule: FeedingSchedule) {
        ScheduleDatabase.databaseWriteExecutor.execute { feedingScheduleDao.update(schedule) } // Execute update on a background thread
    }

    // Method to delete a feeding schedule from the database
    fun delete(schedule: FeedingSchedule) {
        ScheduleDatabase.databaseWriteExecutor.execute { feedingScheduleDao.delete(schedule) } // Execute delete on a background thread
    }

    // Method to get all feeding schedules from the database
    fun getAllFeedingSchedules(): LiveData<List<FeedingSchedule>> {
        return feedingScheduleDao.getAllSchedules() // Query the Dao for schedules
    }
}
