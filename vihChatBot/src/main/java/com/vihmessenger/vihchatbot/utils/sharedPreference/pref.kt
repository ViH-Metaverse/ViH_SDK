package com.vihmessenger.vihchatbot.utils.sharedPreference

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.vihmessenger.vihchatbot.constants.AppConstants

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

    private val preferences: SharedPreferences = try {
        // SECURITY: Use EncryptedSharedPreferences to protect sensitive data at rest
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            ENCRYPTED_PREF_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback: if encryption fails (e.g., on very old devices), log warning and use standard prefs
        Log.w("Prefs", "EncryptedSharedPreferences unavailable, falling back to standard preferences")
        context.getSharedPreferences(AppConstants.AppSharedPref, Context.MODE_PRIVATE)
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
