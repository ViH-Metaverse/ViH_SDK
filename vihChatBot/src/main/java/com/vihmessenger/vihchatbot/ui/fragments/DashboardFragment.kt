package com.vihmessenger.vihchatbot.ui.fragments

import BaseActivity
import BaseFragment
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap // Added import
import android.graphics.BitmapFactory // Added import
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope // Added import
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.vihmessenger.vihchatbot.AppController
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.constants.AppConstants
import com.vihmessenger.vihchatbot.data.model.UserProfileRequest
import com.vihmessenger.vihchatbot.databinding.FragmentDashboardBinding
import com.vihmessenger.vihchatbot.services.ShortcutPinnedReceiver
import com.vihmessenger.vihchatbot.ui.activity.home.DashBoardActivity
import com.vihmessenger.vihchatbot.ui.bottomsheet.IndustryFiltersBottomSheetFragment
import com.vihmessenger.vihchatbot.ui.custom.StringCommunicator
import com.vihmessenger.vihchatbot.utils.DynamicThemeManager
import com.vihmessenger.vihchatbot.utils.extensions.dpToPx
import com.vihmessenger.vihchatbot.utils.extensions.hideKeyboard
import com.vihmessenger.vihchatbot.utils.extensions.statusBarSecondaryColor
import com.vihmessenger.vihchatbot.utils.extensions.toHex
import com.vihmessenger.vihchatbot.utils.extensions.withAlpha
import com.vihmessenger.vihchatbot.utils.getVihSettings
import com.vihmessenger.vihchatbot.utils.sharedPreference.Prefs
import com.vihmessenger.vihchatbot.viewmodel.HomeViewModel
import com.vihmessenger.vihchatbot.viewmodel.ProfileViewModel
import kotlinx.coroutines.Dispatchers // Added import
import kotlinx.coroutines.launch // Added import
import kotlinx.coroutines.withContext // Added import
import java.net.HttpURLConnection // Added import
import java.net.URL // Added import

class DashboardFragment : BaseFragment(), OnBackPressedListener {

    val prefs: Prefs by lazy {
        AppController.prefs!!
    }

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var regularFont: Typeface
    private lateinit var semiBoldFont: Typeface

    private var mainFragment: ChatListFragment? = null
    private var discoverFragment: DiscoverFragment? = null
    private var settingFragment: SettingFragment? = null
    private var isAnimating: Boolean = false
    private var fcmToken: String? = null

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var profileViewModel: ProfileViewModel

    private var communicator: StringCommunicator? = null
    private var isFilterClear: Boolean = false

    private var currentPrimaryColor: Int = Color.BLACK // Default or initial value
    private var currentDefaultTextColor: Int = Color.GRAY // Default or initial value

    private var isInitialFragmentLoaded = false


    companion object {
        var TAG = DashboardFragment::class.java.simpleName
        private const val SHORTCUT_ID = "vih_chatbot_shortcut" // Unique ID for your shortcut
        private const val MAX_REDIRECTS = 5 // Maximum number of redirects to follow

        fun getInstance(hashCode: String, phoneNumber: String): DashboardFragment {
            val fragment = DashboardFragment()
            val args = Bundle()
            args.putString(AppConstants.HASHCODE_EXTRA, hashCode)
            args.putString(AppConstants.PHONENUMBER, phoneNumber)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is StringCommunicator) {
            communicator = context
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun initViewModels() {
        homeViewModel = getViewModel(
            fragment = activity as BaseActivity,
            viewModel = HomeViewModel(activity as BaseActivity),
            className = HomeViewModel::class.java
        )

        profileViewModel = getViewModel(
            fragment = activity as BaseActivity,
            viewModel = ProfileViewModel(activity as BaseActivity),
            className = ProfileViewModel::class.java
        )



        arguments?.getString(AppConstants.HASHCODE_EXTRA)?.let { hashCode ->
            homeViewModel.getSdkFeatures(true, hashCode)
        }
    }

    override fun onViewClick(view: View?) {
//        updateBottomNavigationColors()
    }

    override fun initView(view: View) {
        binding.edtSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                communicator?.sendData(s.toString().trim())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                fcmToken = task.result
            } else {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
            }
            // Refresh the user session independently of getSdkFeatures. Previously this
            // was gated behind a successful SDK-features response, so a failing (e.g.
            // 404) getSdkFeatures call silently skipped the session refresh and left
            // Discover/Chat unauthenticated. Run it here regardless of the FCM result.
            refreshUserSession()
        }

        activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        onThemeChanged(
            DynamicThemeManager.getPrimaryColor(),
            DynamicThemeManager.getSecondaryColor(),
            DynamicThemeManager.getPrimaryTextColor(),
            DynamicThemeManager.getSecondaryTextColor(),
            DynamicThemeManager.getHeaderColor(),
            DynamicThemeManager.getDefaultTextColor()
        )

        setClickOnSearchButton()
        setClickOnBackButton()
        setDashBoardClBackgroundColor()
    }

    /**
     * Re-establishes the user session by calling signup-login with the stored phone +
     * hashcode. Kept independent of getSdkFeatures so a non-critical SDK-features failure
     * can never block authentication for the Discover/Chat tabs.
     */
    private fun refreshUserSession() {
        val phoneNumber = arguments?.getString(AppConstants.PHONENUMBER) ?: return
        val hashCode = arguments?.getString(AppConstants.HASHCODE_EXTRA) ?: return
        val profileRequest = UserProfileRequest(
            phoneNumber,
            hashCode,
            fcm_token = fcmToken ?: ""
        )
        homeViewModel.getUserProfile(false, profileRequest)
    }

    override fun setListeners() {
        // Implementation not provided in the original code
    }

    override fun setObservers() {
        homeViewModel.sdkFeatureLiveData.observe(viewLifecycleOwner) { features ->
            if (features != null) {

                if (prefs.isSDK) {
                    DynamicThemeManager.setColorsFromApi(
                        requireContext(),
                        features.style_primary_color,
                        features.style_secondary_color,
                        features.style_accent_color,
                        features.style_font_color,
                        "#F7F7F7",
                        ContextCompat.getColor(requireContext(), R.color.black).toHex(),
                    )
                } else {
                    DynamicThemeManager.setColorsFromApi(
                        requireContext(),
                        ContextCompat.getColor(requireContext(), R.color.primarycolor).toHex(),
                        ContextCompat.getColor(requireContext(), R.color.secondarycolor).toHex(),
                        ContextCompat.getColor(requireContext(), R.color.primarytextcolor).toHex(),
                        ContextCompat.getColor(requireContext(), R.color.secondarytextcolor)
                            .toHex(),
                        "#F7F7F7",
                        ContextCompat.getColor(requireContext(), R.color.black).toHex()
                    )
                }
                checkAndPromptForShortcut(
                    features.chat_boat_logo ?: "",
                    features.chat_boat_name ?: ""
                )
                prefs.vihSettings = Gson().toJson(features)
            }

            this.onThemeChanged(
                DynamicThemeManager.getPrimaryColor(),
                DynamicThemeManager.getSecondaryColor(),
                DynamicThemeManager.getPrimaryTextColor(),
                DynamicThemeManager.getSecondaryTextColor(),
                DynamicThemeManager.getHeaderColor(),
                DynamicThemeManager.getDefaultTextColor()
            )
        }

        profileViewModel.userProfileLogout.observe(viewLifecycleOwner) { response ->
            prefs.clearAllPreferences()
            FirebaseMessaging.getInstance().deleteToken()
            requireActivity().finish()
        }

        homeViewModel.userprofileLiveData.observe(viewLifecycleOwner) { profile ->
            if (profile != null) {
                binding.clDashBoardMain.visibility = View.VISIBLE
                prefs.userProfile = Gson().toJson(profile.data.user)
                prefs.accessToken = profile.data.access_token
                prefs.refreshToken = profile.data.refresh

                // Auth is now available — flush any FCM token that arrived before login.
                // Safe no-op if the token is already registered (architecture §3.3).
                com.vihmessenger.vihchatbot.services.DeviceTokenRegistrar
                    .registerCachedTokenIfNeeded(requireContext())

                if (!isInitialFragmentLoaded) {
                    setChatListFragment() // This will set bottomNav to R.id.navChat
                    isInitialFragmentLoaded = true
                } else {
                    Log.d(TAG, "UserProfile updated, but initial fragment was already loaded.")
                }
            }
        }

        homeViewModel.fragmentTransitionLiveData.observe(viewLifecycleOwner) {
            setDiscoverFragment()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initView() { // This is a duplicate of the initView above, ensure one is correct or merge
        setClickOnSearchButton()
        setClickOnBackButton()
        setDashBoardClBackgroundColor()
    }

    private fun setupBottomNavigation() {
        _binding?.let { binding ->
            regularFont = ResourcesCompat.getFont(requireContext(), R.font.rubik_regular)!!
            semiBoldFont = ResourcesCompat.getFont(requireContext(), R.font.rubik_semibold)!!

            binding.bottomNavigation.apply {
                isClickable = true
                ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                    v.setPadding(
                        v.paddingLeft,
                        v.paddingTop,
                        v.paddingRight,
                        insets.systemWindowInsetBottom // This was systemWindowInsetBottom, ensure it's what you need
                    )
                    insets
                }
            }

            binding.bottomNavigation.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.navDiscover -> {
                        handleDiscoverSelection()
                        true
                    }
                    R.id.navChat -> {
                        handleChatsSelection()
                        true
                    }

                    R.id.navSettings -> {
                        handleSettingsSelection()
                        true
                    }

                    else -> false
                }
            }

            if (binding.bottomNavigation.selectedItemId == 0) { // Check if not already set
                binding.bottomNavigation.selectedItemId = R.id.navChat // Or your default
            }

        } ?: Log.e(TAG, "setupBottomNavigation: Binding is null")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        val windowInsetsController = ViewCompat.getWindowInsetsController(binding.root)
        windowInsetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setCustomStatusBar() // Call this after window flags are set

        setupBottomNavigation()

    }

    override fun onResume() {
        super.onResume()
        setCustomStatusBar()
    }


    private fun setCustomStatusBar() {
        if (!isAdded || activity == null) {
            Log.w(TAG, "setCustomStatusBar: Fragment not added or activity is null.")
            return
        }

        val window: Window = requireActivity().window
        val view: View = window.decorView // Use decorView for reliable access
        // It's generally safer to get colors dynamically if they can change,
        // or ensure R.color.status_bar_grey is always the correct one.
        val targetColor = ContextCompat.getColor(requireContext(), R.color.status_bar_grey)


        // These flags are important for custom status bar color
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)


        // Post the color change to ensure the view is laid out
        view.post {
            window.statusBarColor = targetColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val controller = WindowInsetsControllerCompat(window, view)
                // Determine if icons should be light or dark based on status bar color's luminance
                val isLightColor = ColorUtils.calculateLuminance(targetColor) > 0.5
                // Corrected logic:
                // true for dark icons on light background, false for light icons on dark background
                controller.isAppearanceLightStatusBars = isLightColor
            }
        }

    }

    private fun handleDiscoverSelection() {
        binding.edtSearch.text?.clear()
        communicator?.sendData("")
        hideKeyboard()
        binding.lyTopBar.btnFilter.visibility = View.VISIBLE
        setTopBarTitle("Discover")

        val selectedFilters = homeViewModel.getSelectedIndustries()
        updateIndustryFilterUI(selectedFilters)
        binding.lyTopBar.btnClearFilters.visibility =
            if (selectedFilters.isNotEmpty()) View.VISIBLE else View.GONE
        binding.flDashboardSettings.visibility = View.INVISIBLE

        arguments?.getString(AppConstants.HASHCODE_EXTRA)?.let { hashCode ->
            discoverFragment = DiscoverFragment.getInstance(hashCode)
            childFragmentManager.beginTransaction()
                .replace(R.id.flDashboard, discoverFragment!!)
                .commit()

            discoverFragment?.receiveIndustryData(selectedFilters)
        }

        requireActivity().statusBarSecondaryColor()
        setClSearchVisibility()
    }

    private fun handleChatsSelection() {
        binding.edtSearch.text?.clear()
        communicator?.sendData("")
        hideKeyboard()
        binding.lyTopBar.btnFilter.visibility = View.GONE
        binding.lyTopBar.btnClearFilters.visibility = View.GONE
        setTopBarTitle("Chats   ") // Note: extra spaces here

        binding.flDashboardSettings.visibility = View.INVISIBLE

        arguments?.getString(AppConstants.HASHCODE_EXTRA)?.let { hashCode ->
            mainFragment = ChatListFragment.getInstance(hashCode)
            childFragmentManager.beginTransaction()
                .replace(R.id.flDashboard, mainFragment!!)
                .commit()
        }

        requireActivity().statusBarSecondaryColor()
        setClSearchVisibility()
    }

    private fun handleSettingsSelection() {
        binding.flDashboardSettings.visibility = View.VISIBLE
        settingFragment = SettingFragment.getInstance()

        childFragmentManager.beginTransaction()
            .replace(R.id.flDashboardSettings, settingFragment!!)
            .addToBackStack(null) // Consider if this is always needed
            .commit()
        binding.flDashboardSettings.isClickable = true // Ensure this is intended
        binding.flDashboardSettings.isFocusable = true // Ensure this is intended
    }

    private fun setDashBoardClBackgroundColor() {
        requireActivity().statusBarSecondaryColor()
    }

    private fun setClSearchVisibility() {
        if (binding.clSearch.isVisible) {
            binding.clSearch.visibility = View.GONE
            binding.lyTopBar.root.visibility = View.VISIBLE
        }
    }

    private fun setTopBarTitle(title: String) {
        binding.lyTopBar.tvTopBarTitle.text = title

        if (prefs.isSDK) {
            binding.lyTopBar.ivback.visibility = View.VISIBLE
        } else {
            binding.lyTopBar.ivback.visibility = View.GONE
        }

        binding.lyTopBar.ivback.setOnClickListener {
            requireActivity().finish()
        }


        if (getVihSettings() != null && !getVihSettings()?.style_primary_color.isNullOrEmpty()) {
            // Original comment: _viewBinder!!.lyTopBar.tvTopBarTitle.setVihTextColor()
            // If you have a custom extension `setVihTextColor`, you might want to use it here.
            // For now, applying default text color or primary color based on theme.
            binding.lyTopBar.tvTopBarTitle.setTextColor(currentDefaultTextColor) // Or currentPrimaryColor
        } else {
            // Fallback gradient if no theme color is set
            val shader = LinearGradient(
                10f,
                20f,
                10f,
                binding.lyTopBar.tvTopBarTitle.textSize,
                Color.parseColor("#9C15F7"),
                Color.parseColor("#0049E6"),
                Shader.TileMode.CLAMP
            )
            binding.lyTopBar.tvTopBarTitle.paint.shader = shader
        }
    }

    private fun setDiscoverFragment() {
        if (_binding != null) { // Ensure binding is not null
            binding.bottomNavigation.selectedItemId = R.id.navDiscover
        }
    }

    private fun setChatListFragment() {
        binding.lyTopBar.btnFilter.visibility = View.GONE
        binding.lyTopBar.btnClearFilters.visibility = View.GONE
        setTopBarTitle("Chats") // Removed extra spaces

        arguments?.getString(AppConstants.HASHCODE_EXTRA)?.let { hashCode ->
            mainFragment = ChatListFragment.getInstance(hashCode)
            childFragmentManager.beginTransaction()
                .replace(R.id.flDashboard, mainFragment!!)
                .commit()
        }
        if (_binding != null) { // Ensure binding is not null
            binding.bottomNavigation.selectedItemId = R.id.navChat
        }
    }

    private fun setClickOnSearchButton() {
        binding.lyTopBar.ivSearch.setOnClickListener {
            binding.lyTopBar.root.visibility = View.INVISIBLE

            val animation: Animation =
                AnimationUtils.loadAnimation(context, R.anim.anim_right_to_left)
            binding.clSearch.startAnimation(animation)
            binding.clSearch.visibility = View.VISIBLE
        }
    }

    private fun setClickOnBackButton() {
        binding.ibBackButton.setOnClickListener {
            binding.lyTopBar.root.visibility = View.VISIBLE
            binding.clSearch.visibility = View.GONE
            binding.edtSearch.text?.clear()
            communicator?.sendData("")
            hideKeyboard()
        }

        binding.lyTopBar.btnFilter.setOnClickListener {
            val selectedIndustries = homeViewModel.getSelectedIndustries()
            val bottomSheet =
                IndustryFiltersBottomSheetFragment.newInstance(selectedIndustries, isFilterClear)
            bottomSheet.setOnIndustrySelectionListener(object :
                IndustryFiltersBottomSheetFragment.OnIndustrySelectionListener {
                override fun onIndustriesSelected(industries: String) {
                    homeViewModel.setSelectedIndustries(industries)
                    updateIndustryFilterUI(industries)
                    isFilterClear = false
                }
            })
            bottomSheet.show(childFragmentManager, "IndustryFilters")
        }

        binding.lyTopBar.btnClearFilters.setOnClickListener {
            isFilterClear = true
            binding.lyTopBar.btnClearFilters.visibility = View.GONE
            homeViewModel.setSelectedIndustries("") // Clear selected industries in view model
            communicator?.sendIndustryData("") // Send empty filter to trigger new API request
        }
    }

    private fun updateIndustryFilterUI(industries: String) {
        if (industries.isNotEmpty()) {
            val formattedIndustries = industries.replaceFirst("^,".toRegex(), "")
            communicator?.sendIndustryData(formattedIndustries)
            binding.lyTopBar.btnClearFilters.visibility = View.VISIBLE
        } else {
            communicator?.sendIndustryData("")
            binding.lyTopBar.btnClearFilters.visibility = View.GONE
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(requireActivity()).apply {
            setTitle("Log out")
            setMessage("Are you sure, you want to log out?")
            setPositiveButton("Yes") { _, _ ->
                profileViewModel.logoutProfile(false, AppController.Companion.prefs?.refreshToken.toString())

            }
            setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            setCancelable(false)
            show()
        }
    }

    override fun onBackPressed(): Boolean {
        return when {
            binding.clSearch.isVisible -> {
                binding.lyTopBar.root.visibility = View.VISIBLE
                binding.clSearch.visibility = View.GONE
                binding.edtSearch.clearFocus()
                binding.edtSearch.text?.clear()
                communicator?.sendData("")
                true
            }

            else -> false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun receiveData(data: String) {
        val currentFragment = childFragmentManager.findFragmentById(R.id.flDashboard)
        when (currentFragment) {
            is DiscoverFragment -> currentFragment.receiveData(data)
            is ChatListFragment -> currentFragment.receiveData(data)
        }
    }

    fun receiveIndustryData(filterStr: String) {
        val currentFragment = childFragmentManager.findFragmentById(R.id.flDashboard)
        if (currentFragment is DiscoverFragment) {
            currentFragment.receiveIndustryData(filterStr)
        }
    }

    override fun applyTheme(primaryColor: Int) {
        super.applyTheme(primaryColor)
        Log.e(TAG, "applyTheme: ${primaryColor}")
    }

    override fun onThemeChanged(
        primaryColor: Int,
        secondaryColor: Int,
        primaryTextColor: Int,
        secondaryTextColor: Int,
        headerColor: Int,
        defaultTextColor: Int
    ) {
        super.onThemeChanged(
            primaryColor,
            secondaryColor,
            primaryTextColor,
            secondaryTextColor,
            headerColor,
            defaultTextColor
        )

        currentPrimaryColor = primaryColor
        currentDefaultTextColor = defaultTextColor

        // Ensure binding is not null before proceeding
        _binding?.let { safeBinding ->
            with(safeBinding) {
                // Background update as before
                clDashBoardMain.setBackgroundColor(headerColor)
                clTopBar.setBackgroundColor(headerColor)
                lyTopBar.root.setBackgroundColor(headerColor)

                // Set text colors
                lyTopBar.tvTopBarTitle.setTextColor(defaultTextColor)
                lyTopBar.btnClearFilters.setTextColor(primaryColor)
                lyTopBar.ivback.setColorFilter(defaultTextColor, PorterDuff.Mode.SRC_IN)
                // Set icon tints — use the brand primary (purple) so the glyphs stand out
                // against the white circle background set below.
                lyTopBar.btnFilter.imageTintList = ColorStateList.valueOf(primaryColor)
                lyTopBar.ivSearch.imageTintList = ColorStateList.valueOf(primaryColor)

                val strokeColor = defaultTextColor.withAlpha(25) // 10% of 255 ≈ 25
                val dynamicBackground = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    gradientType = GradientDrawable.LINEAR_GRADIENT
                    orientation = GradientDrawable.Orientation.TL_BR
                    colors =
                        intArrayOf(Color.WHITE, Color.WHITE, Color.WHITE) // Consider theming this
                    setStroke(1.dpToPx(), strokeColor)
                }

                lyTopBar.btnFilter.background = dynamicBackground
                lyTopBar.ivSearch.background = dynamicBackground

                ibBackButton.setColorFilter(defaultTextColor, PorterDuff.Mode.SRC_IN)

                val drawable =
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_search)?.mutate()
                drawable?.setTint(defaultTextColor)
                edtSearch.setHintTextColor(defaultTextColor)
                edtSearch.setTextColor(defaultTextColor)
                edtSearch.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
                val background = edtSearch.background as? GradientDrawable
                background?.setColor(Color.WHITE) // Consider theming this
                setCustomStatusBar()

                val filterCloseIcon =
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_close)
                        ?.mutate()
                filterCloseIcon?.setTint(primaryColor)
                lyTopBar.btnClearFilters.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    null,
                    filterCloseIcon,
                    null
                )
                val backgroundDrawable = lyTopBar.btnClearFilters.background
                if (backgroundDrawable is GradientDrawable) {
                    val strokeWidthInPx = try {
                        resources.getDimensionPixelSize(R.dimen._1sdp)
                    } catch (e: Exception) {
                        (1 * resources.displayMetrics.density).toInt()
                    }
                    backgroundDrawable.setStroke(strokeWidthInPx, primaryColor)
                } else {
                    Log.w(
                        TAG,
                        "btnClearFilters background is not a GradientDrawable, cannot set stroke color dynamically."
                    )
                }
                val unselectedIconColor = ColorUtils.setAlphaComponent(
                    defaultTextColor,
                    178
                )
                val unselectedTextColor = ColorUtils.setAlphaComponent(
                    defaultTextColor,
                    178
                )


                val iconTintColorStateList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    ),
                    intArrayOf(
                        primaryColor,
                        unselectedIconColor
                    )
                )
                val textColorStateList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    ),
                    intArrayOf(
                        defaultTextColor, // Or primaryColor if selected text should be primary
                        unselectedTextColor
                    )
                )

                bottomNavigation.let { navView ->
                    navView.itemIconTintList = iconTintColorStateList
                    navView.itemTextColor = textColorStateList
                }
            }
        }
    }


    private fun checkAndPromptForShortcut(chatBoatLogoUrl: String, chatBotName: String) {
        val isShortcutAdded = prefs.shortcutAddedSuccessfully
        Log.i(
            TAG,
            "checkAndPromptForShortcut: Current value of prefs.shortcutAddedSuccessfully at method start IS: $isShortcutAdded"
        )

        if (isShortcutAdded) {
            Log.i(TAG, "Shortcut ALREADY ADDED (according to prefs). No prompt needed.")
            return
        }

        Log.d(
            TAG,
            "Shortcut NOT YET ADDED (according to prefs). Proceeding with prompt/denial logic."
        )

        val shouldPromptNow = if (prefs.shortcutDeniedByUser) {
            prefs.shortcutDeniedCount += 1
            Log.d(
                TAG,
                "Shortcut was denied by user. Denied count incremented to: ${prefs.shortcutDeniedCount}"
            )
            if (prefs.shortcutDeniedCount >= 10) {
                Log.i(TAG, "Denied 10 times. Will attempt to prompt for shortcut again.")
                prefs.shortcutDeniedCount = 0 // Reset counter for next cycle of denials
                true
            } else {
                Log.d(
                    TAG,
                    "Shortcut denied previously. Count: ${prefs.shortcutDeniedCount}/10. Not prompting yet."
                )
                false
            }
        } else {
            prefs.shortcutPromptCount += 1
            Log.d(
                TAG,
                "Shortcut not denied by user. Prompt count incremented to: ${prefs.shortcutPromptCount}"
            )
            if (prefs.shortcutPromptCount >= 5) { // Changed from 5 to 2 for easier testing, revert if needed
                Log.i(TAG, "Prompt count reached. Will attempt to prompt for shortcut.")
                prefs.shortcutPromptCount = 0 // Reset counter for next cycle of prompts
                true
            } else {
                Log.d(
                    TAG,
                    "Shortcut prompt count: ${prefs.shortcutPromptCount}/2. Not prompting yet."
                )
                false
            }
        }

        if (shouldPromptNow) {
            if (!isAdded || activity == null) {
                Log.w(
                    TAG,
                    "Cannot proceed with shortcut prompt: Fragment not added or activity is null."
                )
                return
            }
            Log.d(TAG, "Proceeding to load image for shortcut dialog. URL: $chatBoatLogoUrl")
            viewLifecycleOwner.lifecycleScope.launch { // Launches on Main dispatcher by default
                val shortcutBitmap: Bitmap? = if (chatBoatLogoUrl.isNotBlank()) {
                    withContext(Dispatchers.IO) { // Switch to IO for network call
                        Log.d(TAG, "Loading image from URL: $chatBoatLogoUrl")
                        loadImageFromUrl(chatBoatLogoUrl, 0)
                    }
                } else {
                    Log.w(TAG, "chatBoatLogoUrl is blank. No custom icon to load.")
                    null
                }

                // Back on Main thread here
                if (!isAdded || activity == null) { // Re-check fragment state
                    Log.w(
                        TAG,
                        "Fragment became detached or activity became null while loading image. Aborting dialog."
                    )
                    return@launch
                }

                if (shortcutBitmap != null) {
                    Log.d(TAG, "Successfully loaded bitmap for shortcut icon.")
                } else {
                    Log.w(
                        TAG,
                        "Failed to load bitmap or URL was blank. Dialog will use default icon logic."
                    )
                }
                showAddShortcutDialog(
                    shortcutBitmap,
                    chatBoatLogoUrl,
                    chatBotName
                ) // Pass bitmap (can be null)
            }
        }
    }


    private fun showAddShortcutDialog(
        shortcutBitmap: Bitmap?,
        originalUrl: String,
        chatBotName: String
    ) { // Added originalUrl for logging if needed
        if (!isAdded || activity == null) {
            Log.w(
                TAG,
                "showAddShortcutDialog: Fragment not added or activity is null. Cannot show dialog."
            )
            return
        }

        AlertDialog.Builder(requireActivity()).apply {
            setTitle("Add Shortcut")
            setMessage("Would you like to add a shortcut of $chatBotName on your Home Screen for quick access?")
            setPositiveButton("Yes") { _, _ ->
                createHomeScreenShortcut(
                    shortcutBitmap,
                    originalUrl,
                    chatBotName
                ) // Pass the pre-loaded bitmap (or null)
                prefs.shortcutDeniedByUser = false // User is acting on a prompt, so reset denial
                prefs.shortcutDeniedCount = 0      // Reset denial count
            }
            setNegativeButton("No") { dialog, _ ->
                prefs.shortcutDeniedByUser = true
                prefs.shortcutDeniedCount =
                    0 // Reset to start counting denials for the next 10 attempts
                Toast.makeText(context, "Okay, we won't ask for a while.", Toast.LENGTH_SHORT)
                    .show()
                dialog.dismiss()
            }
            setCancelable(false)
            show()
        }
    }

    private fun createHomeScreenShortcut(
        shortcutBitmap: Bitmap?,
        chatBoatLogoUrlForLogging: String,
        chatBotName: String
    ) {
        if (!isAdded || activity == null) {
            Log.w(TAG, "createHomeScreenShortcut: Fragment not added or activity is null.")
            return
        }
        val context = requireContext()

        val currentPhoneNumber = arguments?.getString(AppConstants.PHONENUMBER)
        val currentHashCode = arguments?.getString(AppConstants.HASHCODE_EXTRA)

        if (currentPhoneNumber == null || currentHashCode == null) {
            Log.e(
                TAG,
                "Cannot create shortcut: Essential data (phone/hashcode) missing from fragment arguments."
            )
            Toast.makeText(
                context,
                "Error: Could not create shortcut due to missing info.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            // --- MODIFIED PART: Make the Intent for the broadcast receiver EXPLICIT ---
            val shortcutIntentForActivity = Intent(context, DashBoardActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(AppConstants.PHONENUMBER, currentPhoneNumber)
                putExtra(AppConstants.HASHCODE_EXTRA, currentHashCode)
                putExtra("launched_from_shortcut", true)
            }

            // This intent is for the BroadcastReceiver
            val pinnedShortcutCallbackIntent =
                Intent(context, ShortcutPinnedReceiver::class.java).apply {
                    // You can still set an action if your receiver uses it for an additional check,
                    // but the primary targeting is now by class.
                    action = AppConstants.ACTION_SHORTCUT_PINNED
                    // If the receiver needs any data from this callback, add it as extras here.
                    // For example: putExtra("shortcut_id", shortcutInfo.id) (if shortcutInfo is built before this)
                }
            // --- END MODIFIED PART ---

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                val iconToUse: IconCompat = if (shortcutBitmap != null) {
                    Log.d(TAG, "Using provided Bitmap for shortcut icon.")
                    IconCompat.createWithBitmap(shortcutBitmap)
                } else {
                    Log.w(
                        TAG,
                        "Provided Bitmap was null (URL was '$chatBoatLogoUrlForLogging' or failed to load). Using default app icon."
                    )
                    IconCompat.createWithResource(
                        context,
                        R.drawable.placeholder
                    ) // Ensure this is your app's launcher icon
                }

                // Build shortcutInfo using shortcutIntentForActivity
                val shortcutInfo = ShortcutInfoCompat.Builder(
                    context,
                    SHORTCUT_ID + "_" + System.currentTimeMillis()
                )
                    .setShortLabel(chatBotName)
                    .setIcon(iconToUse)
                    .setIntent(shortcutIntentForActivity) // Intent to launch the app
                    .build()

                // Create PendingIntent using pinnedShortcutCallbackIntent (for the receiver)
                val successCallback = PendingIntent.getBroadcast(
                    context,
                    0, // Request code
                    pinnedShortcutCallbackIntent, // Explicit intent for the receiver
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    val pinRequestSent = ShortcutManagerCompat.requestPinShortcut(
                        context,
                        shortcutInfo,
                        successCallback.intentSender
                    )
                    if (pinRequestSent) {
                        Log.i(
                            TAG,
                            "ShortcutManagerCompat.requestPinShortcut call SUCCEEDED. Waiting for broadcast to confirm addition."
                        )
                    } else {
                        Log.e(
                            TAG,
                            "ShortcutManagerCompat.requestPinShortcut call FAILED. Launcher might not support or user denied at system level."
                        )
                        Toast.makeText(
                            context,
                            "Your launcher did not accept the shortcut request.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException requesting shortcut: ${e.message}", e)
                    Toast.makeText(
                        context,
                        "Could not create shortcut due to security restrictions.",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Error requesting shortcut: ${e.message}", e)
                    Toast.makeText(context, "Error creating shortcut.", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Log.w(TAG, "Cannot create shortcut: Pinning not supported by launcher.")
            Toast.makeText(
                context,
                "Unable to create shortcut on this launcher.",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    // Updated helper function to load Bitmap from URL with redirect handling
    private suspend fun loadImageFromUrl(imageUrl: String, redirectCount: Int): Bitmap? {
        if (redirectCount > MAX_REDIRECTS) {
            Log.e(
                TAG,
                "loadImageFromUrl: Exceeded maximum redirect limit ($MAX_REDIRECTS) for URL: $imageUrl"
            )
            return null
        }

        if (imageUrl.isBlank() || !android.util.Patterns.WEB_URL.matcher(imageUrl).matches()) {
            Log.e(TAG, "Invalid URL for image: $imageUrl")
            return null
        }

        // This function is already designed to be called from a Dispatchers.IO context
        // No need to wrap withContext(Dispatchers.IO) here again if called from one.
        var bitmap: Bitmap? = null
        var connection: HttpURLConnection? = null
        try {
            Log.d(
                TAG,
                "loadImageFromUrl (Attempt ${redirectCount + 1}): Starting download from $imageUrl"
            )
            val url = URL(imageUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false // We will handle redirects manually
            connection.connectTimeout = 15000 // 15 seconds
            connection.readTimeout = 15000    // 15 seconds
            connection.doInput = true
            connection.connect()

            val responseCode = connection.responseCode
            Log.d(TAG, "loadImageFromUrl: Response code $responseCode for $imageUrl")

            when (responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val inputStream = connection.inputStream
                    // Consider adding options for BitmapFactory to prevent OOM for very large images if necessary
                    // val options = BitmapFactory.Options().apply { inSampleSize = 2 } // Example
                    bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    Log.d(TAG, "loadImageFromUrl: Bitmap decoded successfully from $imageUrl")
                }

                HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, HttpURLConnection.HTTP_SEE_OTHER -> {
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect() // Disconnect current connection before recursing
                    if (newUrl != null) {
                        Log.d(TAG, "loadImageFromUrl: Redirected to $newUrl from $imageUrl")
                        // Recursively call loadImageFromUrl with the new URL and incremented redirect count
                        return loadImageFromUrl(
                            newUrl,
                            redirectCount + 1
                        ) // Return the result of recursive call
                    } else {
                        Log.e(
                            TAG,
                            "loadImageFromUrl: Redirect response but no Location header for $imageUrl"
                        )
                    }
                }

                else -> {
                    Log.e(
                        TAG,
                        "loadImageFromUrl: Server responded with code $responseCode for $imageUrl"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "loadImageFromUrl: Error downloading image from $imageUrl - ${e.message}",
                e
            )
        } finally {
            connection?.disconnect()
        }
        return bitmap // Return the bitmap (or null if failed/redirected and handled by recursive return)
    }
}

interface OnBackPressedListener {
    fun onBackPressed(): Boolean
}
