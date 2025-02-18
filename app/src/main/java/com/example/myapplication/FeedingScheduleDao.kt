package com.example.myapplication

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

// DAO for managing feeding schedules in the database
@Dao
interface FeedingScheduleDao {

    // Get all feeding schedules for a specific day, sorted by time
    @Query("SELECT * FROM feeding_schedule WHERE day_of_week = :day ORDER BY feeding_time ASC")
    fun getFeedingSchedulesForDay(day: String?): LiveData<List<FeedingSchedule>>

    // Add a new feeding schedule to the database
    @Insert
    fun insert(schedule: FeedingSchedule)

    // Update an existing feeding schedule
    @Update
    fun update(schedule: FeedingSchedule)

    // Delete a specific feeding schedule
    @Delete
    fun delete(schedule: FeedingSchedule)

    // Get all feeding schedules, ordered by day of the week and then by feeding time
    @Query("""
        SELECT * FROM feeding_schedule 
        ORDER BY 
            CASE 
                WHEN day_of_week = 'Monday' THEN 1
                WHEN day_of_week = 'Tuesday' THEN 2
                WHEN day_of_week = 'Wednesday' THEN 3
                WHEN day_of_week = 'Thursday' THEN 4
                WHEN day_of_week = 'Friday' THEN 5
                WHEN day_of_week = 'Saturday' THEN 6
                WHEN day_of_week = 'Sunday' THEN 7
            END, 
            feeding_time
    """)
    fun getAllSchedules(): LiveData<List<FeedingSchedule>>
}


