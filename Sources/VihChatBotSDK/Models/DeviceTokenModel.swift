import Foundation

/// Body for the session-registry endpoint (architecture §3.3).
/// `platform` defaults to "ios" — Android sends "android".
public struct DeviceTokenRequest: Codable {
    public let deviceId: String
    public let fcmToken: String
    public let hashcode: String?
    public let platform: String
    public let sdkVersion: String

    public init(
        deviceId: String,
        fcmToken: String,
        hashcode: String?,
        platform: String = "ios",
        sdkVersion: String
    ) {
        self.deviceId = deviceId
        self.fcmToken = fcmToken
        self.hashcode = hashcode
        self.platform = platform
        self.sdkVersion = sdkVersion
    }

    enum CodingKeys: String, CodingKey {
        case deviceId = "device_id"
        case fcmToken = "fcm_token"
        case hashcode, platform
        case sdkVersion = "sdk_version"
    }
}

public struct DeviceTokenResponse: Codable {
    public var status: Bool
    public var message: String?
}
