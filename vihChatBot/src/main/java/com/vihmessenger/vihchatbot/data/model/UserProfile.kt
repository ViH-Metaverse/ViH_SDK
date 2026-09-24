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

/**
 * Body for `account/sdk-login/`.
 *
 * The attestation fields are nullable and Gson omits nulls, so a request without them is
 * byte-identical to what earlier SDK versions sent — which is what lets this ship before the
 * backend enforces attestation. Populate them via
 * [com.vihmessenger.vihchatbot.utils.SdkAttestation.acquire]; see [withAttestation].
 *
 * `attestation_key_id` is iOS App Attest only and is deliberately absent here.
 */
data class UserProfileRequest(
    @SerializedName("mobile") var mobile_number: String,
    @SerializedName("channel_id") var hash_code: String,
    @SerializedName("fcm_token") var fcm_token: String,
    // Device binding (F-13). The backend binds the issued token to this id and refuses the
    // token from a different device; it also feeds SdkDeviceClaim ("first device wins",
    // observe-only for now). A token minted without the claim authenticates as before, so
    // sending it is safe ahead of enforcement. Same UUID the SDK already uses for
    // sdk-device-token, so the two views of a device agree.
    @SerializedName("device_id") var device_id: String? = null,
    @SerializedName("attestation_platform") var attestation_platform: String? = null,
    @SerializedName("attestation_token") var attestation_token: String? = null,
    @SerializedName("attestation_nonce") var attestation_nonce: String? = null,
)

/**
 * Returns a copy carrying [attestation], or the receiver unchanged when attestation could not
 * be produced. Keeps every sign-in call site to a single line and one shape.
 */
fun UserProfileRequest.withAttestation(
    attestation: com.vihmessenger.vihchatbot.utils.SdkAttestation.Attestation?
): UserProfileRequest = if (attestation == null) this else copy(
    attestation_platform = attestation.platform,
    attestation_token = attestation.token,
    attestation_nonce = attestation.nonce,
)

/**
 * Body for account/email-login/. Two mutually exclusive credential shapes share this class
 * because the endpoint's contract differs per backend generation (see MOBILE_OTP_MIGRATION.md):
 *
 *  - **Cognito backends** (api.platform, api.prod.platform): send [cognito_id_token]. The OTP
 *    was issued and verified by Cognito; the backend only validates the resulting ID token.
 *  - **Backend-SMTP backends** (api.messenger, the `saas` flavour): send [email] + [otp]. The
 *    backend issues the code over its own SMTP via account/request-login-otp/ and verifies it
 *    here. Sending `cognito_id_token` to one of these yields 400 EC_VALIDATION_4006.
 *
 * Gson omits null fields, so only the populated pair is serialised. Build them through
 * [cognito] or [backendOtp] rather than the constructor so the wrong pair can't be sent.
 * `mobile` is carried on both paths as the key for hashcode-matched message delivery.
 */
data class EmailLoginRequest(
    @SerializedName("cognito_id_token") var cognito_id_token: String? = null,
    @SerializedName("email") var email: String? = null,
    @SerializedName("otp") var otp: String? = null,
    @SerializedName("mobile") var mobile: String,
    @SerializedName("channel_id") var channel_id: String,
    @SerializedName("fcm_token") var fcm_token: String,
) {
    companion object {
        /** Cognito token-exchange shape (staging / prod). */
        fun cognito(idToken: String, mobile: String, channelId: String, fcmToken: String) =
            EmailLoginRequest(
                cognito_id_token = idToken,
                mobile = mobile,
                channel_id = channelId,
                fcm_token = fcmToken,
            )

        /** Backend-SMTP OTP shape (saas). */
        fun backendOtp(
            email: String, otp: String, mobile: String, channelId: String, fcmToken: String
        ) = EmailLoginRequest(
            email = email,
            otp = otp,
            mobile = mobile,
            channel_id = channelId,
            fcm_token = fcmToken,
        )
    }
}

/** Body for account/request-login-otp/ — asks the backend to email a 6-digit code. */
data class RequestLoginOtpRequest(
    @SerializedName("email") var email: String,
)

/**
 * Response for account/request-login-otp/. Deliberately carries no account information: the
 * backend answers identically for a known and an unknown email to prevent account enumeration,
 * so the client must never infer "no such account" from this call.
 */
data class RequestLoginOtpResponse(
    @SerializedName("status") var status: Boolean = false,
    @SerializedName("message") var message: String? = null,
    @SerializedName("error_code") var error_code: String? = null,
)

/**
 * Response for account/email-login/. On a complete account `data` carries the existing
 * app-session tokens; for a new/incomplete account `account_status == "needs_profile"`
 * and `data` may be null (the client then collects the remaining profile fields).
 */
data class EmailLoginResponse(
    @SerializedName("status") var status: Boolean,
    @SerializedName("message") var message: String,
    // Present on the backend-SMTP path; the OTP screen maps EC_AUTH_4014 (wrong code) and
    // EC_AUTH_4015 (expired) to different prompts. Null on the Cognito backends.
    @SerializedName("error_code") var error_code: String? = null,
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
