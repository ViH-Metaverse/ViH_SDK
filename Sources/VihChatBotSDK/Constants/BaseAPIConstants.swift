import Foundation

/// Mirrors `com.vihmessenger.vihchatbot.constants.BaseAPIConstants`.
/// `baseURL` is sourced from `VihSDKConfig` instead of BuildConfig.
public enum BaseAPIConstants {
    // Backend has no api/ namespace; this route lives under developer/ and is
    // SimpleRouter-backed (trailing slash required — see ApiService.getSdkFeatures).
    public static let sdkFeatures = "developer/channels-tracking-code-sdk-feature/"
    public static let mainChat = "main/chat/"
    public static let industries = "main/industries/"
    public static let enterprises = "main/enterprise-details/"
    public static let chatHistory = "main/chat-history/"
    public static let mainChatList = "main/get-user-session/"
    public static let mainDiscoverList = "main/enterprises/"
    public static let userProfile = "account/profile/"
    public static let userSignupLogin = "account/signup-login/"

    /// Email-OTP token exchange: posts a verified Cognito ID token (+ mobile for
    /// hashcode-matched delivery) and returns the existing app-session tokens.
    public static let emailLogin = "account/email-login/"
    public static let subscribeChannel = "account/subscribe-channel/"
    public static let userLogout = "account/logout/"

    // Per-enterprise state mutations (read side is the four flags on EnterPriseModel).
    public static let userBlacklistEnterprise = "developer/user-blacklist-enterprise/"
    public static let muteEnterprise = "main/mute-enterprise-user-channel/"
    // promotional_opt_in is the INVERSE of the read flag is_promotional_message_blocked.
    public static let userChannelEnterpriseConfig = "main/user-channel-enterprise-configuration/"

    /// Session-registry endpoint (architecture §3.3) mapping deviceId -> push token.
    public static let registerDeviceToken = "main/sdk-device-token/"
}
