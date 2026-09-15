package com.vihmessenger.vihchatbot.utils

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import java.io.File

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

    /**
     * @deprecated HISPL 12.8. `ACTION_PICK` opens the whole gallery and is the reason the SDK
     * used to declare `READ_MEDIA_IMAGES`. Use the Android Photo Picker instead, which grants
     * access to the chosen item only and needs no permission:
     *
     * ```
     * private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> … }
     * picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
     * ```
     *
     * Kept only so a host app calling it does not fail to compile on upgrade. Nothing in the
     * SDK calls it; it will be removed in a later release.
     */
    @Deprecated(
        "Use ActivityResultContracts.PickVisualMedia — no permission required (HISPL 12.8).",
        level = DeprecationLevel.WARNING
    )
    fun dispatchPickImageIntent(activity: Activity) {
        val pickPhoto = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        activity.startActivityForResult(pickPhoto, REQUEST_IMAGE_PICK)
    }

    /** @see dispatchPickImageIntent */
    @Deprecated(
        "Use ActivityResultContracts.PickVisualMedia — no permission required (HISPL 12.8).",
        level = DeprecationLevel.WARNING
    )
    fun dispatchPickImageIntentFragment(activity: Fragment) {
        val pickPhoto = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        activity.startActivityForResult(pickPhoto, REQUEST_IMAGE_PICK)
    }

    // SECURITY (HISPL 12.4): capture files live in the internal cache, not
    // getExternalFilesDir(), which is world-readable to any app holding
    // READ_EXTERNAL_STORAGE on API 24-28. See CaptureFiles.
    fun createImageFile(activity: Activity): File? = CaptureFiles.create(activity)

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
