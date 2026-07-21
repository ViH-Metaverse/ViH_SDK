import Foundation

/// Mirrors `services/DeviceTokenRegistrar.kt`. Push the (deviceId, fcmToken)
/// pair to the session registry (architecture §3.3).
///
/// Two entry points cover the rotation paths:
///   - `onNewToken` fires whenever APNs/FCM rotates the token. Auth may not
///     be ready yet, so the call is best-effort and the cached token +
///     `fcmTokenRegistered = false` flag ensure a retry happens after login.
///   - `registerCachedTokenIfNeeded` is called after sign-in to flush any
///     token that arrived before auth was available.
public final class DeviceTokenRegistrar {

    public static let shared = DeviceTokenRegistrar()
    private init() {}

    public func onNewToken(_ token: String) {
        let prefs = VihChatBotSDK.shared.prefs
        prefs?.fcmToken = token
        prefs?.fcmTokenRegistered = false
        attemptRegistration()
    }

    public func registerCachedTokenIfNeeded() {
        let prefs = VihChatBotSDK.shared.prefs
        guard let token = prefs?.fcmToken, !token.isEmpty else { return }
        // Re-register when the token hasn't been ack'd yet, OR when the channel hashkey
        // changed since the last successful registration. The push registry is keyed per
        // (channel, device), so after a channel switch (login with a different hashkey, or
        // the Settings channel switch) the device must register again or shoots on the new
        // channel are rejected as "not registered on the channel".
        let currentHash = prefs?.hashcode ?? VihChatBotSDK.shared.config?.hashcode
        let registeredForCurrentChannel =
            (prefs?.fcmTokenRegistered == true) && (prefs?.fcmRegisteredHashcode == currentHash)
        guard !registeredForCurrentChannel else { return }
        attemptRegistration()
    }

    private func attemptRegistration() {
        guard let prefs = VihChatBotSDK.shared.prefs,
              let cfg = VihChatBotSDK.shared.config,
              let token = prefs.fcmToken, !token.isEmpty else { return }
        let deviceId = prefs.deviceId
        let hashcode = prefs.hashcode ?? cfg.hashcode
        let sdkVersion = cfg.sdkVersion
        let isAuthenticated = !((prefs.accessToken ?? "").isEmpty)

        Task {
            // Primary path: the backend delivers FCM pushes using the fcm_token
            // stored on the user PROFILE. The FCM token frequently mints/rotates
            // AFTER login, and the dedicated sdk-device-token route 404s on
            // staging — so without this sync the backend keeps a stale/empty
            // token and shoots never arrive. PATCH needs auth, so guard on it.
            if isAuthenticated {
                do {
                    _ = try await APIClient.shared.apiService.updateProfileSelective(
                        fields: ["fcm_token": token],
                        imageData: nil, imageMimeType: nil, imageFilename: nil
                    )
                    prefs.fcmTokenRegistered = true
                    CorrelationLogger.info(tag: "DeviceTokenRegistrar", message: "fcm token synced to profile")
                } catch {
                    CorrelationLogger.warn(tag: "DeviceTokenRegistrar", message: "profile fcm token sync failed", error: error)
                }
            }

            // Also try the dedicated session registry (still 404s on staging
            // until the AWS Phase 1 route is deployed; harmless when it isn't).
            do {
                _ = try await APIClient.shared.apiService.registerDeviceToken(
                    body: DeviceTokenRequest(
                        deviceId: deviceId,
                        fcmToken: token,
                        hashcode: hashcode,
                        sdkVersion: sdkVersion
                    )
                )
                prefs.fcmTokenRegistered = true
                // Record the channel this device registration was for, so a later channel
                // switch triggers a fresh registration on the new channel.
                prefs.fcmRegisteredHashcode = hashcode
                CorrelationLogger.info(tag: "DeviceTokenRegistrar", message: "device token registered")
            } catch let error as APIError where error.message == "Resource not found" {
                // The session-registry route is part of an in-progress AWS Phase 1
                // migration (architecture §3.3). Both Android and iOS will 404 here
                // until the backend deploys it. Demote to info so it stops looking
                // like a real error.
                CorrelationLogger.info(
                    tag: "DeviceTokenRegistrar",
                    message: "session-registry endpoint not deployed yet (expected on staging)"
                )
            } catch {
                // Persistent `fcmTokenRegistered=false` lets the next launch or
                // login retry — matches Android behaviour.
                CorrelationLogger.warn(
                    tag: "DeviceTokenRegistrar",
                    message: "device token registration failed",
                    error: error
                )
            }
        }
    }
}
