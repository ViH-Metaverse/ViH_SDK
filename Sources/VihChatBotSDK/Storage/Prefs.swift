import Foundation
import Security

/// iOS equivalent of `utils/sharedPreference/Prefs.kt`. Sensitive values
/// (`accessToken`, `refreshToken`, `userProfile`, `phoneNumber`) live in the
/// Keychain — the parity for Android's `EncryptedSharedPreferences` (AES-256-GCM).
/// Non-sensitive flags live in `UserDefaults`.
public final class Prefs {

    public static let shared = Prefs()
    private let defaults = UserDefaults.standard
    private let keychainService = "com.vihmessenger.vihchatbot.secure"

    private init() {
        migrateLegacyPlaintextPII()
    }

    // MARK: - Sensitive (Keychain)

    public var accessToken: String? {
        get { keychainRead(AppConstants.userAccessToken) }
        set { keychainWrite(AppConstants.userAccessToken, value: newValue) }
    }

    public var refreshToken: String? {
        get { keychainRead(AppConstants.refreshAccessToken) }
        set { keychainWrite(AppConstants.refreshAccessToken, value: newValue) }
    }

    public var userProfile: String? {
        get { keychainRead(AppConstants.userProfileSharedPref) }
        set { keychainWrite(AppConstants.userProfileSharedPref, value: newValue) }
    }

    public var phoneNumber: String? {
        get { keychainRead(AppConstants.phoneNumber) }
        set { keychainWrite(AppConstants.phoneNumber, value: newValue) }
    }

    /// SECURITY (VAPT F-15, CWE-311): name and email are PII and used to live in
    /// `UserDefaults` — an unencrypted plist inside the app container that is included in
    /// unencrypted iTunes/Finder backups and readable on a jailbroken device. They belong
    /// in the Keychain alongside the other identity fields.
    ///
    /// `migrateLegacyPlaintextPII()` moves values written by earlier builds and deletes the
    /// plaintext copies, so upgrading installs do not leave the old values on disk.
    public var name: String? {
        get { keychainRead(AppConstants.userName) }
        set { keychainWrite(AppConstants.userName, value: newValue) }
    }

    public var email: String? {
        get { keychainRead(AppConstants.userEmail) }
        set { keychainWrite(AppConstants.userEmail, value: newValue) }
    }

    // MARK: - Non-sensitive (UserDefaults)

    public var vihSettings: String? {
        get { defaults.string(forKey: AppConstants.vihSettingSharedPref) }
        set { defaults.set(newValue, forKey: AppConstants.vihSettingSharedPref) }
    }

    public var hashcode: String? {
        get { defaults.string(forKey: AppConstants.hashcodeExtra) }
        set { defaults.set(newValue, forKey: AppConstants.hashcodeExtra) }
    }

    public var userProfileUrl: String? {
        get { defaults.string(forKey: "USER_PROFILE_URL") }
        set { defaults.set(newValue, forKey: "USER_PROFILE_URL") }
    }

    public var notificationIcon: Int {
        get { defaults.integer(forKey: "NOTIFICATION_ICON") }
        set { defaults.set(newValue, forKey: "NOTIFICATION_ICON") }
    }

    public var isSDK: Bool {
        get { defaults.bool(forKey: AppConstants.isSdkMode) }
        set { defaults.set(newValue, forKey: AppConstants.isSdkMode) }
    }

    public var shortcutPromptCount: Int {
        get { defaults.integer(forKey: AppConstants.prefShortcutPromptCount) }
        set { defaults.set(newValue, forKey: AppConstants.prefShortcutPromptCount) }
    }

    public var shortcutDeniedCount: Int {
        get { defaults.integer(forKey: AppConstants.prefShortcutDeniedCount) }
        set { defaults.set(newValue, forKey: AppConstants.prefShortcutDeniedCount) }
    }

    public var shortcutDeniedByUser: Bool {
        get { defaults.bool(forKey: AppConstants.prefShortcutDeniedByUser) }
        set { defaults.set(newValue, forKey: AppConstants.prefShortcutDeniedByUser) }
    }

    public var shortcutAddedSuccessfully: Bool {
        get { defaults.bool(forKey: AppConstants.prefShortcutAddedSuccessfully) }
        set { defaults.set(newValue, forKey: AppConstants.prefShortcutAddedSuccessfully) }
    }

    /// Stable per-install identifier used by the new AWS data path (FCM session
    /// registry maps deviceId -> push token; IoT Core topic is /u/{deviceId}/inbox).
    /// Lazily generated on first read so existing installs upgrade transparently.
    public var deviceId: String {
        if let existing = defaults.string(forKey: AppConstants.deviceId), !existing.isEmpty {
            return existing
        }
        let fresh = UUID().uuidString
        defaults.set(fresh, forKey: AppConstants.deviceId)
        return fresh
    }

    /// Most recent push token (APNs hex string or FCM token if the host wires
    /// Firebase Messaging). Cached so sign-in can re-register the token without
    /// waiting for the next rotation.
    public var fcmToken: String? {
        get { defaults.string(forKey: AppConstants.fcmToken) }
        set { defaults.set(newValue, forKey: AppConstants.fcmToken) }
    }

    public var fcmTokenRegistered: Bool {
        get { defaults.bool(forKey: AppConstants.fcmTokenRegistered) }
        set { defaults.set(newValue, forKey: AppConstants.fcmTokenRegistered) }
    }

    // Channel hashkey the device token was last successfully registered under. When the
    // active hashcode differs, the device must re-register so shoots on the new channel
    // aren't rejected as "not registered on the channel".
    public var fcmRegisteredHashcode: String? {
        get { defaults.string(forKey: AppConstants.fcmRegisteredHashcode) }
        set { defaults.set(newValue, forKey: AppConstants.fcmRegisteredHashcode) }
    }

    public func clearAllPreferences() {
        let keysToRemove = [
            AppConstants.vihSettingSharedPref,
            AppConstants.hashcodeExtra,
            "USER_NAME", "USER_EMAIL", "USER_PROFILE_URL", "NOTIFICATION_ICON",
            AppConstants.fcmTokenRegistered,
            AppConstants.fcmRegisteredHashcode
        ]
        keysToRemove.forEach { defaults.removeObject(forKey: $0) }

        // Sensitive — Keychain
        keychainWrite(AppConstants.userAccessToken, value: nil)
        keychainWrite(AppConstants.refreshAccessToken, value: nil)
        keychainWrite(AppConstants.userProfileSharedPref, value: nil)
        keychainWrite(AppConstants.phoneNumber, value: nil)
        keychainWrite(AppConstants.userName, value: nil)
        keychainWrite(AppConstants.userEmail, value: nil)
        // Keep DEVICE_ID and FCM_TOKEN — they belong to the install, not the user.
    }

    /// Moves name/email written by pre-F-15 builds out of `UserDefaults` and into the
    /// Keychain, then removes the plaintext copies.
    ///
    /// Without this an upgrading install keeps its PII sitting in the unencrypted plist
    /// forever — the new accessors would simply never read it. Runs once at init; the
    /// `defaults` lookup is cheap and returns nil on every subsequent launch.
    private func migrateLegacyPlaintextPII() {
        if let legacyName = defaults.string(forKey: "USER_NAME"), !legacyName.isEmpty {
            if keychainRead(AppConstants.userName) == nil {
                keychainWrite(AppConstants.userName, value: legacyName)
            }
            defaults.removeObject(forKey: "USER_NAME")
        }
        if let legacyEmail = defaults.string(forKey: "USER_EMAIL"), !legacyEmail.isEmpty {
            if keychainRead(AppConstants.userEmail) == nil {
                keychainWrite(AppConstants.userEmail, value: legacyEmail)
            }
            defaults.removeObject(forKey: "USER_EMAIL")
        }
    }

    // MARK: - Keychain primitives

    private func keychainRead(_ key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: key,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecReturnData as String: true
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess,
              let data = result as? Data,
              let str = String(data: data, encoding: .utf8) else { return nil }
        return str
    }

    private func keychainWrite(_ key: String, value: String?) {
        let baseQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: key
        ]
        // Always delete first; idempotent regardless of existing entry.
        SecItemDelete(baseQuery as CFDictionary)
        guard let value = value, let data = value.data(using: .utf8) else { return }

        var attributes = baseQuery
        attributes[kSecValueData as String] = data
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(attributes as CFDictionary, nil)
    }
}
