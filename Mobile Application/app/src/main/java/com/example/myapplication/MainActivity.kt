package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.NotificationCompat
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    // Firebase Authentication
    private lateinit var auth: FirebaseAuth

    // UI components
    private lateinit var dashboardContainer: ScrollView
    private lateinit var listViewSection: LinearLayout
    private lateinit var listView: ListView
    private lateinit var listViewBackButton: Button
    private lateinit var logoutButton: Button
    private lateinit var moreInfoButton: Button
//    private lateinit var redirectToListViewButton: Button
    private lateinit var manualFeedButton: Button
    private lateinit var scheduleFeedButton: Button
    private lateinit var errorSectionButton: Button
    private lateinit var viewSchedulesButton: Button
    private lateinit var batteryLevelText: TextView
    private lateinit var foodLevelText: TextView
    private lateinit var toolbar: Toolbar


    // New progress bar components
    private lateinit var foodLevelProgress: ProgressBar
    private lateinit var batteryLevelProgress: ProgressBar

    // Notification variables
    private lateinit var notificationManager: NotificationManager
    private val channelID = "pet_feeder_channel"
    private val batterynotificationID = 1001
    private val foodnotificationID = 1002

    // Track notification thresholds to prevent repeated notifications
    private var lastBatteryThreshold = 100
    private var lastFoodThreshold = 100

    // Add these variables to track which thresholds have already been notified
    private var notifiedBatteryThresholds = mutableSetOf<Int>()
    private var notifiedFoodThresholds = mutableSetOf<Int>()
    // Add a flag to skip initial notifications
    private var isInitialDataLoad = true

    // Constants
    private val sessionTimeoutMillis = 10 * 60 * 1000 // 10 minutes
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serviceIntent = Intent(this, ErrorMonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Background operation to monitor Firebase
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Initialize Firebase Authentication first
        auth = FirebaseAuth.getInstance()

        // Check authentication status before setting content view
        // This ensures the user is properly authenticated when opening the app fresh
        if (auth.currentUser == null  /*|| isSessionExpired() */) {
            redirectToLogin()
            return // Stop further execution of onCreate
        }

        setContentView(R.layout.activity_main)

        // Setup Toolbar
        toolbar = findViewById(R.id.ToolbarMain)
        setSupportActionBar(toolbar)

        // Firebase connection test
        val database = Firebase.database
        val testRef = database.getReference("testMessage")
        testRef.setValue("Firebase connected")
            .addOnSuccessListener { Log.d(TAG, "Test message written to Firebase successfully") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to write test message", e) }

        // Initialize notification channel
        createNotificationChannel()

        // Initialize UI elements including new progress bars
        initializeUIElements()

        // Setup Firebase listeners for battery and food levels
        setupBatteryLevelListener()
        setupFoodLevelListener()


        // Setup main activity UI components and functionality
        setupMainActivity()
    }

    private fun createNotificationChannel() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Pet Feeder Notifications"
            val descriptionText = "Notifications for battery and food levels"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Added function to cleanly redirect to login
    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun initializeUIElements() {
        // Initialize existing UI elements
        batteryLevelText = findViewById(R.id.batteryLevelText)
        foodLevelText = findViewById(R.id.foodLevelText)

        // Initialize new progress bars
        foodLevelProgress = findViewById(R.id.foodLevelProgress)
        batteryLevelProgress = findViewById(R.id.batteryLevelProgress)

        // Set a delayed task to enable notifications after initial data load
        Handler(Looper.getMainLooper()).postDelayed({
            isInitialDataLoad = false
        }, 3000) // 3 seconds delay
    }

    private fun setupBatteryLevelListener() {
        val batteryRef = FirebaseDatabase.getInstance().getReference("battery_level/level")
        batteryRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val rawBatteryLevel = snapshot.getValue(Double::class.java)
                if (rawBatteryLevel != null) {
                    // Constrain battery level between 0 and 100
                    val batteryLevel = rawBatteryLevel.coerceIn(0.0, 100.0)

                    // Update text display with 2 decimal places
                    batteryLevelText.text = "Battery Level: %.2f%%".format(batteryLevel)

                    // Update progress bar with rounded value
                    batteryLevelProgress.progress = batteryLevel.toInt()

                    // Change color based on battery level
                    batteryLevelProgress.progressTintList = ColorStateList.valueOf(
                        when {
                            batteryLevel >= 75 -> Color.parseColor("#4CAF50")  // Green
                            batteryLevel >= 50 -> Color.parseColor("#2196F3")  // Blue
                            batteryLevel >= 25 -> Color.parseColor("#FFC107")  // Yellow
                            else -> Color.parseColor("#F44336")  // Red
                        }
                    )

                    // Check battery level for notifications
                     checkBatteryLevelForNotification(batteryLevel)

                } else {
                    batteryLevelText.text = "Battery Level: Unknown"
                    batteryLevelProgress.progress = 0
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to read battery level: ${error.message}")
                batteryLevelText.text = "Battery Level: Error"
                batteryLevelProgress.progress = 0
            }
        })
    }

    // Updated notification function
    private fun checkBatteryLevelForNotification(batteryLevel: Double) {
        val batteryInt = batteryLevel.toInt()
        val thresholdReached = when {
            batteryInt <= 1 -> 1
            batteryInt < 25 -> 25
            batteryInt < 50 -> 50
            batteryInt < 75 -> 75
            else -> 100
        }

        // Skip notifications on initial data load
        if (isInitialDataLoad) {
            lastBatteryThreshold = thresholdReached
            return
        }

        // Only send notification if threshold has decreased AND we haven't notified for this threshold yet
        if (thresholdReached < lastBatteryThreshold && !notifiedBatteryThresholds.contains(thresholdReached)) {
            lastBatteryThreshold = thresholdReached
            notifiedBatteryThresholds.add(thresholdReached)

            val message = when (thresholdReached) {
                1 -> "Recharge battery now!"
                25 -> "Battery is below 25%"
                50 -> "Battery is below 50%"
                75 -> "Battery is below 75%"
                else -> return // No notification needed
            }

            sendNotification(
                batterynotificationID,
                "Battery Alert",
                message
            )
        } else if (thresholdReached > lastBatteryThreshold) {
            // If battery level increased to a higher threshold, update tracking
            // and reset notifications for lower thresholds
            lastBatteryThreshold = thresholdReached
            notifiedBatteryThresholds.removeIf { it < thresholdReached }
        }
    }


    private fun setupFoodLevelListener() {
        val foodRef = FirebaseDatabase.getInstance().getReference("food_level/level")
        foodRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val rawFoodLevel = snapshot.getValue(Double::class.java)
                if (rawFoodLevel != null) {
                    // Constrain food level between 0 and 100
                    val foodLevel = rawFoodLevel.coerceIn(0.0, 100.0)

                    // Update text display with 2 decimal places
                    foodLevelText.text = "Food Level: %.2f%%".format(foodLevel)

                    // Update progress bar with rounded value
                    foodLevelProgress.progress = foodLevel.toInt()

                    // Change color based on food level
                    foodLevelProgress.progressTintList = ColorStateList.valueOf(
                        when {
                            foodLevel >= 75 -> Color.parseColor("#4CAF50")  // Green
                            foodLevel >= 50 -> Color.parseColor("#2196F3")  // Blue
                            foodLevel >= 25 -> Color.parseColor("#FFC107")  // Yellow
                            else -> Color.parseColor("#F44336")  // Red
                        }
                    )

                    // Check food level for notifications
                      checkFoodLevelForNotification(foodLevel)

                } else {
                    foodLevelText.text = "Food Level: Unknown" // In case nothing or possible "negative" reading
                    foodLevelProgress.progress = 0
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to read food level: ${error.message}")
                foodLevelText.text = "Food Level: Error" // Might remove this, error handling
                foodLevelProgress.progress = 0
            }
        })
    }

    // Updated notification function
    private fun checkFoodLevelForNotification(foodLevel: Double) {
        val foodInt = foodLevel.toInt()
        val thresholdReached = when {
            foodInt <= 1 -> 1
            foodInt < 25 -> 25
            foodInt < 50 -> 50
            foodInt < 75 -> 75
            else -> 100
        }

        // Skip notifications on initial data load
        if (isInitialDataLoad) {
            lastFoodThreshold = thresholdReached
            return
        }

        // Only send notification if threshold has decreased AND we haven't notified for this threshold yet
        if (thresholdReached < lastFoodThreshold && !notifiedFoodThresholds.contains(thresholdReached)) {
            lastFoodThreshold = thresholdReached
            notifiedFoodThresholds.add(thresholdReached)

            val message = when (thresholdReached) {
                1 -> "Refill food now!"
                25 -> "Food is below 25%"
                50 -> "Food is below 50%"
                75 -> "Food is below 75%"
                else -> return // No notification needed
            }

            sendNotification(
                foodnotificationID,
                "Food Alert",
                message
            )
        } else if (thresholdReached > lastFoodThreshold) {
            // If food level increased to a higher threshold, update tracking
            // and reset notifications for lower thresholds
            lastFoodThreshold = thresholdReached
            notifiedFoodThresholds.removeIf { it < thresholdReached }
        }
    }

    private fun sendNotification(notificationId: Int, title: String, message: String) {
        val builder = NotificationCompat.Builder(this, channelID)
            .setSmallIcon(R.drawable.img_food) // icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notificationManager.notify(notificationId, builder.build())
    }

    /*
    private fun isSessionExpired(): Boolean {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val lastLoginTime = prefs.getLong("lastLoginTime", 0L)
        val currentTime = System.currentTimeMillis()

        // Check for valid lastLoginTime value
        // This prevents bypassing login if lastLoginTime was never set
        return lastLoginTime == 0L || (currentTime - lastLoginTime > sessionTimeoutMillis)
    } */

    // Method to update the last login time in shared preferences
    // This should be called from LoginActivity when user logs in
    companion object {
        fun updateLoginTime(context: Context) {
            val currentTime = System.currentTimeMillis()
            context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                .edit()
                .putLong("lastLoginTime", currentTime)
                .apply()
        }
    }

    // onResume check to verify session hasn't expired while app was in background
    override fun onResume() {
        super.onResume()

        if (auth.currentUser == null /*|| isSessionExpired() */) {
            redirectToLogin()
        }
    }

    // Main Activity UI set up
    private fun setupMainActivity() {
        moreInfoButton = findViewById(R.id.moreInfoButton)
        dashboardContainer = findViewById(R.id.dashboardContainer)
        listViewSection = findViewById(R.id.listViewSection)
        listView = findViewById(R.id.lvMain)
        listViewBackButton = findViewById(R.id.listViewBackButton)
        logoutButton = findViewById(R.id.logoutButton)
        manualFeedButton = findViewById(R.id.manualFeedButton)
        scheduleFeedButton = findViewById(R.id.scheduleFeedButton)
        errorSectionButton = findViewById(R.id.errorSectionButton)
        viewSchedulesButton = findViewById(R.id.viewSchedulesButton)
      //  redirectToListViewButton = findViewById(R.id.redirectToListViewButton)

        setupListView()
        scheduleSync()
        setupButtonListeners()
    }

    private fun setupButtonListeners() {
        // Log out functionality
        logoutButton.setOnClickListener {
            // Clear the lastLoginTime when user manually logs out
            getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                .edit()
                .remove("lastLoginTime")
                .apply()

            auth.signOut()
            redirectToLogin()
        }

        // Manual feed button functionality
        manualFeedButton.setOnClickListener {
            val intent = Intent(this, ManualActivity::class.java)
            startActivity(intent)
        }

        // Schedule feed button functionality
        scheduleFeedButton.setOnClickListener {
            val intent = Intent(this, ScheduleActivity::class.java)
            startActivity(intent)
        }

        // Error section button functionality
        errorSectionButton.setOnClickListener {
            val intent = Intent(this, ErrorsActivity::class.java)
            startActivity(intent)
        }

        // View schedules button functionality
        viewSchedulesButton.setOnClickListener {
            val intent = Intent(this, UpdatesActivity::class.java)
            startActivity(intent)
        }

        // More Info button functionality
        moreInfoButton.setOnClickListener {
            val intent = Intent(this, MoreInfoActivity::class.java)
            startActivity(intent)
        }

        /*
        // Redirect to ListView functionality
        redirectToListViewButton.setOnClickListener {
            toggleViews()
        }

        // ListView back button functionality
        listViewBackButton.setOnClickListener {
            toggleViews()
        }

         */
    }

    private fun setupListView() { // Listview for original app
        val title = resources.getStringArray(R.array.Main)
        val description = resources.getStringArray(R.array.Description)
        val adapter = SimpleAdapter(this, title, description)
        listView.adapter = adapter
    }

    private fun toggleViews() { // Display swap to go between log in and dashboard
        if (dashboardContainer.visibility == View.VISIBLE) {
            dashboardContainer.visibility = View.GONE
            listViewSection.visibility = View.VISIBLE
        } else {
            dashboardContainer.visibility = View.VISIBLE
            listViewSection.visibility = View.GONE
        }
    }

    private fun scheduleSync() { // Assists with schedule build
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(applicationContext).enqueue(syncRequest)
    }


    // Check if this needs to be deleted or commented out, original application UI (NOT part of final Application Build)
    // Adapter for ListView
    inner class SimpleAdapter(
        mContext: Context,
        private val titleArray: Array<String>, // array of titles
        private val descriptionArray: Array<String> // array of descriptions
    ) : BaseAdapter() {

        private val layoutInflater = LayoutInflater.from(mContext)

        override fun getCount(): Int = titleArray.size // items displayed by title

        override fun getItem(position: Int): Any = titleArray[position] // Returns the data item at a specific position

        override fun getItemId(position: Int): Long = position.toLong() // Returns the unique ID for the item at the given position

        // Returns the view for each item in the list
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            // Reuse an existing view if available, otherwise inflate a new one
            val view = convertView ?: layoutInflater.inflate(R.layout.main_activity_single_item, parent, false)

            // UI visuals based on layout
            val imageView = view.findViewById<ImageView>(R.id.ivMain)
            val titleTextView = view.findViewById<TextView>(R.id.tvMain)
            val descriptionTextView = view.findViewById<TextView>(R.id.tvDescription)

            // Title and description set based on the ID
            titleTextView.text = titleArray[position]
            descriptionTextView.text = descriptionArray[position]

            // Image set based on title
            imageView.setImageResource(
                when (titleArray[position].lowercase()) {
                    "schedule" -> R.drawable.img_schedule
                    "updates" -> R.drawable.img_update
                    "errors" -> R.drawable.img_error
                    "manual" -> R.drawable.img_manual
                    "food" -> R.drawable.img_food
                    else -> R.drawable.img_battery // only one image left
                }
            )

            view.setOnClickListener {
                val intent = when (titleArray[position].lowercase()) {
                    "schedule" -> Intent(applicationContext, ScheduleActivity::class.java)
                    "updates" -> Intent(applicationContext, UpdatesActivity::class.java)
                    "errors" -> Intent(applicationContext, ErrorsActivity::class.java)
                    "manual" -> Intent(applicationContext, ManualActivity::class.java)
                    "food" -> Intent(applicationContext, FoodActivity::class.java)
                    else -> Intent(applicationContext, BatteryActivity::class.java) // only one description left
                }
                startActivity(intent) // Launch
            }

            return view
        }
    }
}


