import Foundation

public struct UserProfileUpdateResponse: Codable {
    public var status: Bool
    public var message: String
    public var data: UserProfileModel
}

public struct UserProfileResponse: Codable {
    public var status: Bool
    public var message: String
    public var data: UserProfileData
}

public struct UserProfileData: Codable {
    public var user: UserProfileModel
    public var access_token: String
    public var refresh: String
}

public struct UserProfileModel: Codable {
    public var id: Int
    public var created_at: String?
    public var updated_at: String?
    public var user_name: String?
    public var username: String?
    public var full_name: String?
    public var email: String?
    public var status: Bool?
    public var profile_image: String?
    public var user_profile_image: String?
    public var metaverse: Bool?
    public var billing: Bool?
    public var shopping: Bool?
    public var mobile: String
    public var hash_code: String?

    enum CodingKeys: String, CodingKey {
        case id, created_at, updated_at, user_name, username, full_name, email
        case status = "active_status"
        case profile_image, user_profile_image
        case metaverse = "metaverse_status"
        case billing = "billing_status"
        case shopping = "shoping_status"
        case mobile, hash_code
    }
}

public struct UserProfilePatchData: Codable {
    public var status: Bool
    public var metaverse: Bool
    public var billing: Bool
    public var shopping: Bool

    enum CodingKeys: String, CodingKey {
        case status = "active_status"
        case metaverse = "metaverse_status"
        case billing = "billing_status"
        case shopping = "shoping_status"
    }
}

public struct UserProfilePatchUsername: Codable {
    public var username: String
}

public struct UpdateUserProfile: Codable {
    public var username: String
    public var email: String
}

public struct UserProfileRequest: Codable {
    public var mobile_number: String
    public var hash_code: String
    public var fcm_token: String

    enum CodingKeys: String, CodingKey {
        case mobile_number = "mobile"
        case hash_code = "channel_id"
        case fcm_token
    }

    public init(mobile_number: String, hash_code: String, fcm_token: String) {
        self.mobile_number = mobile_number
        self.hash_code = hash_code
        self.fcm_token = fcm_token
    }
}

/// Body for the email-OTP token-exchange endpoint (`account/email-login/`). The Cognito
/// ID token is the verified email credential; `mobile` is carried only as the key for
/// hashcode-matched message delivery (it is not OTP'd).
public struct EmailLoginRequest: Codable {
    public var cognito_id_token: String
    public var mobile: String
    public var channel_id: String
    public var fcm_token: String

    public init(cognito_id_token: String, mobile: String, channel_id: String, fcm_token: String) {
        self.cognito_id_token = cognito_id_token
        self.mobile = mobile
        self.channel_id = channel_id
        self.fcm_token = fcm_token
    }
}

/// Response for `account/email-login/`. On a complete account `data` carries the existing
/// app-session tokens; for a new/incomplete account `account_status == "needs_profile"`.
public struct EmailLoginResponse: Codable {
    public var status: Bool
    public var message: String
    public var account_status: String?
    public var data: UserProfileData?
}

/// Body / response for `account/subscribe-channel/` (subscribe current user to a channel).
public struct SubscribeChannelRequest: Codable {
    public var channel_id: String
    public init(channel_id: String) { self.channel_id = channel_id }
}

public struct SubscribeChannelResponse: Codable {
    public var status: Bool
    public var message: String
}

public struct LogoutDataModel: Codable {
    public var status: String
    public var message: String

    enum CodingKeys: String, CodingKey {
        case status
        case message = "channel_id"
    }
}
