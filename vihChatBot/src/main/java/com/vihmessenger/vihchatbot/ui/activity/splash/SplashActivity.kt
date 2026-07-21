package com.vihmessenger.vihchatbot.ui.activity.splash

import BaseActivity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import com.vihmessenger.vihchatbot.databinding.ActivitySplashBinding
import com.vihmessenger.vihchatbot.ui.activity.home.DashBoardActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : BaseActivity() {
    private val _viewBinder by lazy { ActivitySplashBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(_viewBinder.root)
    }

    private suspend fun gotoMainActivity() {
        delay(3000)
        startActivity(Intent(this, DashBoardActivity::class.java))
    }

    private fun coroutineScope() {
        GlobalScope.launch {
            delay(1200)
            withContext(Dispatchers.Main) {
                gotoMainActivity()
            }
        }
    }

    override fun initViewModels() {

    }

    override fun initView() {
        _viewBinder.wvSplash.loadUrl("https://65f3d16d0688d40cf9fcac12--fluffy-concha-9b12d1.netlify.app/")
        _viewBinder.wvSplash.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                coroutineScope()
            }
        }
    }

    override fun setObservers() {
    }

    override fun setListeners() {
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
    }
}