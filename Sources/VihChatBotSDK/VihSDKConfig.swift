import Foundation

/// Runtime configuration for the SDK. Replaces Android's `BuildConfig` constants
/// (API_BASE_URL, HASHCODE, BUGFENDER_KEY, SDK_VERSION, DEBUG). Host apps configure
/// this once during launch via `VihChatBotSDK.configure(_:)`.
public struct VihSDKConfig {
    public let apiBaseURL: URL
    public let hashcode: String
    public let bugfenderKey: String
    public let sdkVersion: String
    public let isDebug: Bool

    /// Optional SHA-256 public-key pins keyed by host. Mirrors the OkHttp
    /// `CertificatePinner` template — wire `URLSessionDelegate` to enforce.
    public let certificatePins: [String: [String]]

    /// Cognito (email-OTP auth) configuration. Empty values disable email-OTP and leave
    /// the existing phone auth path intact. Mirrors Android's COGNITO_* BuildConfig fields.
    public let cognitoUserPoolId: String
    public let cognitoAppClientId: String
    public let cognitoRegion: String

    public init(
        apiBaseURL: URL,
        hashcode: String,
        bugfenderKey: String = "",
        sdkVersion: String,
        isDebug: Bool,
        certificatePins: [String: [String]] = [:],
        cognitoUserPoolId: String = "",
        cognitoAppClientId: String = "",
        cognitoRegion: String = "us-east-1"
    ) {
        self.apiBaseURL = apiBaseURL
        self.hashcode = hashcode
        self.bugfenderKey = bugfenderKey
        self.sdkVersion = sdkVersion
        self.isDebug = isDebug
        self.certificatePins = certificatePins
        self.cognitoUserPoolId = cognitoUserPoolId
        self.cognitoAppClientId = cognitoAppClientId
        self.cognitoRegion = cognitoRegion
    }
}
