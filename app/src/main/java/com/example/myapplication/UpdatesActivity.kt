package com.example.myapplication

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Observer
import java.time.*
import java.time.format.DateTimeFormatter

class UpdatesActivity : AppCompatActivity() {

    private lateinit var updatesListView: ListView
    private lateinit var nextFeedingTextView: TextView
    private lateinit var updatesAdapter: ArrayAdapter<String>
    private val updatesList: MutableList<String> = mutableListOf()
    private lateinit var viewModel: ScheduleViewModel
    private lateinit var alarmManager: AlarmManager
    private lateinit var notificationHelper: NotificationHelper

    companion object {
        private const val TAG = "UpdatesActivity"
    }

    private val feedingUpdateReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.myapplication.FEEDING_TIME") {
                val scheduleId = intent.getIntExtra("scheduleId", -1)
                showFeedingUpdate(scheduleId)
                // Schedule the next occurrence
                viewModel.getAllFeedingSchedules().value?.let { schedules ->
                    schedules.find { it.id.toInt() == scheduleId }?.let { schedule ->
                        scheduleNextFeeding(schedule)
                    }
                }
            }

        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_updates)

        val dashboardButton: Button = findViewById(R.id.dashboardButton) // Dashboard Button
        dashboardButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java) // Push to Dashboard
            startActivity(intent)
            finish() // Close ScheduleActivity
        }

        // Initialize components
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        notificationHelper = NotificationHelper(this)

        // Check and request exact alarm permission for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Intent().also { intent ->
                    intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    startActivity(intent)
                }
            }
        }

        initializeUI()
        registerReceiver()
        setupViewModel()
    }

    // Initialize UI
    private fun initializeUI() {
        updatesListView = findViewById(R.id.updatesListView)
        nextFeedingTextView = findViewById(R.id.nextFeedingTextView)
        updatesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, updatesList)
        updatesListView.adapter = updatesAdapter
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun registerReceiver() {
        val filter = IntentFilter("com.example.myapplication.FEEDING_TIME")
        registerReceiver(feedingUpdateReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[ScheduleViewModel::class.java]
        viewModel.getAllFeedingSchedules().observe(this, Observer { schedules ->
            updateScheduleDisplay(schedules)
            // Schedule all feedings
            schedules.forEach { schedule ->
                scheduleNextFeeding(schedule)
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.O) // Update the list
    private fun updateScheduleDisplay(schedules: List<FeedingSchedule>) {
        if (schedules.isNotEmpty()) {
            displayAllFeedings(schedules)
            updateNextFeedingDisplay(schedules)
        } else {
            clearDisplays()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun displayAllFeedings(schedules: List<FeedingSchedule>) {
        updatesList.clear()

        // Sort schedules properly by time and day
        val sortedSchedules = schedules.sortedWith(compareBy<FeedingSchedule> {
            // First, sort by day of week
            val dayMap = mapOf(
                "Monday" to 1,
                "Tuesday" to 2,
                "Wednesday" to 3,
                "Thursday" to 4,
                "Friday" to 5,
                "Saturday" to 6,
                "Sunday" to 7
            )
            dayMap[it.dayOfWeek] ?: 0
        }.thenBy {
            // Then sort by time (in minutes since midnight)
            val timeParts = it.feedingTime.split(":|\\s+".toRegex())
            var hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()
            val amPm = timeParts[2]

            // Convert to minutes since midnight for sorting
            if (amPm == "AM") {
                hour = if (hour == 12) 0 else hour
            } else { // PM
                hour = if (hour == 12) 12 else hour + 12
            }

            hour * 60 + minute
        })

        updatesList.addAll(sortedSchedules.map { "${it.feedingTime} ${it.dayOfWeek}, ${it.foodPortion} grams" })
        updatesAdapter.notifyDataSetChanged()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateNextFeedingDisplay(schedules: List<FeedingSchedule>) { // Top feeding is swapped based on time
        val nextFeeding = findNextFeeding(schedules)
        nextFeedingTextView.text = if (nextFeeding != null) {
            "Next Feeding: ${nextFeeding.feedingTime} ${nextFeeding.dayOfWeek}, ${nextFeeding.foodPortion} grams"
        } else {
            "No upcoming feedings"
        }
    }

    @RequiresApi(Build.VERSION_CODES.O) // List check for next header
    private fun findNextFeeding(schedules: List<FeedingSchedule>): FeedingSchedule? {
        val now = LocalTime.now()
        val today = LocalDate.now()
        val currentDayOfWeek = today.dayOfWeek

        return schedules.minByOrNull { schedule -> // Resets schedule based on day of the week
            val scheduleDay = DayOfWeek.valueOf(schedule.dayOfWeek.uppercase())

            // Parse the feeding time properly handling AM/PM
            val timeParts = schedule.feedingTime.split(":|\\s+".toRegex())
            var hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()
            val amPm = timeParts[2]

            // Convert to 24-hour time for proper comparison
            if (amPm == "AM") {
                hour = if (hour == 12) 0 else hour
            } else { // PM
                hour = if (hour == 12) 12 else hour + 12
            }

            val scheduleTime = LocalTime.of(hour, minute)

            val daysUntilFeeding = (scheduleDay.value - currentDayOfWeek.value + 7) % 7
            val adjustedDay = if (daysUntilFeeding == 0 && scheduleTime.isBefore(now)) 7 else daysUntilFeeding
            adjustedDay * 24 * 60 + scheduleTime.hour * 60 + scheduleTime.minute
        }
    }

    private fun clearDisplays() { // Clears the next feeding text and update list display
        nextFeedingTextView.text = "No scheduled feedings"
        updatesList.clear()
        updatesAdapter.notifyDataSetChanged()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun scheduleNextFeeding(schedule: FeedingSchedule) { // Calculate the next feeding time based on the schedule
        val nextFeedingDateTime = calculateNextFeedingDateTime(schedule)
        val intent = Intent(this, FeedingAlarmReceiver::class.java).apply { // Prepare an intent for the alarm broadcast
            action = "com.example.myapplication.FEEDING_TIME"
            putExtra("scheduleId", schedule.id)
        }

        val pendingIntent = PendingIntent.getBroadcast( // Create a PendingIntent with the schedule ID
            this,
            schedule.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Convert next feeding time to epoch milliseconds
        val triggerTime = nextFeedingDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        alarmManager.setExactAndAllowWhileIdle(  // Schedule the alarm using AlarmManager
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )

        Log.d(TAG, "Scheduled next feeding for ${schedule.id} at $nextFeedingDateTime")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculateNextFeedingDateTime(schedule: FeedingSchedule): LocalDateTime {
        val now = LocalDateTime.now()

        // Parse the time with proper AM/PM handling
        val timeParts = schedule.feedingTime.split(":|\\s+".toRegex())
        var hour = timeParts[0].toInt()
        val minute = timeParts[1].toInt()
        val amPm = timeParts[2]

        // Convert to 24-hour format
        if (amPm == "AM") {
            hour = if (hour == 12) 0 else hour
        } else { // PM
            hour = if (hour == 12) 12 else hour + 12
        }

        val scheduleTime = LocalTime.of(hour, minute)
        val scheduleDay = DayOfWeek.valueOf(schedule.dayOfWeek.uppercase()) // Get the scheduled day of the week as an enum

        var nextFeeding = now.with(scheduleTime) // Combine current date with the scheduled feeding time

        // Loop forward until the date is the right day of the week and not in the past
        while (nextFeeding.dayOfWeek != scheduleDay || nextFeeding.isBefore(now)) {
            nextFeeding = nextFeeding.plusDays(1)
        }

        return nextFeeding
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showFeedingUpdate(scheduleId: Int) {
        val updateMessage = "Feeding occurred for schedule ID: $scheduleId at ${getCurrentTime()}"
        updatesList.add(0, updateMessage) // Build update message with timestamp
        updatesAdapter.notifyDataSetChanged() // Insert the update at the top of the list
        sendNotification(scheduleId) // Send a system notification
        refreshNextFeedingDisplay()     // Refresh the display to show the next upcoming feeding
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendNotification(scheduleId: Int) {
        // Send feeding notification with time and day (this may not work)
        notificationHelper.sendNotification(scheduleId, getCurrentTime(), getDayOfWeek())
    }

    @RequiresApi(Build.VERSION_CODES.O) // Get all feeding schedules and update display
    private fun refreshNextFeedingDisplay() {
        viewModel.getAllFeedingSchedules().value?.let { schedules ->
            updateNextFeedingDisplay(schedules)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O) // Return the current time formatted as HH:mm (24-hour)
    private fun getCurrentTime(): String {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    @RequiresApi(Build.VERSION_CODES.O)  // Return current day of the week
    private fun getDayOfWeek(): String {
        return LocalDate.now().dayOfWeek.toString().capitalize() // Capitalize was causing problems
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(feedingUpdateReceiver) // Unregister the feeding update broadcast receiver
    }
}