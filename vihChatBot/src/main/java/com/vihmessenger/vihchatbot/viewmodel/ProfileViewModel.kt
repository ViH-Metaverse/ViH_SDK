package com.vihmessenger.vihchatbot.viewmodel

import BaseActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.vihmessenger.vihchatbot.api.services.ApiClient
import com.vihmessenger.vihchatbot.data.model.LogoutDataModel
import com.vihmessenger.vihchatbot.data.model.SubscribeChannelRequest
import com.vihmessenger.vihchatbot.data.model.SubscribeChannelResponse
import com.vihmessenger.vihchatbot.data.model.UpdateUserProfile
import com.vihmessenger.vihchatbot.data.model.UserProfileResponse
import com.vihmessenger.vihchatbot.data.model.UserProfileUpdateResponse
import com.vihmessenger.vihchatbot.data.repository.HomeRepository
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import okhttp3.RequestBody

class ProfileViewModel(baseActivity: BaseActivity?) : BaseViewModel() {

    internal val userProfileUpdateLiveData = MutableLiveData<UserProfileUpdateResponse>()
    internal val userProfileUpdateUsernameLiveData = MutableLiveData<UserProfileUpdateResponse>()
    internal val userProfileUpdateImageLiveData = MutableLiveData<UserProfileUpdateResponse>()
    internal val userProfileUpdateImageErrorLiveData = MutableLiveData<String>()

    val createProfileLiveData = SingleLiveEvent<UserProfileUpdateResponse>()

//    val createProfileLiveData = MutableLiveData<UserProfileUpdateResponse>()
    val createProfileErrorLiveData = MutableLiveData<String>()


    val userProfileLogout = MutableLiveData<LogoutDataModel>()
    val userProfileLogoutError = MutableLiveData<String>()

    val subscribeChannelLiveData = MutableLiveData<SubscribeChannelResponse>()
    val subscribeChannelError = MutableLiveData<String>()

    // Enterprise state mutations. Each success event carries the NEW state that was applied
    // (so the UI updates optimistically); the shared error event carries a user message.
    val blacklistResultLiveData = SingleLiveEvent<Boolean>()   // new blocked state
    val muteResultLiveData = SingleLiveEvent<Boolean>()        // new muted state
    val promotionalResultLiveData = SingleLiveEvent<Boolean>() // new opt-in state
    val enterpriseMutationError = MutableLiveData<String>()

    private val customerHomeRepository = HomeRepository(
        ApiClient.apiService, baseActivity
    )

    /** Block ([blacklist]=true) or unblock the enterprise for the current user. */
    fun blacklistEnterprise(enterprisePk: Int, blacklist: Boolean) {
        scope.launch {
            try {
                val res = customerHomeRepository.blacklistEnterprise(false, enterprisePk, blacklist)
                if (res.status == false) {
                    enterpriseMutationError.postValue(res.message ?: "Couldn't update block state")
                } else {
                    blacklistResultLiveData.postValue(blacklist)
                }
            } catch (e: Throwable) {
                enterpriseMutationError.postValue(e.message ?: "Couldn't update block state")
            }
        }
    }

    /** Mute ([mute]=true) or unmute the enterprise for the current (user, channel). */
    fun muteEnterprise(enterpriseId: Int, mute: Boolean) {
        scope.launch {
            try {
                val res = customerHomeRepository.muteEnterprise(false, enterpriseId, mute)
                if (res.status == false) {
                    enterpriseMutationError.postValue(res.message ?: "Couldn't update mute state")
                } else {
                    muteResultLiveData.postValue(mute)
                }
            } catch (e: Throwable) {
                enterpriseMutationError.postValue(e.message ?: "Couldn't update mute state")
            }
        }
    }

    /**
     * Set the promotional preference. [optIn] is the inverse of the read flag
     * is_promotional_message_blocked (optIn=true => not blocked). Emits the new opt-in state.
     */
    fun updateEnterprisePromotional(enterpriseId: Int, channelId: String, optIn: Boolean) {
        scope.launch {
            try {
                val res = customerHomeRepository.updateEnterprisePromotional(
                    false, optIn, enterpriseId, channelId
                )
                if (res.status == false) {
                    enterpriseMutationError.postValue(res.message ?: "Couldn't update preference")
                } else {
                    promotionalResultLiveData.postValue(optIn)
                }
            } catch (e: Throwable) {
                enterpriseMutationError.postValue(e.message ?: "Couldn't update preference")
            }
        }
    }

    /** Subscribes the current user to [hashcode] (Settings hashkey switch). */
    fun subscribeChannel(showBlockingLoader: Boolean, hashcode: String) {
        scope.launch {
            try {
                subscribeChannelLiveData.postValue(
                    customerHomeRepository.subscribeChannel(
                        showBlockingLoader, SubscribeChannelRequest(channel_id = hashcode)
                    )
                )
            } catch (e: Throwable) {
                subscribeChannelError.postValue(e.message ?: "Couldn't switch channel")
            }
        }
    }

    fun getUpdateProfile(
        showBlockingLoader: Boolean, id: Int, updateUserProfile: UpdateUserProfile
    ) {
        scope.launch {
            try {
                userProfileUpdateLiveData.postValue(
                    customerHomeRepository.updateUserProfile(
                        showBlockingLoader, id, updateUserProfile
                    )
                )
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun UpdateProfile(
        showBlockingLoader: Boolean, id: Int, userprofile: UpdateUserProfile
    ) {
        scope.launch {
            try {
                userProfileUpdateUsernameLiveData.postValue(
                    customerHomeRepository.updateUserProfile(
                        showBlockingLoader, id, userprofile
                    )
                )
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun getUpdateProfileImage(showBlockingLoader: Boolean, id: Int, part: MultipartBody.Part) {
        scope.launch {
            try {
                userProfileUpdateImageLiveData.postValue(
                    customerHomeRepository.updateUserProfileImage(
                        showBlockingLoader, id, part
                    )
                )
            } catch (e: Throwable) {
                userProfileUpdateImageErrorLiveData.postValue(e.message)
            }
        }
    }

    fun createProfile(
        showBlockingLoader: Boolean,
        fullName: String,
        email: String,
        user_profile_image: MultipartBody.Part,
    ) {
        scope.launch {
            try {
                createProfileLiveData.postValue(
                    customerHomeRepository.createProfile(
                        showBlockingLoader,
                        fullName,
                        email,
                        user_profile_image,
                    )
                )
            } catch (e: Throwable) {
                createProfileErrorLiveData.postValue(e.message)
                e.printStackTrace()
            }
        }
    }

    fun updateProfileSelective(
        showBlockingLoader: Boolean,
        fields: HashMap<String, RequestBody>,
        image: MultipartBody.Part? = null
    ) {
        scope.launch {
            try {
                val response = customerHomeRepository.updateProfileSelective(
                    showBlockingLoader,
                    fields,
                    image
                )
                createProfileLiveData.postValue(response)
            } catch (e: Throwable) {
                createProfileErrorLiveData.postValue(e.message ?: "An error occurred")
                e.printStackTrace()
            }
        }
    }

    fun logoutProfile(
        showBlockingLoader: Boolean,
        refresh_token: String
    ) {
        scope.launch {
            try {
                val response = customerHomeRepository.userLogout(
                    showBlockingLoader,
                    refresh_token,
                )
                userProfileLogout.postValue(response)
            } catch (e: Throwable) {
                userProfileLogoutError.postValue(e.message ?: "An error occurred")
                e.printStackTrace()
            }
        }
    }
}