import Foundation

/// Wire models for phone verification. See
/// docs/ios/phone-auth-backend-contract.md for the full contract.

public struct AuthStartRequest: Codable {
    public var phone: String
    public var channel_id: String
    public init(phone: String, channel_id: String) {
        self.phone = phone
        self.channel_id = channel_id
    }
}

public struct AuthStartResponse: Codable {
    /// "sna" or "otp".
    public var method: String
    public var request_id: String
    /// Present only when `method == "sna"`: the carrier check URL to open over cellular.
    public var check_url: String?
}

public struct AuthFinishRequest: Codable {
    public var request_id: String
    public init(request_id: String) { self.request_id = request_id }
}

public struct AuthVerifyResponse: Codable {
    public var verified: Bool
    public var message: String?
}

public struct OtpSendRequest: Codable {
    public var request_id: String
    public init(request_id: String) { self.request_id = request_id }
}

public struct OtpSendResponse: Codable {
    public var sent: Bool
    public var request_id: String?
    public var message: String?
}

public struct OtpVerifyRequest: Codable {
    public var request_id: String
    public var code: String
    public init(request_id: String, code: String) {
        self.request_id = request_id
        self.code = code
    }
}
