package com.vihmessenger.vihchatbot.ui.activity.splash

import BaseActivity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.vihmessenger.vihchatbot.databinding.ActivitySplashBinding
import com.vihmessenger.vihchatbot.ui.activity.home.DashBoardActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Brand splash shown while the SDK warms up.
 *
 * SECURITY (VAPT F-05): this used to render a remote Netlify deploy-preview URL in a
 * WebView, which handed render authority inside partner apps to a third-party host on a
 * subdomain we do not durably control. The artwork is now a local drawable and there is no
 * WebView, no network fetch, and no JavaScript on this screen.
 */
class SplashActivity : BaseActivity() {
    private val _viewBinder by lazy { ActivitySplashBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(_viewBinder.root)
    }

    override fun initViewModels() {
    }

    override fun initView() {
        // Hold the brand frame briefly, then continue. Scoped to the lifecycle so rotating
        // or backing out cancels the pending navigation instead of leaking (the previous
        // GlobalScope launch survived the Activity).
        lifecycleScope.launch {
            delay(SPLASH_HOLD_MS)
            startActivity(Intent(this@SplashActivity, DashBoardActivity::class.java))
            finish()
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

    private companion object {
        /** Matches the previous perceived duration (1.2s page load + 3s delay). */
        const val SPLASH_HOLD_MS = 1500L
    }
}
