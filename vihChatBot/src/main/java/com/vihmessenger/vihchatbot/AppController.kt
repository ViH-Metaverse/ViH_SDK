package com.vihmessenger.vihchatbot

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.vihmessenger.vihchatbot.utils.VihLog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.provider.FontRequest
import androidx.emoji.text.EmojiCompat
import androidx.emoji.text.FontRequestEmojiCompatConfig
import com.amplifyframework.AmplifyException
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify
import com.amplifyframework.core.AmplifyConfiguration
import com.bugfender.sdk.Bugfender
import com.google.firebase.FirebaseApp
import org.json.JSONObject
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.googlecompat.GoogleCompatEmojiProvider
import com.vihmessenger.vihchatbot.config.VihConfigStore
import com.vihmessenger.vihchatbot.data.services.BaseCloudAPIService
import com.vihmessenger.vihchatbot.utils.DynamicThemeManager
import com.vihmessenger.vihchatbot.utils.NetworkConnectivityManager
import com.vihmessenger.vihchatbot.utils.sharedPreference.Prefs
import retrofit2.Retrofit

class AppController : Application(),Application.ActivityLifecycleCallbacks {
    lateinit var networkConnectivityManager: NetworkConnectivityManager

    companion object {
        val TAG: String = AppController::class.java.simpleName
        var appController: AppController? = null
        var cloudApiService: Retrofit? = null
        var prefs: Prefs? = null
        private var currentActivityCount = 0
        fun isAppInForeground(): Boolean {
            return currentActivityCount > 0
        }

        @Volatile
        private var diagnosticsStarted = false

        /**
         * Starts Bugfender iff the host app opted in via [VihConfig.diagnostics]. Called by
         * [VihConfigStore.set] rather than from `onCreate`, because the host's config does not
         * exist yet at Application-create time — initialising there would have made the
         * opt-in permanently inert.
         *
         * Idempotent: a host that re-enters the SDK with a new config will not re-init.
         */
        @JvmStatic
        fun applyDiagnosticsConfig() {
            if (diagnosticsStarted) return
            val app = appController ?: return
            val diagnostics = VihConfigStore.config?.diagnostics ?: return
            val wantsLogging = diagnostics.remoteLoggingEnabled
            val wantsCrashes = diagnostics.crashReportingEnabled
            if (!wantsLogging && !wantsCrashes) return

            val bugfenderKey = BuildConfig.BUGFENDER_KEY
            if (bugfenderKey.isEmpty()) {
                VihLog.w(TAG, "Diagnostics opted in but no Bugfender key configured — skipping.")
                return
            }

            diagnosticsStarted = true
            Bugfender.init(app, bugfenderKey, BuildConfig.DEBUG)
            if (wantsCrashes) Bugfender.enableCrashReporting()
            // UI-event and logcat capture sweep up whatever else is on screen or in the log,
            // which we cannot vet — debug builds only, and only when logging was opted into.
            if (wantsLogging && BuildConfig.DEBUG) {
                Bugfender.enableUIEventLogging(app)
                Bugfender.enableLogcatLogging()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        appController = this
        cloudApiService = BaseCloudAPIService()
        // Follow the device's system light/dark setting.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        prefs = Prefs.getInstance(applicationContext)
        FirebaseApp.initializeApp(this)
        initAmplify()
        networkConnectivityManager = NetworkConnectivityManager(this)
        networkConnectivityManager.startMonitoring()
        EmojiManager.install(
            GoogleCompatEmojiProvider(
                EmojiCompat.init(
                    FontRequestEmojiCompatConfig(
                        this, FontRequest(
                            "com.google.android.gms.fonts",
                            "com.google.android.gms",
                            "Noto Color Emoji Compat",
                            R.array.com_google_android_gms_fonts_certs,
                        )
                    ).setReplaceAll(true)
                )
            )
        )
        // SECURITY (VAPT F-14): diagnostics are NOT started here. Bugfender is a third-party
        // remote log/crash processor, and in an SDK embedded in someone else's app that data
        // flow is the host operator's decision. It is opt-in via VihConfig.diagnostics and is
        // started from [applyDiagnosticsConfig], which VihConfigStore.set invokes once the
        // host has actually supplied a config. No config supplied => nothing leaves the device.

        DynamicThemeManager.loadSavedTheme(this)

    }

    /**
     * Configures Amplify Auth (Cognito) for passwordless email-OTP sign-in. Config is built
     * programmatically from BuildConfig (per-flavor pool) rather than a bundled
     * amplifyconfiguration.json, so secrets stay in local.properties. No-ops safely when the
     * pool isn't provisioned for this build, leaving the existing phone login path intact.
     */
    private fun initAmplify() {
        val poolId = BuildConfig.COGNITO_USER_POOL_ID
        val clientId = BuildConfig.COGNITO_APP_CLIENT_ID
        val region = BuildConfig.COGNITO_REGION
        if (poolId.isEmpty() || clientId.isEmpty()) {
            VihLog.w(TAG, "Cognito not configured for this build — skipping Amplify init.")
            return
        }
        try {
            val config = JSONObject().put(
                "auth",
                JSONObject().put(
                    "plugins",
                    JSONObject().put(
                        "awsCognitoAuthPlugin",
                        JSONObject()
                            .put(
                                "CognitoUserPool", JSONObject().put(
                                    "Default", JSONObject()
                                        .put("PoolId", poolId)
                                        .put("AppClientId", clientId)
                                        .put("Region", region)
                                )
                            )
                            .put(
                                "Auth", JSONObject().put(
                                    "Default",
                                    JSONObject().put("authenticationFlowType", "USER_AUTH")
                                )
                            )
                    )
                )
            )
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.configure(AmplifyConfiguration.builder(config).build(), applicationContext)
            VihLog.i(TAG, "Amplify Auth (Cognito) configured.")
        } catch (e: AmplifyException) {
            VihLog.e(TAG, "Amplify init failed: ${e.message}", e)
        }
    }


    override fun onActivityStarted(p0: Activity) {
        currentActivityCount++
    }


    override fun onActivityStopped(p0: Activity) {
        currentActivityCount--
    }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityDestroyed(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

}