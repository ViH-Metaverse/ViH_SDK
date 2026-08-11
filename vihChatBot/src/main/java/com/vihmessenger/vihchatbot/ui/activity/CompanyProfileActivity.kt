package com.vihmessenger.vihchatbot.ui.activity

import BaseActivity
import com.vihmessenger.vihchatbot.utils.CustomImageLoader
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.vihmessenger.vihchatbot.utils.VihLog
import android.view.View
import android.widget.Toast
import com.vihmessenger.vihchatbot.AppController
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.constants.AppConstants
import com.vihmessenger.vihchatbot.data.model.EnterPriseModel
import com.vihmessenger.vihchatbot.databinding.ActivityCompanyProfileBinding
import com.vihmessenger.vihchatbot.viewmodel.ProfileViewModel
import com.vihmessenger.vihchatbot.utils.ExternalUrl

class CompanyProfileActivity : BaseActivity() {

    private val _viewBinder by lazy { ActivityCompanyProfileBinding.inflate(layoutInflater) }

    private var channel: EnterPriseModel? = null // Moved from top-level property
    private lateinit var profileViewModel: ProfileViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(_viewBinder.root)
        applyThemeAndSetupListeners()
        channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(AppConstants.CHANNEL_EXTRA, EnterPriseModel::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(AppConstants.CHANNEL_EXTRA) as? EnterPriseModel
        }

        initViewModels()
        initView()
        setObservers()
        setListeners()
    }

    override fun initViewModels() {
        profileViewModel = getViewModel(
            viewModel = ProfileViewModel(this), className = ProfileViewModel::class.java
        )
    }

    /** True when the user has opted OUT of promotional messages. Cascade per backend:
     *  prefer is_promotional_message_blocked; else derive from promotional_opt_in; else false. */
    private fun isPromotionalBlocked(enterprise: EnterPriseModel): Boolean =
        enterprise.is_promotional_message_blocked
            ?: enterprise.promotional_opt_in?.let { !it }
            ?: false

    /** Refresh the Block / Mute / Promotional row labels from the current [channel] flags. */
    private fun refreshStateRows() {
        val enterprise = channel ?: return
        _viewBinder.tvBlock.text =
            if (enterprise.is_blacklisted_by_user == true) "Unblock business" else "Block business"
        _viewBinder.tvMute.text =
            if (enterprise.is_muted_by_user == true) "Unmute notifications" else "Mute notifications"
        _viewBinder.tvPromotional.text =
            if (isPromotionalBlocked(enterprise)) "Opt-in to promotional messages"
            else "Opt-out from promotional messages"
    }

    /** Serialize the (possibly mutated) channel back so the launching screen refreshes in place. */
    private fun publishChannelResult() {
        setResult(RESULT_OK, Intent().apply {
            putExtra(AppConstants.CHANNEL_EXTRA, channel)
        })
    }

    override fun initView() {
        VihLog.e(TAG, "initView: ${channel}")
        channel?.let { enterprise ->
            val companyNameText = enterprise.comp_name ?: "N/A"
            val companyDescription = enterprise.displayNameModel?.description ?: "N/A"
            val companyLogo = if (enterprise.display_img.isNullOrBlank()) {
                enterprise.profile_picture
            } else {
                enterprise.display_img
            }

            // Header Info
            _viewBinder.tvCompanyName.text = enterprise.displayNameModel?.display_name ?: "N/A" // Header company name
            CustomImageLoader.loadImageView(
                imageView = _viewBinder.ivCompanyImage,
                url = companyLogo,
                name = companyNameText,
                onError = {
                    _viewBinder.ivCompanyImage.setImageResource(R.drawable.profile_placeholder)
                }
            )
            _viewBinder.tvCompanyDescription.text = companyDescription

            // Detailed Info
            _viewBinder.companyWebAddress.text =
                enterprise.comp_website?.takeIf { it.isNotBlank() } ?: "Not available"
            _viewBinder.companyEmailAddress.text =
                enterprise.email?.takeIf { it.isNotBlank() } ?: "Not available"

            val phoneToDisplay = enterprise.customercare?.takeIf { it.isNotBlank() }
                ?: enterprise.phone?.takeIf { it.isNotBlank() }
                ?: "Not available"
            _viewBinder.phoneNumber.text = phoneToDisplay

            _viewBinder.companyName.text = companyNameText // Company name in the details section
            _viewBinder.companyAddress.text =
                enterprise.comp_address?.takeIf { it.isNotBlank() } ?: "Not available"
        } ?: run {
            // Handle case where channel is null (e.g., display error or default values)
            _viewBinder.tvCompanyName.text = "Company Details Not Found"
            _viewBinder.tvCompanyDescription.text = ""
            _viewBinder.companyWebAddress.text = "Not available"
            _viewBinder.companyEmailAddress.text = "Not available"
            _viewBinder.phoneNumber.text = "Not available"
            _viewBinder.companyName.text = "Not available"
            _viewBinder.companyAddress.text = "Not available"
            // _viewBinder.ivCompanyImage.setImageResource(R.drawable.profile_placeholder)
        }
        refreshStateRows()
    }

    override fun setObservers() {
        profileViewModel.blacklistResultLiveData.observe(this) { blocked ->
            channel?.is_blacklisted_by_user = blocked
            refreshStateRows()
            publishChannelResult()
            Toast.makeText(
                this, if (blocked) "Business blocked" else "Business unblocked", Toast.LENGTH_SHORT
            ).show()
        }
        profileViewModel.muteResultLiveData.observe(this) { muted ->
            channel?.is_muted_by_user = muted
            refreshStateRows()
            publishChannelResult()
            Toast.makeText(
                this, if (muted) "Notifications muted" else "Notifications unmuted", Toast.LENGTH_SHORT
            ).show()
        }
        profileViewModel.promotionalResultLiveData.observe(this) { optIn ->
            // optIn and is_promotional_message_blocked are inverse — set both locally so the
            // read cascade stays consistent even if the backend returns stale session data.
            channel?.promotional_opt_in = optIn
            channel?.is_promotional_message_blocked = !optIn
            refreshStateRows()
            publishChannelResult()
            Toast.makeText(
                this,
                if (optIn) "Opted in to promotional messages" else "Opted out of promotional messages",
                Toast.LENGTH_SHORT
            ).show()
        }
        profileViewModel.enterpriseMutationError.observe(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun setListeners() {
        _viewBinder.ivBack.setOnClickListener {
            finish()
        }

        _viewBinder.rowBlock.setOnClickListener {
            val enterprise = channel ?: return@setOnClickListener
            profileViewModel.blacklistEnterprise(
                enterprise.id, blacklist = enterprise.is_blacklisted_by_user != true
            )
        }

        _viewBinder.rowMute.setOnClickListener {
            val enterprise = channel ?: return@setOnClickListener
            profileViewModel.muteEnterprise(
                enterprise.id, mute = enterprise.is_muted_by_user != true
            )
        }

        _viewBinder.rowPromotional.setOnClickListener {
            val enterprise = channel ?: return@setOnClickListener
            // If currently opted OUT (blocked), this action opts IN, and vice-versa.
            val optIn = isPromotionalBlocked(enterprise)
            val channelId = intent.getStringExtra(AppConstants.HASHCODE_EXTRA)
                ?.takeIf { it.isNotBlank() }
                ?: AppController.prefs?.hashcode.orEmpty()
            profileViewModel.updateEnterprisePromotional(enterprise.id, channelId, optIn)
        }

        _viewBinder.companyWebAddress.setOnClickListener {
            channel?.comp_website?.takeIf { it.isNotBlank() }?.let { url ->
                // SECURITY (VAPT F-16): this used to DOWNGRADE a scheme-less URL to http://.
                if (!ExternalUrl.open(this, url, useChooser = true)) {
                    Toast.makeText(
                        this,
                        "No application can handle this request. Please install a web browser.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        _viewBinder.companyEmailAddress.setOnClickListener {
            channel?.email?.takeIf { it.isNotBlank() }?.let { emailAddress ->
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:") // Only email apps should handle this
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
                }
                try {
                    startActivity(Intent.createChooser(intent, "Send email using..."))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, "No email client found.", Toast.LENGTH_LONG).show()
                    VihLog.e(TAG, "ActivityNotFoundException for email", e)
                }
            }
        }

        _viewBinder.phoneNumber.setOnClickListener {
            val numberToDial = channel?.customercare?.takeIf { it.isNotBlank() }
                ?: channel?.phone?.takeIf { it.isNotBlank() }

            numberToDial?.let { phone ->
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phone")
                }
                try {
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        this,
                        "No application can handle this request.",
                        Toast.LENGTH_LONG
                    ).show()
                    VihLog.e(TAG, "ActivityNotFoundException for dialer: ${VihLog.tail(phone)}", e)
                }
            }
        }

        _viewBinder.companyAddress.setOnClickListener {
            channel?.comp_address?.takeIf { it.isNotBlank() }?.let { address ->
                val encodedAddress = Uri.encode(address)
                val gmmIntentUri = Uri.parse("geo:0,0?q=$encodedAddress")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                try {
                    startActivity(mapIntent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, "No map application found.", Toast.LENGTH_LONG).show()
                    VihLog.e(TAG, "ActivityNotFoundException for map address: $address", e)
                }
            }
        }
    }

    override fun onViewClick(view: View?) {
    }

    override fun onThemeChanged(
        primaryColor: Int,
        secondaryColor: Int,
        primaryTextColor: Int,
        secondaryTextColor: Int,
        headerColor: Int,
        defaultTextColor: Int
    ) {
        _viewBinder.main.setBackgroundColor(headerColor)
        _viewBinder.linearMain.setBackgroundColor(headerColor)
        _viewBinder.ivBack.setColorFilter(defaultTextColor, PorterDuff.Mode.SRC_IN)
        _viewBinder.tvCompanyName.setTextColor(defaultTextColor)
        _viewBinder.tvCompanyDescription.setTextColor(defaultTextColor)
        _viewBinder.companyWebAddress.setTextColor(primaryColor)
        _viewBinder.companyEmailAddress.setTextColor(primaryColor)
    }
}