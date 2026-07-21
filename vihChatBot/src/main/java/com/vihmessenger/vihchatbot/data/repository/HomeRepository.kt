package com.vihmessenger.vihchatbot.data.repository

import BaseActivity
import com.vihmessenger.vihchatbot.data.model.EmailLoginRequest
import com.vihmessenger.vihchatbot.data.model.EmailLoginResponse
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
}