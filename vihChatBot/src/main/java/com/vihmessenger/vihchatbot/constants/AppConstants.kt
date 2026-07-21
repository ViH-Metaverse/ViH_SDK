package com.vihmessenger.vihchatbot.constants

object AppConstants {
    const val AppSharedPref = "VihMessengerPreference"
    const val VihSettingSharedPref = "VihMessengerPreference"
    const val UserProfileSharedPref = "UserProfileSharedPref"
    const val UserAccessToken = "UserAccessToken"
    const val RefreshAccessToken = "RefreshAccessToken"

    const val HASHCODE_EXTRA = "hashcode_extra"
    const val PHONENUMBER = "phoneNumber"
    const val FLAVOR = "flavor"
    const val SEND_ID = "bot"
    const val CPASS = "cpaas"
    const val RECEIVER_ID = "user"
    const val WELCOME_MESSAGE = "WELCOME_MESSAGE"
    const val TEMPLATE_MESSAGE = "TEMPLATE_MESSAGE"
    const val LEFT_CHAT_PROGRESS = "LEFT_CHAT_PROGRESS"
    const val VIEW_TYPE_DATE_HEADER = "VIEW_TYPE_DATE_HEADER"
    const val IS_SDK_MODE = "is_sdk_mode"


    const val ID = "ID"
    const val CHANNEL_NAME = "CHANNEL_NAME"
    const val CHANNEL_LOGO = "CHANNEL_LOGO"
    const val CHANNEL_EXTRA = "CHANNEL_EXTRA"


    const val PREF_SHORTCUT_PROMPT_COUNT = "pref_shortcut_prompt_count"
    const val PREF_SHORTCUT_DENIED_COUNT = "pref_shortcut_denied_count"
    const val PREF_SHORTCUT_DENIED_BY_USER = "pref_shortcut_denied_by_user"
    const val PREF_SHORTCUT_ADDED_SUCCESSFULLY = "pref_shortcut_added_successfully" // To ensure we don't ask again if added
    const val ACTION_SHORTCUT_PINNED = "com.vihmessenger.vihchatbot.SHORTCUT_PINNED"


    // AWS migration — Phase 1 keys (architecture §3.3, §6.2)
    const val DEVICE_ID = "device_id"
    const val FCM_TOKEN = "fcm_token"
    const val FCM_TOKEN_REGISTERED = "fcm_token_registered"
    // The channel hashkey the device token was last successfully registered under. The
    // push registry is keyed per (channel, device), so a channel switch must re-register.
    const val FCM_REGISTERED_HASHCODE = "fcm_registered_hashcode"

    // Correlation IDs forwarded from FCM data payload through to the chat UI
    const val EXTRA_SHOOT_ID = "shoot_id"
    const val EXTRA_MESSAGE_ID = "message_id"
    const val EXTRA_TRACE_ID = "trace_id"


    const val FROM_VIH_TEXT = "From VIH"


}