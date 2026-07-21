package com.vihmessenger.vihchatbot.data.model

import com.google.gson.annotations.SerializedName

data class UserProfileUpdateResponse(
    @SerializedName("status") var status: Boolean,
    @SerializedName("message") var message: String,
    @SerializedName("data") var data: UserProfileModel,
)
data class UserProfileResponse(
    @SerializedName("status") var status: Boolean,
    @SerializedName("message") var message: String,
    @SerializedName("data") var data: UserProfileData,
)

data class UserProfileData(
    @SerializedName("user") var user: UserProfileModel,
    @SerializedName("access_token") var access_token: String,
    @SerializedName("refresh") var refresh: String,
)

data class UserProfileModel(
    @SerializedName("id") var id: Int,
    @SerializedName("created_at") var created_at: String,
    @SerializedName("updated_at") var updated_at: String,
    @SerializedName("user_name") var user_name: String?,
    @SerializedName("username") var username: String?,
    @SerializedName("full_name") var full_name: String?,
    @SerializedName("email") var email: String?,
    @SerializedName("active_status") var status: Boolean,
    @SerializedName("profile_image") var profile_image: String?,
    @SerializedName("user_profile_image") var user_profile_image: String?,
    @SerializedName("metaverse_status") var metaverse: Boolean,
    @SerializedName("billing_status") var billing: Boolean,
    @SerializedName("shoping_status") var shopping: Boolean,
    @SerializedName("mobile") var mobile: String,
    @SerializedName("hash_code") var hash_code: String
)


data class UserProfilePatchData(
//    @SerializedName("user_name") var username: String,
    @SerializedName("active_status") var status: Boolean,
    @SerializedName("metaverse_status") var metaverse: Boolean,
    @SerializedName("billing_status") var billing: Boolean,
    @SerializedName("shoping_status") var shopping: Boolean,
)

data class UserProfilePatchUsername(
    @SerializedName("username") var username: String,
)

data class UpdateUserProfile(
    @SerializedName("username") var username: String,
    @SerializedName("email") var email: String
)

data class UserProfileRequest(
    @SerializedName("mobile") var mobile_number: String,
    @SerializedName("channel_id") var hash_code: String,
    @SerializedName("fcm_token") var fcm_token: String,
)

/**
 * Body for the email-OTP token-exchange endpoint (account/email-login/).
 * The Cognito ID token is the verified email credential; `mobile` is carried only as
 * the key for hashcode-matched message delivery (it is not OTP'd).
 */
data class EmailLoginRequest(
    @SerializedName("cognito_id_token") var cognito_id_token: String,
    @SerializedName("mobile") var mobile: String,
    @SerializedName("channel_id") var channel_id: String,
    @SerializedName("fcm_token") var fcm_token: String,
)

/**
 * Response for account/email-login/. On a complete account `data` carries the existing
 * app-session tokens; for a new/incomplete account `account_status == "needs_profile"`
 * and `data` may be null (the client then collects the remaining profile fields).
 */
data class EmailLoginResponse(
    @SerializedName("status") var status: Boolean,
    @SerializedName("message") var message: String,
    @SerializedName("account_status") var account_status: String? = null,
    @SerializedName("data") var data: UserProfileData? = null,
)

/** Body for account/subscribe-channel/ (subscribe current user to a channel by hashcode). */
data class SubscribeChannelRequest(
    @SerializedName("channel_id") var channel_id: String,
)

data class SubscribeChannelResponse(
    @SerializedName("status") var status: Boolean,
    @SerializedName("message") var message: String,
)

data class LogoutDataModel(
    @SerializedName("status") var status: String,
    @SerializedName("channel_id") var message: String,
)
