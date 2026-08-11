package com.vihmessenger.vihchatbot.utils.sharedPreference

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.vihmessenger.vihchatbot.constants.AppConstants
import com.vihmessenger.vihchatbot.utils.VihLog

class Prefs private constructor(context: Context) {

    companion object {
        private const val ENCRYPTED_PREF_NAME = "VihMessengerSecurePreference"

        @Volatile
        private var INSTANCE: Prefs? = null

        fun getInstance(context: Context): Prefs =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Prefs(context.applicationContext).also { INSTANCE = it }
            }
    }

    /**
     * True when tokens and PII are being held in encrypted storage that survives a restart.
     * False means [InMemorySharedPreferences] is in use: the SDK still works, but nothing is
     * persisted, so the user must sign in again on the next launch. Host apps can read this
     * to decide whether to surface a message.
     */
    @Volatile
    var isSecureStorageAvailable: Boolean = true
        private set

    private val preferences: SharedPreferences = createSecurePreferences(context)

    /**
     * Builds AES-256 encrypted preferences, retrying once after clearing the corrupt state.
     *
     * SECURITY (VAPT F-07, CWE-311): this used to fall back to plaintext `MODE_PRIVATE`
     * preferences on any failure, which silently wrote access tokens, refresh tokens, the
     * phone number and the full user profile to a backup-eligible XML file — with only a
     * `Log.w` to mark it, so neither the user nor the app could tell. `security-crypto` does
     * genuinely fail on some OEM builds and after a Keystore reset or restore-to-new-device,
     * so the path is reachable in the field, not just in theory.
     *
     * The first failure is usually a stale keyset whose Keystore key no longer exists (the
     * classic restore-to-new-device symptom), which clearing and recreating fixes. If it
     * still fails we degrade to memory-only storage: credentials are never written to disk
     * in the clear, and the cost is a re-login next launch.
     */
    private fun createSecurePreferences(context: Context): SharedPreferences {
        buildEncrypted(context)?.let { return it }

        VihLog.w("Prefs", "EncryptedSharedPreferences init failed — clearing keyset and retrying.")
        clearCorruptSecureState(context)

        buildEncrypted(context)?.let {
            VihLog.i("Prefs", "EncryptedSharedPreferences recovered after reset.")
            return it
        }

        // Do NOT fall back to plaintext. Memory-only means the session cannot outlive the
        // process, which is a far better failure than persisting credentials unencrypted.
        isSecureStorageAvailable = false
        VihLog.e(
            "Prefs",
            "Secure storage unavailable — using memory-only preferences. " +
                "Session will not persist across restarts."
        )
        return InMemorySharedPreferences()
    }

    private fun buildEncrypted(context: Context): SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        VihLog.w("Prefs", "EncryptedSharedPreferences.create failed: ${e.javaClass.simpleName}")
        null
    }

    /**
     * Drops the encrypted preference file and any legacy plaintext file left behind by the
     * old fallback path. The legacy wipe matters on upgrade: a device that previously hit
     * the plaintext fallback still has those credentials sitting on disk, and this is the
     * only place that will ever clean them up.
     */
    private fun clearCorruptSecureState(context: Context) {
        runCatching {
            context.getSharedPreferences(ENCRYPTED_PREF_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
        runCatching { context.deleteSharedPreferences(ENCRYPTED_PREF_NAME) }
        runCatching { context.deleteSharedPreferences(AppConstants.AppSharedPref) }
    }

    var vihSettings: String?
        get() = preferences.getString(AppConstants.VihSettingSharedPref, null)
        set(value) = preferences.edit().putString(AppConstants.VihSettingSharedPref, value).apply()

    var userProfile: String?
        get() = preferences.getString(AppConstants.UserProfileSharedPref, null)
        set(value) = preferences.edit().putString(AppConstants.UserProfileSharedPref, value).apply()

    var accessToken: String?
        get() = preferences.getString(AppConstants.UserAccessToken, null)
        set(value) = preferences.edit().putString(AppConstants.UserAccessToken, value).apply()

    var phoneNumber: String?
        get() = preferences.getString(AppConstants.PHONENUMBER, null)
        set(value) = preferences.edit().putString(AppConstants.PHONENUMBER, value).apply()

    var hashcode: String?
        get() = preferences.getString(AppConstants.HASHCODE_EXTRA, null)
        set(value) = preferences.edit().putString(AppConstants.HASHCODE_EXTRA, value).apply()

    var refreshToken: String?
        get() = preferences.getString(AppConstants.RefreshAccessToken, null)
        set(value) = preferences.edit().putString(AppConstants.RefreshAccessToken, value).apply()

    var name: String?
        get() = preferences.getString("USER_NAME", null)
        set(value) = preferences.edit().putString("USER_NAME", value).apply()

    var email: String?
        get() = preferences.getString("USER_EMAIL", null)
        set(value) = preferences.edit().putString("USER_EMAIL", value).apply()

    var userProfileUrl: String?
        get() = preferences.getString("USER_PROFILE_URL", null)
        set(value) = preferences.edit().putString("USER_PROFILE_URL", value).apply()

    var notificationIcon: Int
        get() = preferences.getInt("NOTIFICATION_ICON", 0)
        set(value) = preferences.edit().putInt("NOTIFICATION_ICON", value).apply()

    var isSDK: Boolean
        get() = preferences.getBoolean(AppConstants.IS_SDK_MODE, false)
        set(value) = preferences.edit().putBoolean(AppConstants.IS_SDK_MODE, value).apply()

    /**
     * True when the host application drives the SDK headlessly — i.e. it called
     * `VihDiscover.prepareSession` and renders its own enterprise list, instead of
     * launching the SDK's splash. In that mode the SDK must never relaunch the host's
     * launcher activity on session expiry: doing so wipes the host's task stack.
     */
    var isHostDriven: Boolean
        get() = preferences.getBoolean(AppConstants.IS_HOST_DRIVEN, false)
        set(value) = preferences.edit().putBoolean(AppConstants.IS_HOST_DRIVEN, value).apply()

    // Shortcut preferences
    var shortcutPromptCount: Int
        get() = preferences.getInt(AppConstants.PREF_SHORTCUT_PROMPT_COUNT, 0)
        set(value) = preferences.edit().putInt(AppConstants.PREF_SHORTCUT_PROMPT_COUNT, value).apply()

    var shortcutDeniedCount: Int
        get() = preferences.getInt(AppConstants.PREF_SHORTCUT_DENIED_COUNT, 0)
        set(value) = preferences.edit().putInt(AppConstants.PREF_SHORTCUT_DENIED_COUNT, value).apply()

    var shortcutDeniedByUser: Boolean
        get() = preferences.getBoolean(AppConstants.PREF_SHORTCUT_DENIED_BY_USER, false)
        set(value) = preferences.edit().putBoolean(AppConstants.PREF_SHORTCUT_DENIED_BY_USER, value).apply()

    var shortcutAddedSuccessfully: Boolean
        get() = preferences.getBoolean(AppConstants.PREF_SHORTCUT_ADDED_SUCCESSFULLY, false)
        set(value) = preferences.edit().putBoolean(AppConstants.PREF_SHORTCUT_ADDED_SUCCESSFULLY, value).apply()

    // Stable per-install identifier used by the new AWS data path:
    //  - FCM session registry maps deviceId -> FCM token (architecture §3.3)
    //  - IoT Core topic is /u/{deviceId}/inbox (architecture §3.3, §4.1 step 7)
    // Lazily generated on first read so existing installs upgrade transparently.
    val deviceId: String
        get() {
            val existing = preferences.getString(AppConstants.DEVICE_ID, null)
            if (!existing.isNullOrBlank()) return existing
            val fresh = java.util.UUID.randomUUID().toString()
            preferences.edit().putString(AppConstants.DEVICE_ID, fresh).apply()
            return fresh
        }

    // Most recent FCM token surfaced by FirebaseMessagingService.onNewToken. Cached so
    // sign-in can re-register the token without waiting for the next token rotation.
    var fcmToken: String?
        get() = preferences.getString(AppConstants.FCM_TOKEN, null)
        set(value) = preferences.edit().putString(AppConstants.FCM_TOKEN, value).apply()

    // Tracks whether the cached fcmToken has been ack'd by the session-registry endpoint.
    // Lets us replay registration after auth becomes available (token may arrive before login).
    var fcmTokenRegistered: Boolean
        get() = preferences.getBoolean(AppConstants.FCM_TOKEN_REGISTERED, false)
        set(value) = preferences.edit().putBoolean(AppConstants.FCM_TOKEN_REGISTERED, value).apply()

    // The channel hashkey the device token was last successfully registered under. When
    // the active hashcode differs from this, the device must re-register so shoots on the
    // new channel aren't rejected as "not registered on the channel".
    var fcmRegisteredHashcode: String?
        get() = preferences.getString(AppConstants.FCM_REGISTERED_HASHCODE, null)
        set(value) = preferences.edit().putString(AppConstants.FCM_REGISTERED_HASHCODE, value).apply()


    /**
     * Persist [newHashcode] as the active channel, resetting channel-scoped state when
     * the channel actually changes.
     *
     * When the SDK is (re)launched on a different channel than last time, the previously
     * stored session token, refresh token, cached SDK settings and user profile all
     * belong to the *old* channel. Reusing them makes authenticated calls to the new
     * channel fail (the only workaround was manually clearing app data). Dropping that
     * state here makes a channel switch start from a clean session. Install-scoped values
     * (DEVICE_ID, FCM_TOKEN) are preserved; DeviceTokenRegistrar re-registers push for
     * the new channel after the next login.
     */
    fun switchChannel(newHashcode: String) {
        val previous = hashcode
        if (!previous.isNullOrBlank() && previous != newHashcode) {
            clearAllPreferences()
        }
        hashcode = newHashcode
    }

    fun clearAllPreferences() {
        val editor = preferences.edit()
        editor.remove(AppConstants.VihSettingSharedPref)
        editor.remove(AppConstants.UserProfileSharedPref)
        editor.remove(AppConstants.UserAccessToken)
        editor.remove(AppConstants.PHONENUMBER)
        editor.remove(AppConstants.HASHCODE_EXTRA)
        editor.remove(AppConstants.RefreshAccessToken)
        editor.remove("USER_NAME")
        editor.remove("USER_EMAIL")
        editor.remove("USER_PROFILE_URL")
        editor.remove("NOTIFICATION_ICON")
        // Logout invalidates the server-side deviceId->user binding. Force a fresh
        // registration on the next login. Keep DEVICE_ID and FCM_TOKEN — they belong
        // to the install, not the user.
        editor.remove(AppConstants.FCM_TOKEN_REGISTERED)
        editor.remove(AppConstants.FCM_REGISTERED_HASHCODE)
        editor.apply()
    }
}
