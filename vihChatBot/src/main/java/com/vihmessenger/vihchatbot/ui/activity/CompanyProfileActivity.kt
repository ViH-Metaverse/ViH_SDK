package com.vihmessenger.vihchatbot.ui.activity

import BaseActivity
import com.vihmessenger.vihchatbot.utils.CustomImageLoader
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.constants.AppConstants
import com.vihmessenger.vihchatbot.data.model.EnterPriseModel
import com.vihmessenger.vihchatbot.databinding.ActivityCompanyProfileBinding

class CompanyProfileActivity : BaseActivity() {

    private val _viewBinder by lazy { ActivityCompanyProfileBinding.inflate(layoutInflater) }

    private var channel: EnterPriseModel? = null // Moved from top-level property

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

    override fun initViewModels() {}

    override fun initView() {
        Log.e(TAG, "initView: ${channel}")
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
    }

    override fun setObservers() {}

    override fun setListeners() {
        _viewBinder.ivBack.setOnClickListener {
            finish()
        }

        _viewBinder.companyWebAddress.setOnClickListener {
            channel?.comp_website?.takeIf { it.isNotBlank() }?.let { url ->
                var websiteUrl = url
                if (!websiteUrl.startsWith("http://") && !websiteUrl.startsWith("https://")) {
                    websiteUrl = "http://$websiteUrl"
                }
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl))
                    startActivity(Intent.createChooser(intent, "Open with"))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        this,
                        "No application can handle this request. Please install a web browser.",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e(TAG, "ActivityNotFoundException for web URL: $websiteUrl", e)
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
                    Log.e(TAG, "ActivityNotFoundException for email: $emailAddress", e)
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
                    Log.e(TAG, "ActivityNotFoundException for dialer: $phone", e)
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
                    Log.e(TAG, "ActivityNotFoundException for map address: $address", e)
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