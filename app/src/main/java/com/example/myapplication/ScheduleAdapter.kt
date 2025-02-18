package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


// Adapter class for displaying a schedule in a RecyclerView
class ScheduleAdapter(
    // List of FeedingSchedule items
    private val scheduleList: List<FeedingSchedule>
) : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

    // Helper function to convert day of week integers to their string representations
    private fun getDayOfWeekString(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            7 -> "Sunday"
            else -> "Unknown"
        }
    }

    // Called when the RecyclerView needs a new ViewHolder of the given type to represent an item
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Inflate the layout for a single item in the RecyclerView
        val view =
            LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view) // Return a new ViewHolder instance
    }

    data class FeedingSchedule(
        val dayOfWeek: Int,  // Integer representing the day of the week (1 for Monday, etc.)
        val feedingTime: String  // String representing the time (e.g., "1:00 PM")
    )

    // Called by the RecyclerView to display the data at the specified position
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val schedule = scheduleList[position] // Get the schedule item for the current position
        val dayOfWeekString = getDayOfWeekString(schedule.dayOfWeek) // Get the day of the week as a string
        val formattedText = "Feeding at ${schedule.feedingTime} on $dayOfWeekString" // Format the schedule string
        holder.scheduleTextView.text = formattedText // Set the formatted text in the TextView
    }

    // Returns the total number of items in the data set held by the adapter
    override fun getItemCount(): Int {
        return scheduleList.size // Return the size of the schedule list
    }

    // ViewHolder class that holds the views for each item in the RecyclerView
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var scheduleTextView: TextView = itemView.findViewById(android.R.id.text1) // TextView for displaying the schedule item
    }
}


