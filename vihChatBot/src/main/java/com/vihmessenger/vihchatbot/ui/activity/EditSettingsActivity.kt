package com.vihmessenger.vihchatbot.ui.activity

import BaseActivity
import com.vihmessenger.vihchatbot.utils.CustomImageLoader
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.vihmessenger.vihchatbot.AppController.Companion.prefs
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.constants.BaseAPIConstants.BASE_URL
import com.vihmessenger.vihchatbot.data.model.UserProfileModel
import com.vihmessenger.vihchatbot.databinding.ActivityEditSettingsBinding
import com.vihmessenger.vihchatbot.utils.ProgressBarLoader
import com.vihmessenger.vihchatbot.utils.extensions.getAuthority
import com.vihmessenger.vihchatbot.utils.getProfileData
import com.vihmessenger.vihchatbot.viewmodel.ProfileViewModel
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditSettingsActivity : BaseActivity() {

    private val _viewBinder by lazy { ActivityEditSettingsBinding.inflate(layoutInflater) }
    private lateinit var profileViewModel: ProfileViewModel
    private val TAG = "EditSettingsActivity"

    private var selectedImageUri: Uri? = null
    private var currentPhotoPath: String? = null
    private var originalProfileData: UserProfileModel? = null

    // Define permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            // Camera permission granted, proceed with camera action
            openCameraWithPermission()
        } else {
            // Permission denied
            Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_LONG)
                .show()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                _viewBinder.ivProfileImage.setImageURI(uri)
                Log.d(TAG, "Gallery image selected: $uri")
            }
        }
    }

    // Modified camera launcher that properly handles the result
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            selectedImageUri?.let { uri ->
                Log.d(TAG, "Camera photo captured successfully: $uri")
                try {
                    // Load using Glide to handle potential file path issues
                    CustomImageLoader.loadImageView(
                        imageView = _viewBinder.ivProfileImage,
                        url = uri.toString(),
                        onError = {
                            _viewBinder.ivProfileImage.setImageResource(R.drawable.placeholder)
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading camera image: ${e.message}", e)
                    // Fallback method if Glide fails
                    try {
                        _viewBinder.ivProfileImage.setImageURI(null) // Clear the image view
                        _viewBinder.ivProfileImage.setImageURI(uri) // Set the URI again
                    } catch (e2: Exception) {
                        Log.e(TAG, "Fallback loading failed: ${e2.message}", e2)
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                    }
                }
            } ?: run {
                Log.e(TAG, "Camera returned success but URI is null")
                Toast.makeText(this, "Failed to get image", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d(TAG, "Camera capture canceled or failed")
        }
    }

    fun getRealPathFromURI(uri: Uri): String {
        var result = ""
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
                if (idx != -1) {
                    result = it.getString(idx)
                }
            }
        }
        return result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(_viewBinder.root)

        initViewModels()
        initView()
        setObservers()
        setListeners()
    }

    override fun initViewModels() {
        profileViewModel = getViewModel(
            viewModel = ProfileViewModel(this), className = ProfileViewModel::class.java
        )
        originalProfileData = getProfileData()
        originalProfileData?.let {
            _viewBinder.editTextName.setText(it.full_name)
            _viewBinder.edtPhoneNumber.setText(it.mobile.replace("+91", "") ?: "")
            _viewBinder.editTextEmail.setText(it.email)

            // Disable phone number field
            _viewBinder.edtPhoneNumber.isEnabled = false
            _viewBinder.ccp.isClickable = false
            _viewBinder.ccp.isEnabled = false

            CustomImageLoader.loadImageView(
                _viewBinder.ivProfileImage,
                BASE_URL + it.user_profile_image,
                it.user_profile_image,
                onError = {
                    _viewBinder.ivProfileImage.setImageResource(R.drawable.profile_placeholder)
                }
            )
        }
        _viewBinder.ivProfileImage.setOnClickListener {
            showImagePickerDialog()
        }
    }

    override fun setObservers() {

        profileViewModel.createProfileLiveData.observe(this) { response ->
            ProgressBarLoader.hide()

            response.data?.let { profileData ->
                prefs?.userProfile = Gson().toJson(profileData)
            }

            Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK)
            finish()
        }

        profileViewModel.createProfileErrorLiveData.observe(this) { errorMessage ->
            ProgressBarLoader.hide()
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    override fun setListeners() {
        _viewBinder.ivBack.setOnClickListener { finish() }
        _viewBinder.buttonApply.setOnClickListener {
            if (validateInput()) {
                updateProfile()
            }
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        AlertDialog.Builder(this)
            .setTitle("Choose Profile Picture")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> checkCameraPermission()
                    1 -> openGallery()
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission is already granted
                openCameraWithPermission()
            }

            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                // Show an explanation to the user
                AlertDialog.Builder(this)
                    .setTitle("Camera Permission Needed")
                    .setMessage("This app needs the camera permission to take profile pictures")
                    .setPositiveButton("OK") { _, _ ->
                        requestCameraPermission()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            else -> {
                // No explanation needed, request the permission
                requestCameraPermission()
            }
        }
    }

    private fun requestCameraPermission() {
        requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
    }

    private fun openCameraWithPermission() {
        try {
            val photoFile = createImageFile()
            photoFile?.let { file ->
                val photoURI = FileProvider.getUriForFile(
                    this,
                    getAuthority(this),
                    file
                )
                // Store this URI to use after camera returns
                selectedImageUri = photoURI
                Log.d(TAG, "Launching camera with URI: $photoURI")
                cameraLauncher.launch(photoURI)
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Camera error: ${ex.message}", ex)
            Toast.makeText(this, "Failed to open camera: ${ex.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageFile(): File? {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
                currentPhotoPath = absolutePath
                Log.d(TAG, "Created temp file at: $absolutePath")
            }
        } catch (ex: IOException) {
            Log.e(TAG, "Error creating image file: ${ex.message}", ex)
            Toast.makeText(this, "Failed to create image file", Toast.LENGTH_SHORT).show()
            return null
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun updateProfile() {
        val name = _viewBinder.editTextName.text.toString()
        val email = _viewBinder.editTextEmail.text.toString()

        val fieldsMap = HashMap<String, RequestBody>()
        if (name != originalProfileData?.full_name) {
            fieldsMap["full_name"] = name.toRequestBody("text/plain".toMediaTypeOrNull())
        }
        if (email != originalProfileData?.email) {
            fieldsMap["email"] = email.toRequestBody("text/plain".toMediaTypeOrNull())
        }

        // Check if there are actually any changes (including image) before proceeding
        if (fieldsMap.isEmpty() && selectedImageUri == null) {
            // No changes detected, just close the activity
            Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        // Only show loader if we're actually making changes
        val loader = ProgressBarLoader()
        ProgressBarLoader.show(loader, supportFragmentManager)

        var imagePart: MultipartBody.Part? = null
        selectedImageUri?.let { uri ->
            try {
                Log.d(TAG, "Processing image from URI: $uri")
                val inputStream = contentResolver.openInputStream(uri)
                val file = createTempFileFromInputStream(inputStream)
                Log.d(TAG, "Created compressed file at: ${file.absolutePath}")
                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                imagePart =
                    MultipartBody.Part.createFormData("user_profile_image", file.name, requestFile)
                Log.d(TAG, "Created MultipartBody.Part successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image: ${e.message}", e)
                Toast.makeText(this, "Failed to process image: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
        profileViewModel.updateProfileSelective(true, fieldsMap, imagePart)
    }

    private fun createTempFileFromInputStream(inputStream: InputStream?): File {
        val file = File.createTempFile("IMAGE_", ".jpg", cacheDir)

        inputStream?.use { input ->
            val bitmap = BitmapFactory.decodeStream(input)

            var quality = 100
            var compressedData: ByteArray
            do {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                compressedData = outputStream.toByteArray()
                quality -= 5
            } while (compressedData.size > 100 * 1024 && quality > 5) // Keep reducing until <100KB

            FileOutputStream(file).use { output ->
                output.write(compressedData)
            }
        }
        return file
    }

    private fun validateInput(): Boolean {
        val name = _viewBinder.editTextName.text.toString()
        val email = _viewBinder.editTextEmail.text.toString()
        if (name.isEmpty()) {
            _viewBinder.editTextName.error = "Full name required"
            return false
        }
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _viewBinder.editTextEmail.error = "Invalid email"
            return false
        }
        return true
    }

    override fun onViewClick(view: View?) {}
    override fun onThemeChanged(
        primaryColor: Int,
        secondaryColor: Int,
        primaryTextColor: Int,
        secondaryTextColor: Int,
        headerColor: Int,
        defaultTextColor: Int
    ) {
    }


    override fun initView() {}
}