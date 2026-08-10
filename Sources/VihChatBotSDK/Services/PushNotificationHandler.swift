import Foundation
import UserNotifications
import UIKit

/// iOS equivalent of `services/MyFirebaseMessagingService.kt`. Two entry points:
///   - `registerDeviceToken(_:)` — host app calls this from
///     `application(_:didRegisterForRemoteNotificationsWithDeviceToken:)`.
///   - `handle(notification:)` — host app forwards an APNs payload (or a
///     Firebase Messaging `RemoteMessage.data` map) and we mirror the Android
///     behaviour: post `fcmMessageReceived` to refresh the chat UI, build a
///     `UNUserNotificationContent`, propagate the correlation IDs through the
///     OS notification, and clear any matching pending notifications.
public enum PushNotificationHandler {

    /// Host-app entry from `didRegisterForRemoteNotificationsWithDeviceToken`.
    /// Stores the **raw APNs token** as a hex string. Only use this path when
    /// the backend accepts APNs tokens directly. If Firebase Messaging is wired
    /// (the default since the Android parity work), call `registerFCMToken(_:)`
    /// from the `MessagingDelegate` instead — Firebase converts APNs → FCM and
    /// the backend expects the FCM-shaped token.
    public static func registerDeviceToken(_ tokenData: Data) {
        let token = tokenData.map { String(format: "%02x", $0) }.joined()
        let prefs = VihChatBotSDK.shared.prefs
        prefs?.fcmToken = token
        prefs?.fcmTokenRegistered = false
        DeviceTokenRegistrar.shared.onNewToken(token)
    }

    /// Host-app entry from Firebase's `messaging(_:didReceiveRegistrationToken:)`.
    /// This is the FCM token the Android-side backend pipeline expects — exactly
    /// the same format Android sends, so iOS lights up the same delivery path
    /// without a backend change.
    public static func registerFCMToken(_ fcmToken: String) {
        guard !fcmToken.isEmpty else { return }
        let prefs = VihChatBotSDK.shared.prefs
        prefs?.fcmToken = fcmToken
        prefs?.fcmTokenRegistered = false
        DeviceTokenRegistrar.shared.onNewToken(fcmToken)
    }

    /// Equivalent of `MyFirebaseMessagingService.onMessageReceived`.
    /// Pass the `aps` data payload — for Firebase callers, pass `userInfo`
    /// stripped to the data fields.
    public static func handle(notification userInfo: [AnyHashable: Any]) {
        let shootId = userInfo[AppConstants.extraShootId] as? String
        let messageId = userInfo[AppConstants.extraMessageId] as? String
        let traceId = userInfo[AppConstants.extraTraceId] as? String
        CorrelationLogger.debug(
            tag: "PUSH_DIAGNOSTIC",
            message: "received keys=\(userInfo.keys.map { "\($0)" }.joined(separator: ","))",
            shootId: shootId, messageId: messageId, traceId: traceId
        )

        // Refresh the chat list / chat screen when the app is foregrounded —
        // mirrors the Android `LocalBroadcastManager` "FCM_MESSAGE" broadcast.
        NotificationCenter.default.post(
            name: AppConstants.fcmMessageReceived,
            object: nil,
            userInfo: userInfo as? [String: Any]
        )

        // If app is backgrounded, the system already showed the OS notification.
        // If foreground, the host can either suppress or present via
        // `UNUserNotificationCenterDelegate.willPresent`.
    }

    /// Optional: build the equivalent of Android's custom RemoteViews
    /// notification. Most iOS hosts prefer to send `alert` payloads from the
    /// server and let the system render them. The tone is chosen from the
    /// payload's CPaaS `template_type` (see ``sound(forTemplateType:)``).
    public static func presentLocalNotification(title: String, body: String, userInfo: [String: Any]) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.userInfo = userInfo
        applyTone(to: content)
        let req = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(req)
    }

    // MARK: - Per-template notification tones

    /// The notification tone for a CPaaS template type — Android parity
    /// (`MyFirebaseMessagingService`): `1` = OTP → OTP tone; `2`/`3` =
    /// promotional/transactional → promo tone; anything else = the system default.
    ///
    /// The custom tones require `vih_tone_otp.caf` / `vih_tone_promo.caf` to be present in the
    /// bundle that renders the notification — the **app's main bundle** for foreground/local
    /// notifications, and the **Notification Service Extension's bundle** for backgrounded remote
    /// pushes. iOS only plays `caf`/`aiff`/`wav` (not mp3), so ship the converted `.caf` files.
    public static func sound(forTemplateType templateType: String?) -> UNNotificationSound {
        switch templateType {
        case "1":
            return UNNotificationSound(named: UNNotificationSoundName("vih_tone_otp.caf"))
        case "2", "3":
            return UNNotificationSound(named: UNNotificationSoundName("vih_tone_promo.caf"))
        default:
            return .default
        }
    }

    /// Set the per-template tone on a notification's content from its `template_type` payload key.
    /// Call this from a `UNNotificationServiceExtension` (for backgrounded remote pushes — the
    /// APNs payload must include `mutable-content: 1`) or before presenting a local notification.
    public static func applyTone(to content: UNMutableNotificationContent) {
        let templateType = (content.userInfo["template_type"] as? String)
            ?? (content.userInfo["templ_typ"] as? String)
        content.sound = sound(forTemplateType: templateType)
    }
}
