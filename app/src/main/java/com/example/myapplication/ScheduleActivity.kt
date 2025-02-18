package com.example.myapplication

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.google.firebase.database.FirebaseDatabase
import java.util.*

class ScheduleActivity : AppCompatActivity() {
    private lateinit var viewModel: ScheduleViewModel
    private lateinit var dayOfWeekSpinner: Spinner
    private lateinit var addTimeButton: Button
    private lateinit var feedingTimesListView: ListView
    private var selectedDay = "Monday"

    // Firebase database reference for feeding schedules
    private val database = FirebaseDatabase.getInstance()
    private val firebaseRef = database.getReference("feedingSchedules")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)

        // Initialize UI elements
        dayOfWeekSpinner = findViewById(R.id.dayOfWeekSpinner)
        addTimeButton = findViewById(R.id.addTimeButton)
        feedingTimesListView = findViewById(R.id.feedingTimesListView)

        // Setup ViewModel
        viewModel = ViewModelProvider(this)[ScheduleViewModel::class.java]

        // Set up spinner options for days of the week
        val spinnerAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.days_of_week, android.R.layout.simple_spinner_item
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dayOfWeekSpinner.adapter = spinnerAdapter

        // Load feeding times for the selected day
        dayOfWeekSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                selectedDay = parent.getItemAtPosition(position).toString()
                loadFeedingTimesForDay(selectedDay)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Add feeding time button click listener
        addTimeButton.setOnClickListener {
            showTimeAndPortionDialog()
        }

        // Set up list item click listener for edit/delete options
        feedingTimesListView.setOnItemClickListener { parent, _, position, _ ->
            val selectedSchedule = parent.getItemAtPosition(position) as FeedingSchedule
            showModifyOrDeleteDialog(selectedSchedule)
        }

        // Observe changes in feeding schedules and sync with Firebase
        observeScheduleChanges()
    }

    private fun observeScheduleChanges() {
        viewModel.getAllFeedingSchedules().observe(this) { schedules ->
            syncSchedulesToFirebase(schedules)
        }
    }

    private fun syncSchedulesToFirebase(schedules: List<FeedingSchedule>) {
        firebaseRef.setValue(schedules).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Schedules synced to Firebase", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Failed to sync schedules: ${task.exception?.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadFeedingTimesForDay(day: String) {
        viewModel.getFeedingSchedulesForDay(day).observe(this) { feedingSchedules ->
            val nonNullFeedingSchedules = feedingSchedules ?: emptyList()
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                nonNullFeedingSchedules
            )
            feedingTimesListView.adapter = adapter
        }
    }

    private fun showTimeAndPortionDialog() {
        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val feedingTime = formatTime(hourOfDay, minute)
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }
                val triggerAtMillis = calendar.timeInMillis
                showPortionInputDialog(feedingTime, selectedDay, triggerAtMillis)
            }, 12, 0, false
        )
        timePickerDialog.show()
    }

    private fun showPortionInputDialog(feedingTime: String, selectedDay: String, triggerAtMillis: Long) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Enter Portion (100 grams max)")

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        builder.setView(input)

        builder.setPositiveButton("OK") { _, _ ->
            val portionText = input.text.toString().trim()
            val foodPortion = portionText.toDoubleOrNull()

            if (foodPortion != null && foodPortion >= 1.0 && foodPortion < 100.1) {
                val schedule = FeedingSchedule(dayOfWeek = selectedDay, feedingTime = feedingTime, foodPortion = foodPortion)
                viewModel.insert(schedule)
                scheduleFeedingAlarm(this, triggerAtMillis, schedule.id)
                Toast.makeText(this, "Feeding schedule added", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Invalid portion, please enter a valid number", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun showModifyOrDeleteDialog(schedule: FeedingSchedule) {
        AlertDialog.Builder(this)
            .setTitle("Modify Feeding Schedule")
            .setMessage("What would you like to do?")
            .setPositiveButton("Edit") { _, _ -> showEditTimeAndPortionDialog(schedule) }
            .setNegativeButton("Delete") { _, _ ->
                viewModel.delete(schedule)
                cancelFeedingAlarm(schedule.id)
                Toast.makeText(this, "Feeding schedule deleted", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun cancelFeedingAlarm(scheduleId: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, FeedingAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            scheduleId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun showEditTimeAndPortionDialog(schedule: FeedingSchedule) {
        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val newTime = formatTime(hourOfDay, minute)
                schedule.feedingTime = newTime
                showEditPortionInputDialog(schedule)
            }, 12, 0, false
        )
        timePickerDialog.show()
    }

    private fun showEditPortionInputDialog(schedule: FeedingSchedule) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Edit Food Portion (grams)")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setText(schedule.foodPortion.toString())
        builder.setView(input)

        builder.setPositiveButton("OK") { _, _ ->
            val newPortion = input.text.toString().toDoubleOrNull()
            if (newPortion != null && newPortion >= 0) {
                schedule.foodPortion = newPortion
                viewModel.update(schedule)
                rescheduleFeedingAlarm(schedule)
                Toast.makeText(this, "Feeding schedule updated", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Invalid portion, please enter a valid number", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun rescheduleFeedingAlarm(schedule: FeedingSchedule) {
        val calendar = Calendar.getInstance().apply {
            // Parse the time
            val timeParts = schedule.feedingTime.split(":|\\s+".toRegex()).toTypedArray()
            val hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()
            val isPm = timeParts[2] == "PM"

            // Set the correct hour
            set(Calendar.HOUR_OF_DAY, if (isPm && hour != 12) hour + 12 else if (!isPm && hour == 12) 0 else hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            // Adjust to the correct day of the week
            val dayMap = mapOf(
                "Sunday" to Calendar.SUNDAY,
                "Monday" to Calendar.MONDAY,
                "Tuesday" to Calendar.TUESDAY,
                "Wednesday" to Calendar.WEDNESDAY,
                "Thursday" to Calendar.THURSDAY,
                "Friday" to Calendar.FRIDAY,
                "Saturday" to Calendar.SATURDAY
            )

            val targetDay = dayMap[schedule.dayOfWeek] ?: Calendar.MONDAY

            // If the current time is past the scheduled time, move to the next occurrence
            while (timeInMillis <= System.currentTimeMillis() || get(Calendar.DAY_OF_WEEK) != targetDay) {
                add(Calendar.DAY_OF_WEEK, 1)
            }
        }

        scheduleFeedingAlarm(this, calendar.timeInMillis, schedule.id)
    }

    private fun scheduleFeedingAlarm(context: Context, triggerAtMillis: Long, scheduleId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, FeedingAlarmReceiver::class.java)
        intent.putExtra("scheduleId", scheduleId)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduleId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactAlarm(context, alarmManager, triggerAtMillis, pendingIntent)
    }

    private fun scheduleExactAlarm(context: Context, alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        // Check if the alarm time is in the future
        val currentTime = System.currentTimeMillis()

        // If the trigger time is in the past, adjust to the next occurrence
        val adjustedTriggerTime = if (triggerAtMillis <= currentTime) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = triggerAtMillis
                // Add 7 days to move to the next week
                add(Calendar.DAY_OF_WEEK, 7)
            }
            calendar.timeInMillis
        } else {
            triggerAtMillis
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            context.startActivity(intent)
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, adjustedTriggerTime, pendingIntent)
    }
    private fun formatTime(hourOfDay: Int, minute: Int): String {
        val hour = if (hourOfDay % 12 == 0) 12 else hourOfDay % 12
        val amPm = if (hourOfDay < 12) "AM" else "PM"
        return String.format("%d:%02d %s", hour, minute, amPm)
    }
}















