package com.vihmessenger.vihchatbot.data.repository

import BaseActivity
import com.vihmessenger.vihchatbot.data.model.EmailLoginRequest
import com.vihmessenger.vihchatbot.data.model.EmailLoginResponse
import com.google.gson.Gson
import com.vihmessenger.vihchatbot.data.model.GenericStatusResponse
import com.vihmessenger.vihchatbot.data.model.RequestLoginOtpRequest
import com.vihmessenger.vihchatbot.data.model.RequestLoginOtpResponse
import com.vihmessenger.vihchatbot.data.model.SubscribeChannelRequest
import com.vihmessenger.vihchatbot.data.model.SubscribeChannelResponse
import com.vihmessenger.vihchatbot.data.model.LogoutDataModel
import com.vihmessenger.vihchatbot.data.model.SdkFeatureResponse
import com.vihmessenger.vihchatbot.data.model.UpdateUserProfile
import com.vihmessenger.vihchatbot.data.model.UserProfilePatchUsername
import com.vihmessenger.vihchatbot.data.model.UserProfileRequest
import com.vihmessenger.vihchatbot.data.model.UserProfileResponse
import com.vihmessenger.vihchatbot.data.model.UserProfileUpdateResponse
import com.vihmessenger.vihchatbot.data.services.ApiService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class HomeRepository(
    private val apiService: ApiService, private val baseActivity: BaseActivity?
) : BaseRepository(baseActivity, apiService) {

    suspend fun getCustomerHome(showBlockingLoader: Boolean, hashCode: String): SdkFeatureResponse {
        return doSafeAPIRequest(
            call = { apiService.getSdkFeatures(hashCode) }, showBlockingLoader = showBlockingLoader
        ) ?: throw NoConnectionException("Failed to get SDK features")
    }

    suspend fun createUserProfile(
        showBlockingLoader: Boolean, body: UserProfileRequest
    ): UserProfileResponse {
        return doSafeAPIRequest(
            call = { apiService.createUserProfile(body) }, showBlockingLoader = showBlockingLoader
        ) ?: throw NoConnectionException("Failed to create user profile")
    }

    /** Exchanges a verified Cognito ID token (+ mobile for delivery) for app-session tokens. */
    suspend fun emailLogin(
        showBlockingLoader: Boolean, body: EmailLoginRequest
    ): EmailLoginResponse {
        return doSafeAPIRequest(
            call = { apiService.emailLogin(body) }, showBlockingLoader = showBlockingLoader
        ) ?: throw NoConnectionException("Failed to complete email login")
    }

    /**
     * Backend-SMTP OTP login (saas). Deliberately bypasses [doSafeAPIRequest].
     *
     * That helper maps **every** 401 to "Authentication failed" and calls `handleSessionExpired()`,
     * which clears prefs and bounces to login. On this endpoint 401 is the *normal* answer to a
     * wrong (EC_AUTH_4014) or expired (EC_AUTH_4015) code — routing that through the session-expiry
     * path would throw the user out of the OTP screen instead of letting them retype the code. It
     * also discards `error_code`, which is the only way to tell "wrong code" from "expired code".
     *
     * So the error body is parsed into [EmailLoginResponse] and handed to the caller intact.
     */
    suspend fun emailLoginWithOtp(body: EmailLoginRequest): EmailLoginResponse {
        val response = apiService.emailLogin(body)
        response.body()?.takeIf { response.isSuccessful }?.let { return it }
        return parseAuthError(response.errorBody()?.string(), "Login failed, please try again")
    }

    /** Asks the backend to email a login OTP. Same non-throwing error handling as above. */
    suspend fun requestLoginOtp(email: String): RequestLoginOtpResponse {
        val response = apiService.requestLoginOtp(RequestLoginOtpRequest(email = email))
        response.body()?.takeIf { response.isSuccessful }?.let { return it }
        // 429 is throttling (5/min) and carries no JSON body worth showing.
        if (response.code() == 429) {
            return RequestLoginOtpResponse(
                status = false, message = "Too many requests, wait a minute"
            )
        }
        val parsed = parseAuthError(response.errorBody()?.string(), "Couldn't send the code, try again")
        return RequestLoginOtpResponse(
            status = false, message = parsed.message, error_code = parsed.error_code
        )
    }

    /**
     * Parses a DRF `{status, message, error_code}` error body. A non-JSON body (an HTML 502 page,
     * say) must not surface a Gson stack trace to the user, so it falls back to [fallback].
     */
    private fun parseAuthError(body: String?, fallback: String): EmailLoginResponse {
        val parsed = body?.takeIf { it.isNotBlank() }?.let {
            runCatching { Gson().fromJson(it, EmailLoginResponse::class.java) }.getOrNull()
        }
        return EmailLoginResponse(
            status = false,
            message = parsed?.message?.takeIf { it.isNotBlank() } ?: fallback,
            error_code = parsed?.error_code,
        )
    }

    /** Subscribes the authenticated user to a channel (Settings hashkey switch). */
    suspend fun subscribeChannel(
        showBlockingLoader: Boolean, body: SubscribeChannelRequest
    ): SubscribeChannelResponse {
        return doSafeAPIRequest(
            call = { apiService.subscribeChannel(body) }, showBlockingLoader = showBlockingLoader
        ) ?: throw NoConnectionException("Failed to subscribe to channel")
    }

    suspend fun updateUserProfile(
        showBlockingLoader: Boolean, id: Int, body: UpdateUserProfile
    ): UserProfileUpdateResponse {
        return doSafeAPIRequest(
            call = { apiService.updateUserProfile(body) },
            showBlockingLoader = showBlockingLoader
        ) ?: throw NoConnectionException("Failed to update user profile")
    }


    suspend fun updateUserProfileImage(
        showBlockingLoader: Boolean, id: Int, part: MultipartBody.Part
    ): UserProfileUpdateResponse {
        return doSafeAPIRequest(
            call = { apiService.updateUserProfileImage(part) },
            showBlockingLoader = showBlockingLoader
        ) ?: throw NoConnectionException("Failed to update profile image")
    }

    suspend fun createProfile(
        showBlockingLoader: Boolean,
        fullName: String,
        email: String? = null,
        user_profile_image: MultipartBody.Part,
    ): UserProfileUpdateResponse {
        return doSafeAPIRequest(
            call = {
                val partMap = HashMap<String, RequestBody>()
                partMap["full_name"] = fullName.toRequestBody("text/plain".toMediaTypeOrNull())
                if (!email.isNullOrBlank()) {
                    partMap["email"] = email.toRequestBody("text/plain".toMediaTypeOrNull())
                }
                apiService.createProfile(
                    partMap,
                    user_profile_image
                )
            },
            showBlockingLoader = showBlockingLoader
        ) ?: throw NoConnectionException("Failed to create profile")
    }

    suspend fun updateProfileSelective(
        showBlockingLoader: Boolean,
        fields: HashMap<String, RequestBody>,
        image: MultipartBody.Part? = null
    ): UserProfileUpdateResponse {
        return doSafeAPIRequest(
            call = { apiService.updateProfileSelective(fields, image) },
            showBlockingLoader = showBlockingLoader
        ) ?: throw NoConnectionException("Failed to update profile")
    }

    suspend fun userLogout(
        showBlockingLoader: Boolean,
        refresh_token: String
    ): LogoutDataModel {
        return doSafeAPIRequest(
            call = { apiService.userLogout(refresh_token) }, showBlockingLoader = showBlockingLoader
        ) ?: throw NoConnectionException("Failed to create user profile")
    }

    /** Block/unblock this enterprise for the current user. */
    suspend fun blacklistEnterprise(
        showBlockingLoader: Boolean, enterprisePk: Int, blacklist: Boolean
    ): GenericStatusResponse {
        return doSafeAPIRequest(
            call = { apiService.blacklistEnterprise(enterprisePk, blacklist.toString()) },
            showBlockingLoader = showBlockingLoader
        ) ?: throw NoConnectionException("Failed to update block state")
    }

    /** Mute/unmute this enterprise for the current (user, channel). */
    suspend fun muteEnterprise(
        showBlockingLoader: Boolean, enterpriseId: Int, muteStatus: Boolean
    ): GenericStatusResponse {
        return doSafeAPIRequest(
            call = { apiService.muteEnterprise(enterpriseId, muteStatus) },
            showBlockingLoader = showBlockingLoader
        ) ?: throw NoConnectionException("Failed to update mute state")
    }

    /** Set promotional opt-in for this (user, channel, enterprise). optIn is the inverse
     *  of the is_promotional_message_blocked read flag. */
    suspend fun updateEnterprisePromotional(
        showBlockingLoader: Boolean, optIn: Boolean, enterpriseId: Int, channelId: String
    ): GenericStatusResponse {
        return doSafeAPIRequest(
            call = { apiService.updateEnterprisePromotional(optIn, enterpriseId, channelId) },
            showBlockingLoader = showBlockingLoader
        ) ?: throw NoConnectionException("Failed to update promotional preference")
    }
}