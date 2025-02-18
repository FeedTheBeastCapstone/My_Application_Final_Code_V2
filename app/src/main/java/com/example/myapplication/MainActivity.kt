package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
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
    private lateinit var redirectToListViewButton: Button
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

    // Constants
    private val sessionTimeoutMillis = 10 * 60 * 1000 // 10 minutes
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase Authentication
        auth = FirebaseAuth.getInstance()

        // Setup Toolbar
        toolbar = findViewById(R.id.ToolbarMain)
        setSupportActionBar(toolbar)

        // Firebase connection test
        val database = Firebase.database
        val testRef = database.getReference("testMessage")
        testRef.setValue("Firebase connected")
            .addOnSuccessListener { Log.d(TAG, "Test message written to Firebase successfully") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to write test message", e) }

        // Initialize UI elements including new progress bars
        initializeUIElements()

        // Setup Firebase listeners for battery and food levels
        setupBatteryLevelListener()
        setupFoodLevelListener()

        // Check session expiration
        Handler(Looper.getMainLooper()).postDelayed({
            if (isSessionExpired()) {
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                setupMainActivity()
            }
        }, 500)
    }

    private fun initializeUIElements() {
        // Initialize existing UI elements
        batteryLevelText = findViewById(R.id.batteryLevelText)
        foodLevelText = findViewById(R.id.foodLevelText)

        // Initialize new progress bars
        foodLevelProgress = findViewById(R.id.foodLevelProgress)
        batteryLevelProgress = findViewById(R.id.batteryLevelProgress)
    }

    private fun setupBatteryLevelListener() {
        val batteryRef = FirebaseDatabase.getInstance().getReference("battery_level/level")
        batteryRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val batteryLevel = snapshot.getValue(Int::class.java)
                if (batteryLevel != null) {
                    // Update text display
                    batteryLevelText.text = "Battery Level: $batteryLevel%"

                    // Update progress bar
                    batteryLevelProgress.progress = batteryLevel

                    // Change color based on battery level
                    batteryLevelProgress.progressTintList = ColorStateList.valueOf(
                        when {
                            batteryLevel >= 75 -> Color.parseColor("#4CAF50")  // Green
                            batteryLevel >= 50 -> Color.parseColor("#2196F3")  // Blue
                            batteryLevel >= 25 -> Color.parseColor("#FFC107")  // Yellow
                            else -> Color.parseColor("#F44336")  // Red
                        }
                    )
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

    private fun setupFoodLevelListener() {
        val foodRef = FirebaseDatabase.getInstance().getReference("food_level/level")
        foodRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val foodLevel = snapshot.getValue(Int::class.java)
                if (foodLevel != null) {
                    // Update text display
                    foodLevelText.text = "Food Level: $foodLevel%"

                    // Update progress bar
                    foodLevelProgress.progress = foodLevel

                    // Change color based on food level
                    foodLevelProgress.progressTintList = ColorStateList.valueOf(
                        when {
                            foodLevel >= 75 -> Color.parseColor("#4CAF50")  // Green
                            foodLevel >= 50 -> Color.parseColor("#2196F3")  // Blue
                            foodLevel >= 25 -> Color.parseColor("#FFC107")  // Yellow
                            else -> Color.parseColor("#F44336")  // Red
                        }
                    )
                } else {
                    foodLevelText.text = "Food Level: Unknown"
                    foodLevelProgress.progress = 0
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to read food level: ${error.message}")
                foodLevelText.text = "Food Level: Error"
                foodLevelProgress.progress = 0
            }
        })
    }

    private fun isSessionExpired(): Boolean {
        val lastLoginTime = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            .getLong("lastLoginTime", 0L)
        val currentTime = System.currentTimeMillis()
        return currentTime - lastLoginTime > sessionTimeoutMillis
    }

    private fun setupMainActivity() {
        dashboardContainer = findViewById(R.id.dashboardContainer)
        listViewSection = findViewById(R.id.listViewSection)
        listView = findViewById(R.id.lvMain)
        listViewBackButton = findViewById(R.id.listViewBackButton)
        logoutButton = findViewById(R.id.logoutButton)
        manualFeedButton = findViewById(R.id.manualFeedButton)
        scheduleFeedButton = findViewById(R.id.scheduleFeedButton)
        errorSectionButton = findViewById(R.id.errorSectionButton)
        viewSchedulesButton = findViewById(R.id.viewSchedulesButton)
        redirectToListViewButton = findViewById(R.id.redirectToListViewButton)

        setupListView()
        scheduleSync()
        setupButtonListeners()
    }

    private fun setupButtonListeners() {
        // Log out functionality
        logoutButton.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
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

        // Redirect to ListView functionality
        redirectToListViewButton.setOnClickListener {
            toggleViews()
        }

        // ListView back button functionality
        listViewBackButton.setOnClickListener {
            toggleViews()
        }
    }

    private fun setupListView() {
        val title = resources.getStringArray(R.array.Main)
        val description = resources.getStringArray(R.array.Description)
        val adapter = SimpleAdapter(this, title, description)
        listView.adapter = adapter
    }

    private fun toggleViews() {
        if (dashboardContainer.visibility == View.VISIBLE) {
            dashboardContainer.visibility = View.GONE
            listViewSection.visibility = View.VISIBLE
        } else {
            dashboardContainer.visibility = View.VISIBLE
            listViewSection.visibility = View.GONE
        }
    }

    private fun scheduleSync() {
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(applicationContext).enqueue(syncRequest)
    }

    // Adapter for ListView
    inner class SimpleAdapter(
        mContext: Context,
        private val titleArray: Array<String>,
        private val descriptionArray: Array<String>
    ) : BaseAdapter() {

        private val layoutInflater = LayoutInflater.from(mContext)

        override fun getCount(): Int = titleArray.size

        override fun getItem(position: Int): Any = titleArray[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.main_activity_single_item, parent, false)

            val imageView = view.findViewById<ImageView>(R.id.ivMain)
            val titleTextView = view.findViewById<TextView>(R.id.tvMain)
            val descriptionTextView = view.findViewById<TextView>(R.id.tvDescription)

            titleTextView.text = titleArray[position]
            descriptionTextView.text = descriptionArray[position]

            imageView.setImageResource(
                when (titleArray[position].lowercase()) {
                    "schedule" -> R.drawable.img_schedule
                    "updates" -> R.drawable.img_update
                    "errors" -> R.drawable.img_error
                    "manual" -> R.drawable.img_manual
                    "food" -> R.drawable.img_food
                    else -> R.drawable.img_battery
                }
            )

            view.setOnClickListener {
                val intent = when (titleArray[position].lowercase()) {
                    "schedule" -> Intent(applicationContext, ScheduleActivity::class.java)
                    "updates" -> Intent(applicationContext, UpdatesActivity::class.java)
                    "errors" -> Intent(applicationContext, ErrorsActivity::class.java)
                    "manual" -> Intent(applicationContext, ManualActivity::class.java)
                    "food" -> Intent(applicationContext, FoodActivity::class.java)
                    else -> Intent(applicationContext, BatteryActivity::class.java)
                }
                startActivity(intent)
            }

            return view
        }
    }
}











