import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.data.services.BaseViewModelFactory
import com.vihmessenger.vihchatbot.utils.DynamicThemeManager
import com.vihmessenger.vihchatbot.utils.ImagePickerUtil
import com.vihmessenger.vihchatbot.utils.PermissionRequestCodes


abstract class BaseActivity : AppCompatActivity(), DynamicThemeManager.ThemeChangeListener {

    companion object {
        var TAG: String = BaseActivity::class.java.simpleName // Changed to var if it needs to be overridden by subclasses, or keep val
        private const val PHONE_PERMISSIONS_REQUEST_CODE = 100
        private const val REQUEST_IMAGE_PICK = 2
    }

    private var toastMessage: Toast? = null
    private var loaderAlertDialog: AlertDialog? = null
    private var phoneNumber: String? = null // Not used in provided snippet, but kept
    private var statusBarColorSet = false // Tracks if status bar color has been set at least once

    private var initialThemeApplied = false


    abstract fun initViewModels()
    abstract fun initView()
    abstract fun setObservers()
    abstract fun setListeners()
    abstract fun onViewClick(view: View?)


    // This is the ThemeChangeListener implementation
    override fun onThemeChanged(
        primaryColor: Int,
        secondaryColor: Int,
        primaryTextColor: Int,
        secondaryTextColor: Int,
        headerColor: Int,
        defaultTextColor: Int
    ) {
        // Default implementation in BaseActivity.
        // Child activities like ChatActivity override this to apply specific theme elements.
        // The commented out call to updateStatusBarColor(primaryColor) suggests that
        // a global status bar color update based on primaryColor was considered but not implemented here.
        // updateStatusBarColor(primaryColor) // Example: Could be a default behavior
        applyTheme(primaryColor) // Calls the abstract method for further theming by children
    }

    protected val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onNotificationPermissionResult(isGranted)
    }

    // This requestPermission method seems unused in the context of the problem.
    // private fun requestPermission() {
    //     requestPermissions(
    //         arrayOf(
    //             Manifest.permission.READ_SMS,
    //             Manifest.permission.READ_PHONE_NUMBERS,
    //             Manifest.permission.READ_PHONE_STATE
    //         ), PHONE_PERMISSIONS_REQUEST_CODE
    //     )
    // }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
//        DynamicThemeManager.registerListener(this) // Register for theme changes

        // The initial status bar color is handled by ChatActivity.onCreate itself before this point in lifecycle.
        // The commented out setStatusBarColor(Color.BLACK) is correctly not active.
        // if (!statusBarColorSet) {
        //     setStatusBarColor(Color.BLACK); // This was commented out, which is good.
        // }
        // Follow the device's system light/dark setting.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    protected fun applyThemeAndSetupListeners() {
        if (!initialThemeApplied) {
            DynamicThemeManager.registerListener(this) // This will trigger onThemeChanged
            initialThemeApplied = true
        }
        // You can also move other common listener setups here if they depend on views
    }

    override fun onDestroy() {
        DynamicThemeManager.unregisterListener(this)
        super.onDestroy()
    }

    // This dispatchPickImageIntent method seems unused in the context of the problem.
    // private fun dispatchPickImageIntent() {
    //     if (ContextCompat.checkSelfPermission(
    //             this, Manifest.permission.READ_EXTERNAL_STORAGE
    //         ) != PackageManager.PERMISSION_GRANTED
    //     ) {
    //         ActivityCompat.requestPermissions(
    //             this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_IMAGE_PICK
    //         )
    //         return
    //     }
    //     ImagePickerUtil.dispatchPickImageIntent(this)
    // }


    fun setUserPhoneNumber(mobileNumber: String) { // Not used in provided snippet, but kept
        phoneNumber = mobileNumber
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults) // Call super first or last, consistently. Usually last.
        var handled = false

        when (requestCode) {
            PermissionRequestCodes.PHONE_PERMISSION -> {
                // Your existing phone permission code...
                handled = true
            }
            PermissionRequestCodes.CAMERA_PERMISSION -> {
                // Your existing camera permission code...
                // To let fragments handle it, ensure handled remains false or super is called appropriately.
                handled = false
            }
            PermissionRequestCodes.GALLERY_PERMISSION -> {
                handled = false
            }
        }

        if (!handled) {
            supportFragmentManager.fragments.forEach { fragment ->
                // Check if fragment is not null and is added to the activity
                if (fragment != null && fragment.isAdded && fragment.isVisible) {
                    fragment.onRequestPermissionsResult(requestCode, permissions, grantResults)
                }
            }
        }
        // If super.onRequestPermissionsResult is called at the beginning, remove this.
        // If you want to ensure fragments get it even if activity handles it, adjust logic.
    }

    protected fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    onNotificationPermissionResult(true)
                }

                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    showNotificationPermissionRationale()
                }

                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            onNotificationPermissionResult(true)
        }
    }

    protected open fun onNotificationPermissionResult(isGranted: Boolean) {
        // Override this in child activities if needed
    }

    protected fun showNotificationPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission Required")
            .setMessage("We need notification permission to send you important updates and messages.")
            .setPositiveButton("Grant Permission") { dialog, _ ->
                dialog.dismiss()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                onNotificationPermissionResult(false)
            }
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.O) // Consider if this is needed if targetApi is O+ or if minSdk is O+
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        initViewModels()
        initView()
        setObservers()
        setListeners()
    }


    private fun initBlockingLoaderDialog() {
        if (loaderAlertDialog == null) { // Initialize only if null
            loaderAlertDialog = AlertDialog.Builder(this@BaseActivity).apply {
                setView(LayoutInflater.from(context).inflate(R.layout.layout_blocking_loader, null))
                setCancelable(false)
            }.create()

            loaderAlertDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    // getAssets() is already a method of Context, no need to override unless specialized.
    // override fun getAssets(): AssetManager {
    //    return resources.assets
    // }

    private val onClickListener = View.OnClickListener { view ->
        onViewClick(view)
    }

    fun setOnClickListener(vararg views: View?) { // Allow multiple views
        views.forEach { it?.setOnClickListener(onClickListener) }
    }

    fun setOnClickListener(view: View?) { // Keep single view version if used
        view?.setOnClickListener(onClickListener)
    }


    fun setStatusBarColor(color: Int) {
        val originalColorHex = String.format("#%06X", (0xFFFFFF and window.statusBarColor))
        val targetColorHex = String.format("#%06X", (0xFFFFFF and color))

        window.statusBarColor = color

        val newActualColor = window.statusBarColor
        val newActualColorHex = String.format("#%06X", (0xFFFFFF and newActualColor))

        if (newActualColor != color) {
            Log.e("StatusBarSetAttempt", "Activity: ${javaClass.simpleName} - FAILED to change statusBarColor. Expected $targetColorHex, but it's now $newActualColorHex.")
        } else {
            Log.d("StatusBarSetAttempt", "Activity: ${javaClass.simpleName} - SUCCESSFULLY set statusBarColor to $newActualColorHex.")
        }

        val isColorEffectivelyLight = isColorLight(newActualColor)

        try {
            // Only attempt to get InsetsController if DecorView seems ready
            if (window.peekDecorView() != null && window.peekDecorView().isAttachedToWindow) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.let { controller ->
                        if (isColorEffectivelyLight) {
                            controller.setSystemBarsAppearance(
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            )
                        } else {
                            controller.setSystemBarsAppearance(
                                0,
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            )
                        }
                    } ?: Log.w("StatusBarIconDebug", "API R+: InsetsController was null even after check.")
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    @Suppress("DEPRECATION")
                    val decorView = window.decorView
                    var flags = decorView.systemUiVisibility
                    if (isColorEffectivelyLight) {
                        flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    } else {
                        flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    }
                    decorView.systemUiVisibility = flags
                }
            } else {
                Log.w("StatusBarIconDebug", "DecorView not ready or not attached. Deferred status bar icon appearance.")
            }
            statusBarColorSet = true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting status bar appearance: " + e.message, e)
        }
    }

    private fun isColorLight(color: Int): Boolean {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        val darkness = 1 - (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0
        return darkness < 0.5
    }

    open fun updateStatusBarColor(color: Int) { // Made open in case a child wants to override its direct behavior
        setStatusBarColor(color)
    }

    private fun cancelToastMessage() {
        toastMessage?.cancel()
        toastMessage = null
    }

    fun showToast(message: String?) {
        if (applicationContext == null || message == null) return

        cancelToastMessage()
        toastMessage = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG)
        toastMessage?.show()
    }

    fun showBlockingLoader() {
        runOnUiThread {
            if (loaderAlertDialog == null) {
                initBlockingLoaderDialog()
            }
            if (loaderAlertDialog?.isShowing == false) { // Show only if not already showing
                loaderAlertDialog?.show()
            }
        }
    }

    fun requestGalleryPermission() { // This seems like a utility that could be static or in a helper if not tied to BaseActivity state
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            PermissionRequestCodes.GALLERY_PERMISSION
        )
    }

    fun hideBlockingLoader() {
        runOnUiThread { // Ensure UI operations on UI thread
            if (loaderAlertDialog?.isShowing == true) {
                loaderAlertDialog?.dismiss()
            }
        }
    }

    protected fun <T : ViewModel> getViewModel(
        viewModel: ViewModel, className: Class<T>
    ): T {
        return ViewModelProvider(
            this, BaseViewModelFactory(viewModel, className)
        )[className] // Modern [key] access
    }

    protected open fun applyTheme(primaryColor: Int) {
        // Child classes will override this to update their specific UI elements based on the primaryColor or other theme attributes.
    }
}