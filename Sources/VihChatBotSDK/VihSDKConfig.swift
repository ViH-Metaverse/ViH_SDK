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

    /// SHA-256 SubjectPublicKeyInfo pins keyed by host, enforced by
    /// `CertificatePinningDelegate`. Defaults to `VihSDKConfig.defaultCertificatePins`,
    /// which pins the Amazon CAs that front the VIH API hosts (VAPT F-03).
    ///
    /// Passing `[:]` disables pinning for every host and is treated as a misconfiguration:
    /// debug builds trap on it, release builds log an error. See `defaultCertificatePins`
    /// for why the *CAs* are pinned rather than the leaf certificates.
    public let certificatePins: [String: [String]]

    /// Mask the app-switcher snapshot and blur the UI while the screen is being recorded
    /// or mirrored. On by default — this SDK renders OTPs. See `ScreenCapturePolicy`.
    public let blockScreenCapture: Bool

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
        certificatePins: [String: [String]] = VihSDKConfig.defaultCertificatePins,
        blockScreenCapture: Bool = true,
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
        self.blockScreenCapture = blockScreenCapture
        self.cognitoUserPoolId = cognitoUserPoolId
        self.cognitoAppClientId = cognitoAppClientId
        self.cognitoRegion = cognitoRegion
    }

    // MARK: - Certificate pinning defaults

    /// Pins for the VIH API hosts.
    ///
    /// **These are CA pins, not leaf pins, and that is deliberate.** Both hosts sit behind
    /// AWS Certificate Manager, which auto-renews and generates a *new key pair* at each
    /// renewal — pinning the leaf would hard-fail every shipped client the moment ACM
    /// rotated (the production leaf expires 2026-11-28). Pinning the issuing intermediate
    /// plus `Amazon Root CA 1` survives rotation while still defeating the actual threat
    /// this control exists for: a user- or MDM-installed CA performing interception. A
    /// Burp/mitmproxy CA does not chain to Amazon Root CA 1, so it is rejected.
    ///
    /// `Amazon Root CA 1` is valid until 2037 and is the backup pin that keeps clients
    /// working if AWS moves the host to a different `Amazon RSA 2048 Mxx` intermediate.
    ///
    /// Verify with:
    /// ```
    /// openssl s_client -connect <host>:443 -servername <host> -showcerts </dev/null \
    ///   | openssl x509 -pubkey -noout \
    ///   | openssl pkey -pubin -outform der \
    ///   | openssl dgst -sha256 -binary | openssl enc -base64
    /// ```
    public static let defaultCertificatePins: [String: [String]] = [
        "api.platform.vihresearchlabs.ai": [
            // Amazon RSA 2048 M04 (current issuing intermediate)
            "G9LNNAql897egYsabashkzUCTEJkWBzgoEtk8X/678c=",
            // Amazon Root CA 1 (backup — survives intermediate rotation, valid to 2037)
            "++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI="
        ],
        "api.dev.platform.vihresearchlabs.ai": [
            // Amazon RSA 2048 M01 (current issuing intermediate)
            "DxH4tt40L+eduF6szpY6TONlxhZhBd+pJ9wbHlQ2fuw=",
            // Amazon Root CA 1 (backup)
            "++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI="
        ]
    ]
}
