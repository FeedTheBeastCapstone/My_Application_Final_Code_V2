package com.example.myapplication

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// Represents a feeding schedule in the database
@Entity(tableName = "feeding_schedule") // Sets the table name to "feeding_schedule"
data class FeedingSchedule(
    // Primary key, automatically generated
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0, // Default value is 0

    // Day of the week for the feeding
    @ColumnInfo(name = "day_of_week")
    var dayOfWeek: String = "", // Defaults to an empty string

    // Feeding time
    @ColumnInfo(name = "feeding_time")
    var feedingTime: String = "", // Defaults to an empty string

    // Food portion in grams
    @ColumnInfo(name = "food_portion")
    var foodPortion: Double = 0.0 // Defaults to 0.0
) {
    // Converts the feeding schedule to a readable string
    override fun toString(): String {
        return "Feeding Time: $feedingTime, Day: $dayOfWeek, Portion: $foodPortion grams"
    }
}

