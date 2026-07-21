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

    // Phone verification (silent network auth + SMS OTP fallback). See
    // docs/ios/phone-auth-backend-contract.md.
    public static let authStart = "auth/start/"
    public static let authFinish = "auth/finish/"
    public static let authOtpSend = "auth/otp/send/"
    public static let authOtpVerify = "auth/otp/verify/"

    /// Session-registry endpoint (architecture §3.3) mapping deviceId -> push token.
    public static let registerDeviceToken = "main/sdk-device-token/"

    /// Loan-approval (Ultravox voicebot) lives on a separate host.
    public static let loanApprovalURL = URL(string: "https://developer-portal.vihresearchlabs.ai/main/loan-approval/")!
    public static let callDetailsURL = URL(string: "https://developer-portal.vihresearchlabs.ai/main/call-details/")!
}
