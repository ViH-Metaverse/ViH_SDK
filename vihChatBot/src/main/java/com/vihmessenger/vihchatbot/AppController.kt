package com.vihmessenger.vihchatbot

import android.app.Activity
import android.app.Application
import android.content.Context
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
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.googlecompat.GoogleCompatEmojiProvider
import com.vihmessenger.vihchatbot.config.VihConfigStore
import com.vihmessenger.vihchatbot.data.services.BaseCloudAPIService
import com.vihmessenger.vihchatbot.services.DeviceTokenRegistrar
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

        /**
         * Counts started Activities so [isAppInForeground] can answer.
         *
         * A standalone object rather than [AppController] itself, because the registration has
         * to happen against whichever `Application` actually exists — see [ensureInitialized].
         * While this lived on the AppController instance and was registered from `onCreate`, a
         * host-owned Application meant it was never registered, [isAppInForeground] was
         * permanently false, and [MyFirebaseMessagingService] therefore never broadcast an
         * inbound message to the open chat. The visible symptom: a Flow Builder keyword
         * produced no reply until you left the chat and came back, because the flow's real
         * response arrives asynchronously by push and the send-response only carries a
         * suppressed acknowledgement.
         */
        private object ActivityCounter : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                currentActivityCount++
            }

            override fun onActivityStopped(activity: Activity) {
                if (currentActivityCount > 0) currentActivityCount--
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityDestroyed(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        }

        @Volatile
        private var initialised = false

        @Volatile
        private var networkMonitor: NetworkConnectivityManager? = null

        /**
         * Brings up the process-wide state the SDK's screens assume exists, exactly once.
         *
         * All of this used to live only in [onCreate]. An Android process has one `Application`
         * object, and the host app's declaration wins the manifest merge over the library's
         * `android:name=".AppController"` — so in any app that ships its own Application class
         * (React Native's `MainApplication`, Hilt's generated one, …) `AppController.onCreate`
         * never runs and every companion field below stays null. The failures that produced
         * were all crashes with no obvious link to their cause:
         *
         *  - `EmojiPopup` threw `IllegalStateException: Please install an EmojiProvider …`
         *  - `ChatActivity.prefs` (`AppController.prefs!!`) threw `KotlinNullPointerException`
         *  - `BaseCloudAPIService.getApiService` threw `CloudApiService cannot be null`
         *  - `VihTokenAuthenticator` silently gave up on every 401, so 1.1.5's automatic
         *    session renewal never actually ran in a host-driven app
         *
         * [BaseActivity.onCreate] and the [com.vihmessenger.vihchatbot.discover.VihDiscover]
         * entry points both call this, so the SDK no longer depends on owning the Application.
         *
         * Deliberately excluded: `AppCompatDelegate.setDefaultNightMode` and Amplify. Both are
         * process-global settings that belong to whoever owns the app, and forcing them from a
         * library screen would reach into the host's own UI.
         */
        @JvmStatic
        fun ensureInitialized(context: Context) {
            if (initialised) return
            synchronized(this) {
                if (initialised) return
                val app = context.applicationContext
                runCatching {
                    if (prefs == null) prefs = Prefs.getInstance(app)
                }.onFailure { VihLog.e(TAG, "Prefs init failed: ${it.message}") }
                runCatching {
                    if (cloudApiService == null) cloudApiService = BaseCloudAPIService()
                }.onFailure { VihLog.e(TAG, "Retrofit init failed: ${it.message}") }
                runCatching {
                    (app as? Application)?.registerActivityLifecycleCallbacks(ActivityCounter)
                }
                runCatching { FirebaseApp.initializeApp(app) }
                runCatching { DynamicThemeManager.loadSavedTheme(app) }
                ensureFcmToken(app)
                ensureEmojiProvider(app)
                initialised = true
            }
        }

        /**
         * Obtains the FCM registration token and pushes it to the session registry.
         *
         * The token was only ever fetched by `DashboardFragment` and `DashBoardActivity`, so on
         * the host-driven path — `VihDiscover.openChat` straight into the chat — it was never
         * fetched at all. `prepareSession` then signed in with `fcm_token: ""`, the backend held
         * no push token for the device, and **no push was ever delivered**. Anything that only
         * arrives by push was therefore invisible: most visibly a Flow Builder response, since
         * the send call returns a suppressed acknowledgement rather than the reply itself.
         *
         * [FirebaseMessaging.getInstance] resolves from the cache when a token already exists,
         * and `onNewToken` only fires on rotation — which is why this never self-corrected.
         */
        @JvmStatic
        fun ensureFcmToken(context: Context) {
            val app = context.applicationContext
            runCatching {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    // The whole callback is guarded. It runs later, on the main looper, so an
                    // escape here is an uncaught exception in the *host's* process — the
                    // runCatching around addOnCompleteListener below cannot see it.
                    runCatching {
                        // isSuccessful must be checked before touching result: Task.getResult()
                        // rethrows the task's failure as RuntimeExecutionException. Reading it
                        // first is what crashed hosts whose Firebase config has no valid API
                        // key — a configuration we cannot control and must not die on, since
                        // push is an enhancement to the SDK, not a precondition for using it.
                        if (!task.isSuccessful) {
                            VihLog.w(TAG, "FCM token unavailable: ${task.exception?.message}")
                            return@runCatching
                        }
                        val token = task.result
                        if (token.isNullOrBlank()) {
                            VihLog.w(TAG, "FCM returned a blank token")
                            return@runCatching
                        }
                        if (Prefs.getInstance(app).fcmToken != token) {
                            DeviceTokenRegistrar.onNewToken(app, token)
                        } else {
                            DeviceTokenRegistrar.registerCachedTokenIfNeeded(app)
                        }
                    }.onFailure { VihLog.e(TAG, "FCM token handling failed: ${it.message}") }
                }
            }.onFailure { VihLog.e(TAG, "FCM token fetch failed: ${it.message}") }
        }

        /**
         * The shared connectivity monitor. Created on demand so it also exists when the host
         * owns the Application — [DiscoverFragment] used to reach it by casting
         * `requireActivity().application` to [AppController], which is a ClassCastException in
         * exactly the same hosts.
         */
        @JvmStatic
        fun sharedNetworkMonitor(context: Context): NetworkConnectivityManager =
            networkMonitor ?: synchronized(this) {
                networkMonitor ?: NetworkConnectivityManager(
                    context.applicationContext as Application
                ).also {
                    it.startMonitoring()
                    networkMonitor = it
                }
            }

        @Volatile
        private var emojiProviderInstalled = false

        /**
         * Installs the emoji provider that [com.vanniktech.emoji.EmojiPopup] requires, exactly
         * once per process.
         *
         * This used to run inline in [onCreate], which is only reached when the SDK owns the
         * `Application` — the library manifest declares `android:name=".AppController"`, but a
         * host app with its own Application class (React Native's `MainApplication`, for
         * instance) wins the merge, so `AppController.onCreate` never executes. `ChatActivity`
         * then built an `EmojiPopup` against an uninstalled manager and the host app died with
         * `IllegalStateException: Please install an EmojiProvider through the
         * EmojiManager.install() method first`.
         *
         * [BaseActivity.onCreate] now calls this before any SDK screen inflates, so the SDK is
         * self-sufficient regardless of who owns the Application.
         */
        @JvmStatic
        fun ensureEmojiProvider(context: Context) {
            if (emojiProviderInstalled) return
            synchronized(this) {
                if (emojiProviderInstalled) return
                runCatching {
                    val app = context.applicationContext
                    EmojiManager.install(
                        GoogleCompatEmojiProvider(
                            EmojiCompat.init(
                                FontRequestEmojiCompatConfig(
                                    app, FontRequest(
                                        "com.google.android.gms.fonts",
                                        "com.google.android.gms",
                                        "Noto Color Emoji Compat",
                                        R.array.com_google_android_gms_fonts_certs,
                                    )
                                ).setReplaceAll(true)
                            )
                        )
                    )
                    emojiProviderInstalled = true
                }.onFailure {
                    // Never let emoji setup take down a host app. Without a provider the popup
                    // would still throw, so ChatActivity guards its own use as well.
                    VihLog.e(TAG, "Emoji provider install failed: ${it.javaClass.simpleName} - ${it.message}")
                }
            }
        }

        /** True once [ensureEmojiProvider] has successfully installed the provider. */
        @JvmStatic
        fun isEmojiProviderInstalled(): Boolean = emojiProviderInstalled

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
        appController = this
        // Follow the device's system light/dark setting. Only when the SDK owns the app — see
        // the note in [ensureInitialized].
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        ensureInitialized(this)
        initAmplify()
        networkConnectivityManager = sharedNetworkMonitor(this)
        // SECURITY (VAPT F-14): diagnostics are NOT started here. Bugfender is a third-party
        // remote log/crash processor, and in an SDK embedded in someone else's app that data
        // flow is the host operator's decision. It is opt-in via VihConfig.diagnostics and is
        // started from [applyDiagnosticsConfig], which VihConfigStore.set invokes once the
        // host has actually supplied a config. No config supplied => nothing leaves the device.

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


    // Counting lives in [ActivityCounter], which ensureInitialized registers. These remain
    // only because the class still declares the interface.
    override fun onActivityStarted(p0: Activity) {}

    override fun onActivityStopped(p0: Activity) {}
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityDestroyed(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

}