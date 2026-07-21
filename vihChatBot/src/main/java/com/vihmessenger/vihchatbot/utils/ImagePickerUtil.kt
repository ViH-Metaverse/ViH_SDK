package com.vihmessenger.vihchatbot.utils

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImagePickerUtil {

    private const val REQUEST_IMAGE_CAPTURE = 1
    private const val REQUEST_IMAGE_PICK = 2

    fun dispatchTakePictureIntent(
        activity: Activity, photoFile: File, pickCamera: ActivityResultLauncher<Intent>
    ) {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(activity.packageManager) != null) {
            val photoURI: Uri = FileProvider.getUriForFile(
                activity, "${activity.packageName}.fileprovider", photoFile
            )
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            pickCamera.launch(takePictureIntent)
        }
    }

    fun dispatchPickImageIntent(activity: Activity) {
        val pickPhoto = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        activity.startActivityForResult(pickPhoto, REQUEST_IMAGE_PICK)
    }

    fun dispatchPickImageIntentFragment(activity: Fragment) {
        val pickPhoto = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        activity.startActivityForResult(pickPhoto, REQUEST_IMAGE_PICK)
    }

    fun createImageFile(activity: Activity): File? {
        val timeStamp: String =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir: File? = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Uri? {
        return when (requestCode) {
            REQUEST_IMAGE_CAPTURE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val uri = Uri.fromFile(File(data?.getStringExtra(MediaStore.EXTRA_OUTPUT)))
                    uri
                } else {
                    null
                }
            }

            REQUEST_IMAGE_PICK -> {
                if (resultCode == Activity.RESULT_OK && data != null && data.data != null) {
                    data.data
                } else {
                    null
                }
            }

            else -> null
        }
    }
}
