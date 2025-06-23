package com.example.talking_bill

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.talking_bill.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import android.Manifest
import androidx.core.app.ActivityCompat

/**
 * Main activity for the Talking Bill application.
 * This app monitors notifications from banking apps and announces money transactions.
 * Features:
 * - Notification monitoring and filtering
 * - Text-to-speech for transaction announcements
 * - Background service for continuous monitoring
 * - Battery optimization handling
 * - Notification history management
 * - Automatic service start after permissions are granted (no manual switch)
 */
class MainActivity : AppCompatActivity() {
    // UI Components
    private lateinit var adapter: NotificationAdapter
    private lateinit var prefs: SharedPreferences
    private lateinit var binding: ActivityMainBinding
    private lateinit var statusText: TextView
    private lateinit var configAppsText: TextView
    private lateinit var enableButton: Button
    private lateinit var clearButton: Button
    private lateinit var resetButton: Button
    private lateinit var saveToggle: Switch
    private lateinit var filterToggle: Switch
    private lateinit var notificationsRecyclerView: RecyclerView
    private var batteryOptimizationDialog: AlertDialog? = null
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var loadingText: TextView
    private val REQUEST_CODE_POST_NOTIFICATIONS = 1001

    /**
     * Broadcast receiver for handling notification updates from the service.
     * Receives notifications and updates the UI accordingly.
     */
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NotificationListenerService.ACTION_NOTIFICATION_RECEIVED) {
                val notificationData = intent.getStringExtra(NotificationListenerService.EXTRA_NOTIFICATION_DATA)
                if (notificationData != null) {
                    adapter.addNotification(notificationData)
                    binding.notificationsRecyclerView.scrollToPosition(0)
                }
            }
        }
    }

    companion object {
        // SharedPreferences keys
        private const val PREFS_NAME = "NotificationPrefs"
        private const val KEY_SAVE_ENABLED = "save_enabled"
        private const val KEY_FILTER_ENABLED = "filter_enabled"
        private const val KEY_BACKGROUND_ENABLED = "background_enabled"
        private const val KEY_SERVICE_STATE = "service_state"
        private const val KEY_SPEECH_PREFIX = "speech_prefix"
        private const val KEY_SPEECH_CURRENCY = "speech_currency"
        private const val KEY_PENDING_CHANNEL_DELETE = "pending_channel_delete"
    }

    /**
     * Initializes the activity and sets up all necessary components.
     * - Initializes UI components
     * - Sets up notification monitoring
     * - Configures user preferences
     * - Handles battery optimization
     * - Requests notification permissions (Android 13+)
     * - Service starts automatically after all permissions are granted
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize loading views
        loadingOverlay = binding.loadingOverlay
        loadingText = binding.loadingText

        // Show loading during initialization
        showLoading("Initializing app...")

        // Check and request battery optimization exemption
        checkBatteryOptimization()

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Check if we need to delete the notification channel
        if (prefs.getBoolean(KEY_PENDING_CHANNEL_DELETE, false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    notificationManager.deleteNotificationChannel(NotificationListenerService.NOTIFICATION_CHANNEL_ID)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error deleting notification channel on startup", e)
                }
            }
            prefs.edit().remove(KEY_PENDING_CHANNEL_DELETE).apply()
        }
        
        // Set default values for speech settings if not already set
        if (!prefs.contains(KEY_SPEECH_PREFIX)) {
            prefs.edit().putString(KEY_SPEECH_PREFIX, "đã nhận").apply()
        }
        if (!prefs.contains(KEY_SPEECH_CURRENCY)) {
            prefs.edit().putString(KEY_SPEECH_CURRENCY, "đồng").apply()
        }
        
        // Set the default values in the EditText fields
        binding.prefixEditText.setText(prefs.getString(KEY_SPEECH_PREFIX, "đã nhận"))
        binding.currencyEditText.setText(prefs.getString(KEY_SPEECH_CURRENCY, "đồng"))

        initializeViews()
        setupAdapters()
        setupClickListeners()
        setupToggles()
        registerBroadcastReceiver()
        loadConfig()
        checkNotificationAccess()
        startServiceIfNeeded()
        updateUI()
        hideLoading()
        requestNotificationPermissionIfNeeded()
    }

    /**
     * Initializes all view references from binding.
     * This function sets up all UI components used throughout the activity.
     */
    private fun initializeViews() {
        statusText = binding.statusText
        configAppsText = binding.configAppsText
        enableButton = binding.enableButton
        clearButton = binding.clearButton
        resetButton = binding.resetButton
        saveToggle = binding.saveToggle
        filterToggle = binding.filterToggle
        notificationsRecyclerView = binding.notificationsRecyclerView
    }

    /**
     * Sets up the RecyclerView adapter for displaying notifications.
     * Configures the layout manager and click listeners.
     */
    private fun setupAdapters() {
        adapter = NotificationAdapter()
        notificationsRecyclerView.layoutManager = LinearLayoutManager(this)
        notificationsRecyclerView.adapter = adapter
        adapter.onItemLongClick = { position -> deleteNotification(position) }
    }

    /**
     * Sets up click listeners for all buttons in the UI.
     * Handles navigation to settings, clearing notifications, and resetting the app.
     */
    private fun setupClickListeners() {
        enableButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        clearButton.setOnClickListener {
            showClearConfirmationDialog()
        }

        resetButton.setOnClickListener {
            showResetConfirmationDialog()
        }

        binding.saveSpeechButton.setOnClickListener {
            saveSpeechSettings()
        }
    }

    /**
     * Saves the speech customization settings to SharedPreferences.
     * Updates the prefix and currency text for transaction announcements.
     */
    private fun saveSpeechSettings() {
        val prefix = binding.prefixEditText.text.toString()
        val currency = binding.currencyEditText.text.toString()
        prefs.edit()
            .putString(KEY_SPEECH_PREFIX, prefix)
            .putString(KEY_SPEECH_CURRENCY, currency)
            .apply()
        Toast.makeText(this, "Speech settings saved", Toast.LENGTH_SHORT).show()
    }

    /**
     * Sets up toggle switches for save and filter functionality.
     * Configures the initial state and change listeners for each toggle.
     */
    private fun setupToggles() {
        // Setup save toggle
        saveToggle.isChecked = prefs.getBoolean(KEY_SAVE_ENABLED, true)
        saveToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_SAVE_ENABLED, isChecked).apply()
            showCustomToast(
                if (isChecked) "Notifications will be saved" 
                else "Notifications will not be saved", 
                isChecked
            )
        }

        // Setup filter toggle
        filterToggle.isChecked = prefs.getBoolean(KEY_FILTER_ENABLED, true)
        filterToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_FILTER_ENABLED, isChecked).apply()
            showCustomToast(
                if (isChecked) "Filter and voice enabled" 
                else "Filter and voice disabled",
                isChecked
            )
        }
    }

    /**
     * Registers the broadcast receiver for notification updates.
     * This allows the activity to receive notification events from the service.
     */
    private fun registerBroadcastReceiver() {
        val filter = IntentFilter(NotificationListenerService.ACTION_NOTIFICATION_RECEIVED)
        registerReceiver(notificationReceiver, filter)
    }

    /**
     * Starts the service if permission is granted and the service switch was on.
     * This ensures the service is running when the app starts if it was previously enabled.
     */
    private fun startServiceIfNeeded() {
        if (isNotificationServiceEnabled() && prefs.getBoolean(KEY_SERVICE_STATE, false)) {
            startForegroundService()
        }
    }

    /**
     * Cleans up resources when the activity is destroyed.
     * Unregisters the broadcast receiver and handles the battery optimization dialog.
     */
    override fun onDestroy() {
        super.onDestroy()
        batteryOptimizationDialog?.dismiss()
        batteryOptimizationDialog = null
        
        try {
            unregisterReceiver(notificationReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
        
        if (prefs.getBoolean(KEY_BACKGROUND_ENABLED, true)) {
            startForegroundService()
        }
    }

    /**
     * Handles activity resume events.
     * Updates the UI and checks battery optimization status.
     */
    override fun onResume() {
        super.onResume()
        updateUI()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                batteryOptimizationDialog?.dismiss()
                batteryOptimizationDialog = null
            } else {
                checkBatteryOptimization()
            }
        }
        
        if (isNotificationServiceEnabled()) {
            if (prefs.getBoolean(KEY_SERVICE_STATE, false)) {
                startForegroundService()
            }
            loadNotifications()
        } else {
            stopForegroundService()
            prefs.edit().putBoolean(KEY_SERVICE_STATE, false).apply()
        }
    }

    /**
     * Updates the UI based on notification access status.
     * Shows/hides relevant buttons and updates status text.
     */
    private fun updateUI() {
        val enabled = isNotificationServiceEnabled()
        if (enabled) {
            statusText.text = "Notification access is enabled"
            enableButton.visibility = View.GONE
            clearButton.visibility = View.VISIBLE
            resetButton.visibility = View.VISIBLE
            loadNotifications()
        } else {
            statusText.text = "Please enable notification access"
            enableButton.visibility = View.VISIBLE
            clearButton.visibility = View.GONE
            resetButton.visibility = View.GONE
        }
    }

    /**
     * Checks if notification access is enabled for the app.
     * @return Boolean indicating if notification access is enabled
     */
    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (flat != null && !flat.isEmpty()) {
            val names = flat.split(":")
            for (name in names) {
                if (name.contains(pkgName)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Loads notifications from the log file and updates the RecyclerView.
     * Handles file reading and parsing of stored notifications.
     */
    private fun loadNotifications() {
        try {
            val notifications = mutableListOf<String>()
            try {
                openFileInput("notification_log.txt").use { input ->
                    val content = input.bufferedReader().readText()
                    content.split("---").forEach { notification ->
                        if (notification.trim().isNotEmpty()) {
                            notifications.add(notification.trim())
                        }
                    }
                }
            } catch (e: Exception) {
                // No notification log file found
            }
            adapter.updateNotifications(notifications.reversed())
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading notifications", e)
            showCustomToast("Error loading notifications", false)
        }
    }

    /**
     * Deletes a notification at the specified position.
     * Updates both the UI and the stored notification log.
     * @param position The position of the notification to delete
     */
    private fun deleteNotification(position: Int) {
        try {
            val currentList = adapter.getCurrentList().toMutableList()
            adapter.removeItem(position)
            
            try {
                openFileOutput("notification_log.txt", Context.MODE_PRIVATE).use { output ->
                    currentList.filterIndexed { index, _ -> index != position }
                        .reversed()
                        .forEach { notification ->
                            output.write("$notification\n---\n".toByteArray())
                        }
                }
                showCustomToast("Notification deleted", true)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error updating notification file", e)
                showCustomToast("Error updating notifications", false)
                adapter.updateNotifications(currentList)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error deleting notification", e)
            showCustomToast("Error deleting notification", false)
        }
    }

    /**
     * Shows a confirmation dialog before clearing all notifications.
     */
    private fun showClearConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Notifications")
            .setMessage("Are you sure you want to delete all notifications?")
            .setPositiveButton("Clear") { _, _ -> clearAllNotifications() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Clears all notifications from both the UI and storage.
     */
    private fun clearAllNotifications() {
        try {
            adapter.clearNotifications()
            
            try {
                openFileOutput("notification_log.txt", Context.MODE_PRIVATE).use { output ->
                    output.write("".toByteArray())
                }
                showCustomToast("All notifications cleared", true)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error clearing notification file", e)
                showCustomToast("Error clearing notifications", false)
                loadNotifications()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error clearing notifications", e)
            showCustomToast("Error clearing notifications", false)
        }
    }

    /**
     * Loads and displays app configuration from assets.
     * Parses the JSON configuration file and updates the UI.
     */
    private fun loadConfig() {
        try {
            val configJson = assets.open("app_config.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val jsonObject = JSONObject(configJson)
            val appNames = jsonObject.keys().asSequence().toList()
            
            val configText = StringBuilder("Loaded Apps and Required Keywords:\n\n")
            appNames.forEach { appName ->
                try {
                    val appConfig = jsonObject.getJSONObject(appName)
                    val keywords = appConfig.getJSONArray("receive_keyword")
                        .let { array ->
                            (0 until array.length()).map { array.getString(it) }
                        }
                    val regexes = appConfig.getJSONArray("m_regex")
                        .let { array ->
                            (0 until array.length()).map { array.getString(it) }
                        }
                    configText.append("$appName:\n")
                    configText.append("Keywords: ${keywords.joinToString(", ")}\n")
                    configText.append("Money Regex: ${regexes.joinToString(", ")}\n\n")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error parsing app config for $appName", e)
                    configText.append("$appName: Error loading configuration\n\n")
                }
            }
            
            configAppsText.text = configText.toString()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading app configuration", e)
            configAppsText.text = "Error loading app configuration. Please restart the app."
            showCustomToast("Error loading configuration", false)
        }
    }

    /**
     * Checks and requests notification access if needed.
     * Shows a dialog to guide the user through enabling notification access.
     */
    private fun checkNotificationAccess() {
        if (!isNotificationServiceEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Notification Access Required")
                .setMessage("This app needs notification access to work. Please enable it in the next screen.")
                .setPositiveButton("Enable") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    /**
     * Checks and requests battery optimization exemption.
     * Shows a dialog to guide the user through disabling battery optimization.
     */
    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                batteryOptimizationDialog?.dismiss()
                
                batteryOptimizationDialog = AlertDialog.Builder(this)
                    .setTitle("Battery Optimization")
                    .setMessage("This app needs to run in the background to monitor notifications. Please disable battery optimization for this app.")
                    .setPositiveButton("Disable Optimization") { _, _ ->
                        try {
                            val intent = Intent().apply {
                                action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                data = android.net.Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error launching battery optimization settings", e)
                            showCustomToast("Failed to open battery settings", false)
                        }
                    }
                    .setNegativeButton("Not Now") { _, _ ->
                        batteryOptimizationDialog?.dismiss()
                        batteryOptimizationDialog = null
                    }
                    .setCancelable(false)
                    .create()
                
                batteryOptimizationDialog?.show()
            } else {
                batteryOptimizationDialog?.dismiss()
                batteryOptimizationDialog = null
            }
        }
    }

    /**
     * Starts the foreground service for notification monitoring.
     * Checks battery optimization status before starting.
     */
    private fun startForegroundService() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    showCustomToast("Please disable battery optimization for reliable service", false)
                    checkBatteryOptimization()
                    return
                }
            }

            val serviceIntent = Intent(this, NotificationListenerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting foreground service", e)
            showCustomToast("Failed to start service", false)
        }
    }

    /**
     * Stops the foreground service.
     */
    private fun stopForegroundService() {
        try {
            val serviceIntent = Intent(this, NotificationListenerService::class.java)
            stopService(serviceIntent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error stopping foreground service", e)
        }
    }

    /**
     * Shows a custom styled toast message.
     * @param message The message to display
     * @param isSuccess Whether the message indicates success or failure
     */
    private fun showCustomToast(message: String, isSuccess: Boolean) {
        try {
            val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
            val view = toast.view
            view?.setBackgroundColor(if (isSuccess) getColor(R.color.toast_success) else getColor(R.color.toast_error))
            val text = view?.findViewById<TextView>(android.R.id.message)
            text?.setTextColor(Color.WHITE)
            text?.textSize = 16f
            toast.setGravity(Gravity.CENTER, 0, 0)
            toast.show()
        } catch (e: Exception) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shows a confirmation dialog before resetting the app.
     */
    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset App")
            .setMessage("This will reset all settings and clear all data. The app will restart. Are you sure?")
            .setPositiveButton("Reset") { _, _ -> resetApp() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Resets the app to its initial state.
     * Clears all data, stops the service, and restarts the app.
     */
    private fun resetApp() {
        showLoading("Resetting app...")
        
        try {
            stopForegroundService()
            
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    prefs.edit().putBoolean(KEY_PENDING_CHANNEL_DELETE, true).apply()

                    prefs.edit().clear().apply()
                    deleteFile("notification_log.txt")

                    saveToggle.isChecked = true
                    filterToggle.isChecked = true

                    prefs.edit()
                        .putString(KEY_SPEECH_PREFIX, "đã nhận")
                        .putString(KEY_SPEECH_CURRENCY, "đồng")
                        .putBoolean(KEY_SAVE_ENABLED, true)
                        .putBoolean(KEY_FILTER_ENABLED, true)
                        .putBoolean(KEY_BACKGROUND_ENABLED, true)
                        .apply()

                    binding.prefixEditText.setText("đã nhận")
                    binding.currencyEditText.setText("đồng")

                    adapter.clearNotifications()

                    val componentName = ComponentName(this, NotificationListenerService::class.java)
                    
                    packageManager.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )

                    packageManager.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )

                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    
                    Handler(Looper.getMainLooper()).postDelayed({
                        hideLoading()
                        restartApp()
                    }, 3000)

                } catch (e: Exception) {
                    Log.e("MainActivity", "Error during reset", e)
                    hideLoading()
                    showCustomToast("Error resetting app", false)
                }
            }, 1000)

        } catch (e: Exception) {
            Log.e("MainActivity", "Error stopping service during reset", e)
            hideLoading()
            showCustomToast("Error stopping service", false)
        }
    }

    /**
     * Restarts the app completely.
     * Clears all activities and starts fresh.
     */
    private fun restartApp() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            finishAffinity()
            startActivity(intent)
            Runtime.getRuntime().exit(0)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error restarting app", e)
            finish()
        }
    }

    /**
     * Shows the loading overlay with a custom message.
     * @param message The message to display in the loading overlay
     */
    private fun showLoading(message: String = "Loading...") {
        loadingText.text = message
        loadingOverlay.visibility = View.VISIBLE
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
    }

    /**
     * Hides the loading overlay.
     */
    private fun hideLoading() {
        loadingOverlay.visibility = View.GONE
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    AlertDialog.Builder(this)
                        .setTitle("Notification Permission Required")
                        .setMessage("This app needs notification permission to show important alerts and run reliably in the background.")
                        .setPositiveButton("Allow") { _, _ ->
                            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_POST_NOTIFICATIONS)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_POST_NOTIFICATIONS)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showCustomToast("Notification permission granted", true)
                startForegroundService()
            } else {
                showCustomToast("Notification permission denied. Some features may not work.", false)
            }
        }
    }
}