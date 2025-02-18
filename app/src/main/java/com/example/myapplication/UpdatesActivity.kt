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
        viewModel = ViewModelProvider(this).get(ScheduleViewModel::class.java)
        viewModel.getAllFeedingSchedules().observe(this, Observer { schedules ->
            updateScheduleDisplay(schedules)
            // Schedule all feedings
            schedules.forEach { schedule ->
                scheduleNextFeeding(schedule)
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.O)
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
        updatesList.addAll(schedules.map { "Feeding at: ${it.feedingTime} ${it.dayOfWeek}, ${it.foodPortion} grams" })
        updatesAdapter.notifyDataSetChanged()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateNextFeedingDisplay(schedules: List<FeedingSchedule>) {
        val nextFeeding = findNextFeeding(schedules)
        nextFeedingTextView.text = if (nextFeeding != null) {
            "Next Feeding: ${nextFeeding.feedingTime} ${nextFeeding.dayOfWeek}, ${nextFeeding.foodPortion} grams"
        } else {
            "No upcoming feedings"
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun findNextFeeding(schedules: List<FeedingSchedule>): FeedingSchedule? {
        val now = LocalTime.now()
        val today = LocalDate.now()
        val currentDayOfWeek = today.dayOfWeek

        return schedules.minByOrNull { schedule ->
            val scheduleDay = DayOfWeek.valueOf(schedule.dayOfWeek.uppercase())
            val scheduleTime = LocalTime.parse(schedule.feedingTime, DateTimeFormatter.ofPattern("h:mm a"))
            val daysUntilFeeding = (scheduleDay.value - currentDayOfWeek.value + 7) % 7
            val adjustedDay = if (daysUntilFeeding == 0 && scheduleTime.isBefore(now)) 7 else daysUntilFeeding
            adjustedDay * 24 * 60 + scheduleTime.hour * 60 + scheduleTime.minute
        }
    }

    private fun clearDisplays() {
        nextFeedingTextView.text = "No scheduled feedings"
        updatesList.clear()
        updatesAdapter.notifyDataSetChanged()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun scheduleNextFeeding(schedule: FeedingSchedule) {
        val nextFeedingDateTime = calculateNextFeedingDateTime(schedule)
        val intent = Intent(this, FeedingAlarmReceiver::class.java).apply {
            action = "com.example.myapplication.FEEDING_TIME"
            putExtra("scheduleId", schedule.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            schedule.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = nextFeedingDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )

        Log.d(TAG, "Scheduled next feeding for ${schedule.id} at $nextFeedingDateTime")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculateNextFeedingDateTime(schedule: FeedingSchedule): LocalDateTime {
        val now = LocalDateTime.now()
        val scheduleTime = LocalTime.parse(schedule.feedingTime, DateTimeFormatter.ofPattern("h:mm a"))
        val scheduleDay = DayOfWeek.valueOf(schedule.dayOfWeek.uppercase())

        var nextFeeding = now.with(scheduleTime)

        // Adjust to the next occurrence of the scheduled day
        while (nextFeeding.dayOfWeek != scheduleDay || nextFeeding.isBefore(now)) {
            nextFeeding = nextFeeding.plusDays(1)
        }

        return nextFeeding
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showFeedingUpdate(scheduleId: Int) {
        val updateMessage = "Feeding occurred for schedule ID: $scheduleId at ${getCurrentTime()}"
        updatesList.add(0, updateMessage)
        updatesAdapter.notifyDataSetChanged()
        sendNotification(scheduleId)
        refreshNextFeedingDisplay()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendNotification(scheduleId: Int) {
        notificationHelper.sendNotification(scheduleId, getCurrentTime(), getDayOfWeek())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun refreshNextFeedingDisplay() {
        viewModel.getAllFeedingSchedules().value?.let { schedules ->
            updateNextFeedingDisplay(schedules)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getCurrentTime(): String {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getDayOfWeek(): String {
        return LocalDate.now().dayOfWeek.toString().capitalize()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(feedingUpdateReceiver)
    }
}












