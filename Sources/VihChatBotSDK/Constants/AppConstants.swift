import Foundation

/// Mirrors `com.vihmessenger.vihchatbot.constants.AppConstants`.
public enum AppConstants {
    public static let appSharedPref = "VihMessengerPreference"
    public static let vihSettingSharedPref = "VihMessengerPreference"
    public static let userProfileSharedPref = "UserProfileSharedPref"
    public static let userAccessToken = "UserAccessToken"
    public static let refreshAccessToken = "RefreshAccessToken"

    public static let hashcodeExtra = "hashcode_extra"
    public static let phoneNumber = "phoneNumber"
    public static let flavor = "flavor"
    public static let sendId = "bot"
    public static let cpaas = "cpaas"
    public static let receiverId = "user"
    public static let welcomeMessage = "WELCOME_MESSAGE"
    public static let templateMessage = "TEMPLATE_MESSAGE"
    public static let leftChatProgress = "LEFT_CHAT_PROGRESS"
    public static let viewTypeDateHeader = "VIEW_TYPE_DATE_HEADER"
    public static let isSdkMode = "is_sdk_mode"

    public static let id = "ID"
    public static let channelName = "CHANNEL_NAME"
    public static let channelLogo = "CHANNEL_LOGO"
    public static let channelExtra = "CHANNEL_EXTRA"

    public static let prefShortcutPromptCount = "pref_shortcut_prompt_count"
    public static let prefShortcutDeniedCount = "pref_shortcut_denied_count"
    public static let prefShortcutDeniedByUser = "pref_shortcut_denied_by_user"
    public static let prefShortcutAddedSuccessfully = "pref_shortcut_added_successfully"
    public static let actionShortcutPinned = "com.vihmessenger.vihchatbot.SHORTCUT_PINNED"

    // AWS migration — Phase 1 keys (architecture §3.3, §6.2).
    public static let deviceId = "device_id"
    public static let fcmToken = "fcm_token"
    public static let fcmTokenRegistered = "fcm_token_registered"
    // Channel hashkey the device token was last registered under (push registry is
    // keyed per (channel, device), so a channel switch must re-register).
    public static let fcmRegisteredHashcode = "fcm_registered_hashcode"

    // Correlation IDs propagated from APNs/FCM data payloads through to the chat UI.
    public static let extraShootId = "shoot_id"
    public static let extraMessageId = "message_id"
    public static let extraTraceId = "trace_id"

    public static let fromVihText = "From VIH"

    // Broadcast equivalent. UIKit replaces Android LocalBroadcastManager with
    // NotificationCenter — call sites post / observe this name.
    public static let fcmMessageReceived = Notification.Name("com.vihmessenger.vihchatbot.FCM_MESSAGE")
}
