package com.vihmessenger.vihchatbot.ui.activity.home

import BaseActivity
import addFragmentWithFadeInNoStack // Assuming this handles replacement if fragment exists or you manage it
import android.Manifest
import android.content.Intent // Added import
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.vihmessenger.vihchatbot.AppController
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.constants.AppConstants
import com.vihmessenger.vihchatbot.databinding.ActivityDashBoardBinding
import com.vihmessenger.vihchatbot.ui.custom.StringCommunicator
import com.vihmessenger.vihchatbot.ui.fragments.DashboardFragment
import com.vihmessenger.vihchatbot.ui.fragments.OnBackPressedListener
import com.vihmessenger.vihchatbot.utils.PermissionRequestCodes // Ensure this class exists and is correct
import com.vihmessenger.vihchatbot.utils.sharedPreference.Prefs

class DashBoardActivity : BaseActivity(), StringCommunicator {

    private val _viewBinder by lazy { ActivityDashBoardBinding.inflate(layoutInflater) }
    private var mainFragment: DashboardFragment? = null
    private var phoneNumber: String? = null
    private var hashCode: String? = null
    private val OVERLAY_PERMISSION_REQUEST_CODE = 1234

    val prefs: Prefs by lazy {
        AppController.prefs!!
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(_viewBinder.root)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        Log.d(TAG, "onCreate: Intent Action: ${intent.action}, Extras: ${intent.extras}")
        processIntentExtras(intent) // Process initial intent

        // --- FIX: Call permission checks directly in onCreate ---
        checkAndRequestNotificationPermission()
//        checkOverlayPermission() // <<< ADD THIS LINE
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent: Intent Action: ${intent?.action}, Extras: ${intent?.extras}")
        setIntent(intent)
        processIntentExtras(intent)

        if (phoneNumber != null && hashCode != null) {
            Log.d(TAG, "onNewIntent: Re-evaluating fragment setup.")
            initView()
        } else {
            Log.e(TAG, "onNewIntent: Missing required extras in new intent. Finishing.")
            finishActivityWithMessage("Error: Invalid shortcut data.")
        }
    }

    private fun processIntentExtras(intent: Intent?) {
        if (intent == null) {
            Log.e(TAG, "processIntentExtras: Intent is null.")
            this.phoneNumber = null
            this.hashCode = null
            return
        }
        this.phoneNumber = intent.getStringExtra(AppConstants.PHONENUMBER)
        this.hashCode = intent.getStringExtra(AppConstants.HASHCODE_EXTRA)

        prefs.hashcode = this.hashCode
        prefs.phoneNumber = this.phoneNumber

        Log.d(TAG, "processIntentExtras: Phone: ${this.phoneNumber}, Hash: ${this.hashCode}, Launched from shortcut: ${intent.getBooleanExtra("launched_from_shortcut", false)}")
    }


    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PermissionRequestCodes.NOTIFICATION_PERMISSION
                )
            } else {
                setupFirebaseMessaging()
            }
        } else {
            setupFirebaseMessaging()
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // This will now correctly trigger the redirect
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
            } else {
                // Permission already granted
                Log.d(TAG, "Overlay permission is already granted.")
            }
        }
    }

    private fun setupFirebaseMessaging() {
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this.applicationContext)
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d(TAG, "FCM Token: $token")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionRequestCodes.NOTIFICATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Notification permission granted.")
                setupFirebaseMessaging()
            } else {
                Log.w(TAG, "Notification permission denied.")
                Toast.makeText(
                    this,
                    "Notification permission is recommended to receive important updates.",
                    Toast.LENGTH_LONG
                ).show()
                setupFirebaseMessaging()
            }
        }
    }


    override fun initViewModels() {
        // Your ViewModel initializations
    }

    override fun initView() {
        if (phoneNumber != null && hashCode != null) {
            Log.d(TAG, "initView: Setting up DashboardFragment. Phone: $phoneNumber, Hash: $hashCode")
            var existingFragment = supportFragmentManager.findFragmentById(R.id.flDashboardMain) as? DashboardFragment
            if (existingFragment == null ||
                existingFragment.arguments?.getString(AppConstants.PHONENUMBER) != phoneNumber ||
                existingFragment.arguments?.getString(AppConstants.HASHCODE_EXTRA) != hashCode) {

                mainFragment = DashboardFragment.getInstance(hashCode!!, phoneNumber!!)
                mainFragment?.let {
                    Log.d(TAG, "initView: Adding/Replacing DashboardFragment.")
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.flDashboardMain, it)
                        .commitAllowingStateLoss()
                }
            } else {
                Log.d(TAG, "initView: DashboardFragment already exists with correct data.")
                mainFragment = existingFragment
            }
        } else {
            Log.e(TAG, "initView: phoneNumber or hashCode is null. Finishing activity.")
            finishActivityWithMessage("Required information is missing.")
        }
    }

    private fun finishActivityWithMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }


    override fun setObservers() {}

    override fun setListeners() {}

    override fun onViewClick(view: View?) {}

    override fun onThemeChanged(
        primaryColor: Int,
        secondaryColor: Int,
        primaryTextColor: Int,
        secondaryTextColor: Int,
        headerColor: Int,
        defaultTextColor: Int
    ) {
        // Theme change logic
    }

    override fun onBackPressed() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.flDashboardMain)

        if (currentFragment is OnBackPressedListener && currentFragment.onBackPressed()) {
            return
        }
        if (currentFragment is DashboardFragment) {
            super.onBackPressed()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        private const val TAG = "DashBoardActivity"
    }

    override fun sendData(data: String) {
        if (mainFragment?.isAdded == true) {
            mainFragment?.receiveData(data)
        } else {
            (supportFragmentManager.findFragmentById(R.id.flDashboardMain) as? DashboardFragment)?.receiveData(data)
        }
    }

    override fun sendIndustryData(filterStr: String) {
        if (mainFragment?.isAdded == true) {
            mainFragment?.receiveIndustryData(filterStr)
        } else {
            (supportFragmentManager.findFragmentById(R.id.flDashboardMain) as? DashboardFragment)?.receiveIndustryData(filterStr)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            // It's good practice to check again after the user returns from settings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    Log.d(TAG, "onActivityResult: Overlay permission has been granted.")
                    Toast.makeText(this, "Overlay permission granted!", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "onActivityResult: Overlay permission was NOT granted.")
                    Toast.makeText(this, "Overlay permission is needed for full functionality.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}