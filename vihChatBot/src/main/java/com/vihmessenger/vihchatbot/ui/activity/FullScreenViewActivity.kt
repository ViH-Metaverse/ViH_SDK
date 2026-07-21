package com.vihmessenger.vihchatbot.ui.activity

import com.vihmessenger.vihchatbot.utils.CustomImageLoader
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.databinding.ActivityFullScreenViewBinding

class FullScreenViewActivity : AppCompatActivity() {

    private val _viewBinder by lazy { ActivityFullScreenViewBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(_viewBinder.root)

        val imageUrl = intent.getStringExtra(IMAGEURL)

        if (!imageUrl.isNullOrEmpty()) {
            CustomImageLoader.loadImageView(
                imageView = _viewBinder.fullScreenImage,
                url = imageUrl,
                onError = {
                    _viewBinder.fullScreenImage.setImageResource(R.drawable.placeholder)
                }
            )
        } else {
            Toast.makeText(this, "Invalid Image URL", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    companion object {
        const val IMAGEURL = "IMAGEURL"

        fun startIntent(context: Context, imageUrl: String) {
            val intent = Intent(context, FullScreenViewActivity::class.java).apply {
                putExtra(IMAGEURL, imageUrl)
            }
            context.startActivity(intent)
        }
    }

}