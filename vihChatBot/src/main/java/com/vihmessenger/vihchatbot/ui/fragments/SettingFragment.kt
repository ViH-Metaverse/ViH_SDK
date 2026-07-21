package com.vihmessenger.vihchatbot.ui.fragments

import BaseActivity
import BaseFragment
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import com.google.gson.Gson
import com.vihmessenger.vihchatbot.AppController.Companion.prefs
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.constants.BaseAPIConstants.BASE_URL
import com.vihmessenger.vihchatbot.databinding.FragmentSettingBinding
import com.vihmessenger.vihchatbot.ui.activity.EditSettingsActivity
import com.vihmessenger.vihchatbot.utils.CustomImageLoader
import com.vihmessenger.vihchatbot.utils.getProfileData
import com.vihmessenger.vihchatbot.utils.sharedPreference.Prefs
import com.vihmessenger.vihchatbot.viewmodel.ProfileViewModel


class SettingFragment : BaseFragment() {

    private lateinit var profileViewModel: ProfileViewModel

    // The hashkey awaiting backend subscription before it's persisted + applied.
    private var pendingHashkey: String? = null

    companion object {
        var TAG = SettingFragment::class.java.simpleName
        private const val TERMS_AND_CONDITIONS_URL =
            "https://vihmessenger.com/Vih-terms-and-conditions"
        private const val PRIVACY_POLICY_URL =
            "https://vihmessenger.com/Vih-privacy-policy"

        fun getInstance(): SettingFragment {
            return SettingFragment()
        }
    }

    private var _viewBinder: FragmentSettingBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _viewBinder = FragmentSettingBinding.inflate(inflater, container, false)

        Log.e(TAG, "onCreateView: ${Prefs.getInstance(requireContext()).isSDK}")

        if (Prefs.getInstance(requireContext()).isSDK) {
            _viewBinder?.apply {
                ivback.visibility = View.VISIBLE
                tvEdit.visibility = View.GONE
                rvDelete.visibility = View.GONE
                rvLogout.visibility = View.GONE
                rvChangeHashkey.visibility = View.GONE
                tvAccountSettings.visibility = View.GONE
                notificationTitle.visibility = View.GONE
                rlNotiifcationLayout.visibility = View.GONE
            }
        } else {
            _viewBinder?.apply {
                ivback.visibility = View.GONE
                tvEdit.visibility = View.VISIBLE
                rvDelete.visibility = View.VISIBLE
                rvLogout.visibility = View.VISIBLE
                rvChangeHashkey.visibility = View.VISIBLE
                tvAccountSettings.visibility = View.VISIBLE
                notificationTitle.visibility = View.VISIBLE
                rlNotiifcationLayout.visibility = View.VISIBLE
            }
        }

        _viewBinder!!.ivback.setOnClickListener {
            requireActivity().finish()
        }

        return _viewBinder?.root
    }

    // Inside your SettingFragment class

    private fun setProfileData() {
        // Get the instance of your SharedPreferences
        val localPrefs = Prefs.getInstance(requireContext())

        // Check if running in SDK mode to decide the data source
        if (localPrefs.isSDK) {
            // --- SDK Mode ---
            // Use data saved from the startSdk call

            // Set the user's name
            _viewBinder?.tvProfileName?.text = localPrefs.name ?: "User"
            _viewBinder?.tvProfilePhoneNumber?.text =
                localPrefs.phoneNumber?.let { "+$it" } ?: "Phone Number"

            // Get the image path (could be a URL or a local file path)
            val profilePath = localPrefs.userProfileUrl

            // Use the CustomImageLoader to load the profile picture
            CustomImageLoader.loadImageView(
                imageView = _viewBinder!!.ivProfileImage,
                url = profilePath, // Pass the path/URL here
                name = localPrefs.name, // Fallback to show initials on error
                applyCircleCrop = true, // Make the image circular
                progressBar = _viewBinder!!.progressBar,
                onError = {
                    // What to do if loading fails
                    _viewBinder!!.ivProfileImage.setImageResource(R.drawable.edit_profile_placeholder)
                }
            )

        } else {
            // --- Normal App Mode ---
            // Use your existing logic to get profile data
            val profileData = getProfileData()
            profileData?.let {
                _viewBinder?.tvProfileName?.text = it.full_name ?: "User"
                _viewBinder?.tvProfilePhoneNumber?.text = "+" + it.mobile ?: "Phone Number"

                // Construct the full URL for the profile image
                val imageUrl = BASE_URL + it.user_profile_image

                // Use the CustomImageLoader to load the profile picture
                CustomImageLoader.loadImageView(
                    imageView = _viewBinder!!.ivProfileImage,
                    url = imageUrl,
                    name = it.full_name, // Fallback for initials
                    applyCircleCrop = true,
                    progressBar = _viewBinder!!.progressBar,
                    skipCache = true, // Good for profile pics that might change
                    onError = {
                        _viewBinder!!.ivProfileImage.setImageResource(R.drawable.edit_profile_placeholder)
                    }
                )
            }
        }
    }


    override fun initViewModels() {
        profileViewModel = getFragmentViewModel(
            fragment = this,
            viewModel = ProfileViewModel(activity as BaseActivity),
            className = ProfileViewModel::class.java
        )
    }

    override fun onViewClick(view: View?) {

    }

    override fun onResume() {
        super.onResume()
        setProfileData()
    }

    override fun initView(view: View) {
        setProfileData()
        setClickListeners()
    }

    override fun setListeners() {
    }

    private fun setClickListeners() {
        _viewBinder?.apply {
            tvEdit.setOnClickListener {
                startActivity(Intent(requireActivity(), EditSettingsActivity::class.java))
            }

            linear.findViewById<View>(R.id.rvBlock).setOnClickListener {

            }

            linear.findViewById<View>(R.id.rvTerms).setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_AND_CONDITIONS_URL))
                try {
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        requireContext(),
                        "No application can handle this request. Please install a web browser.",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e(
                        TAG,
                        "ActivityNotFoundException for web URL: $TERMS_AND_CONDITIONS_URL",
                        e
                    )
                }
            }


            linear.findViewById<View>(R.id.rvPrivacy).setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
                try {
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        requireContext(),
                        "No application can handle this request. Please install a web browser.",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e(
                        TAG,
                        "ActivityNotFoundException for web URL: $PRIVACY_POLICY_URL",
                        e
                    )
                }
            }

            linear.findViewById<View>(R.id.rvDelete).setOnClickListener {
                // Show Delete Account Request dialog
            }

            linear.findViewById<View>(R.id.rvChangeHashkey).setOnClickListener {
                showChangeHashkeyDialog()
            }

            linear.findViewById<View>(R.id.rvLogout).setOnClickListener {
                showLogoutDialog()
            }
        }
    }

    override fun setObservers() {
        profileViewModel.userProfileUpdateLiveData.observe(this) {
            if (it != null) {
                prefs?.userProfile = Gson().toJson(it.data)
            }
        }
        profileViewModel.userProfileLogout.observe(viewLifecycleOwner) { response ->
            Log.e(TAG, "setObservers: ${response}")
            finishLogout()
        }
        // Logout is best-effort: even when the API call fails (server error, timeout,
        // or a successful-but-empty response that the repository reports as an error),
        // we must still clear the local session and route to login. Without this
        // observer the screen would freeze with the user neither logged out nor in.
        profileViewModel.userProfileLogoutError.observe(viewLifecycleOwner) { error ->
            Log.e(TAG, "logout error (clearing session anyway): $error")
            finishLogout()
        }
        // Channel switch: only persist the new hashkey + relaunch once the backend has
        // actually subscribed the account, so messages work on the new channel.
        profileViewModel.subscribeChannelLiveData.observe(viewLifecycleOwner) {
            val newHash = pendingHashkey ?: return@observe
            pendingHashkey = null
            prefs?.hashcode = newHash
            relaunchApp()
        }
        profileViewModel.subscribeChannelError.observe(viewLifecycleOwner) { error ->
            pendingHashkey = null
            Toast.makeText(requireContext(), error ?: "Couldn't switch channel", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Clears the local session and relaunches the app so the splash screen re-evaluates
     * auth state and routes to login. Used by both the success and error logout paths.
     */
    private fun finishLogout() {
        prefs?.clearAllPreferences()
        val activity = activity ?: return
        val launchIntent = activity.packageManager
            .getLaunchIntentForPackage(activity.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
            activity.startActivity(launchIntent)
        }
        activity.finish()
    }

    /**
     * Lets the user switch the channel hashkey the app talks to. Saves to Prefs and
     * relaunches so the dashboard (Discover/Chat) reloads against the new channel.
     */
    private fun showChangeHashkeyDialog() {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            setText(prefs?.hashcode ?: "")
            hint = "Channel hashkey"
            isSingleLine = true
        }
        val container = FrameLayout(ctx).apply {
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(ctx).apply {
            setTitle("Change Channel Hashkey")
            setMessage("Switch the channel this app talks to. The app will reload to apply.")
            setView(container)
            setPositiveButton("Save") { _, _ ->
                val newHash = input.text.toString().trim()
                when {
                    newHash.isEmpty() ->
                        Toast.makeText(ctx, "Hashkey can't be empty", Toast.LENGTH_SHORT).show()
                    newHash == prefs?.hashcode -> Unit // unchanged
                    else -> {
                        // Subscribe the account to the new channel first; only persist +
                        // relaunch on success (observer) so messages work on the new channel.
                        pendingHashkey = newHash
                        profileViewModel.subscribeChannel(true, newHash)
                    }
                }
            }
            setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            show()
        }
    }

    /** Relaunches the app (keeping the session) so changed config takes effect. */
    private fun relaunchApp() {
        val activity = activity ?: return
        val launchIntent = activity.packageManager
            .getLaunchIntentForPackage(activity.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
            activity.startActivity(launchIntent)
        }
        activity.finish()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(requireActivity()).apply {
            setTitle("Log out")
            setMessage("Are you sure, you want to log out?")
            setPositiveButton("Yes") { _, _ ->
                profileViewModel.logoutProfile(false, prefs?.refreshToken ?: "")
            }
            setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            setCancelable(false)
            show()
        }
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
            headerColor = headerColor,
            defaultTextColor = defaultTextColor
        )
        _viewBinder?.apply {
            main.setBackgroundColor(headerColor)
            linearMain.setBackgroundColor(headerColor)
            ivback.setColorFilter(defaultTextColor, PorterDuff.Mode.SRC_IN)
            tvProfileName.setTextColor(defaultTextColor)
            tvProfilePhoneNumber.setTextColor(defaultTextColor)
            tvEdit.setTextColor(primaryColor)

            val thumbStates = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ), intArrayOf(primaryColor, defaultTextColor)
            )

            val trackStates = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ), intArrayOf(defaultTextColor, primaryColor)
            )

            swtNotificationStatus.thumbTintList = thumbStates
            swtNotificationStatus.trackTintList = trackStates
        }
    }
}