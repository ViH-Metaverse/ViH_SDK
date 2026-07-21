package com.vihmessenger.vihchatbot.viewmodel

import BaseActivity
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.vihmessenger.vihchatbot.api.services.ApiClient
import com.vihmessenger.vihchatbot.data.model.ChatListModelResponse
import com.vihmessenger.vihchatbot.data.model.EnterPriseDiscoverModel
import com.vihmessenger.vihchatbot.data.model.IndustryResponse
import com.vihmessenger.vihchatbot.data.model.EmailLoginRequest
import com.vihmessenger.vihchatbot.data.model.EmailLoginResponse
import com.vihmessenger.vihchatbot.data.model.SdkFeatureModel
import com.vihmessenger.vihchatbot.data.model.UserProfileRequest
import com.vihmessenger.vihchatbot.data.model.UserProfileResponse
import com.vihmessenger.vihchatbot.data.repository.ChatRepository
import com.vihmessenger.vihchatbot.data.repository.EnterprisesDiscoverRepository
import com.vihmessenger.vihchatbot.data.repository.HomeRepository
import com.vihmessenger.vihchatbot.data.repository.NoConnectionException
import com.vihmessenger.vihchatbot.data.services.ApiService
import com.vihmessenger.vihchatbot.data.services.BaseCloudAPIService
import kotlinx.coroutines.launch

class HomeViewModel(baseActivity: BaseActivity?) : BaseViewModel() {

    private val customerHomeRepository = HomeRepository(
        BaseCloudAPIService.getApiService(ApiService::class.java), baseActivity
    )

    private val homeRepository = HomeRepository(
        ApiClient.apiService, baseActivity
    )

    private val chatRepository = ChatRepository(
        ApiClient.apiService, baseActivity
    )

    private val enterprisesDiscoverRepository =
        EnterprisesDiscoverRepository(ApiClient.apiService, baseActivity)


    internal val sdkFeatureLiveData = MutableLiveData<SdkFeatureModel>()
    val userprofileLiveData = MutableLiveData<UserProfileResponse>()
    val emailLoginLiveData = MutableLiveData<EmailLoginResponse>()
    val emailLoginErrorLiveData = MutableLiveData<String>()

    internal val chatListLiveData = MutableLiveData<ChatListModelResponse>()
    internal val enterprisesDiscoverListLiveData = MutableLiveData<EnterPriseDiscoverModel>()

    internal val industryListLiveData = MutableLiveData<IndustryResponse>()
    private var _selectedIndustries: String = ""

    internal val errorListLiveData = MutableLiveData<String>()

    internal val fragmentTransitionLiveData = MutableLiveData<String>()


    fun getSdkFeatures(showBlockingLoader: Boolean, hashCode: String) {
        scope.launch {
            try {
                sdkFeatureLiveData.postValue(
                    customerHomeRepository.getCustomerHome(
                        showBlockingLoader, hashCode = hashCode
                    ).data
                )
            } catch (e: Throwable) {
                Log.d(TAG, "getSdkFeatures: $e")
                e.printStackTrace()
            }
        }
    }

    fun getUserProfile(showBlockingLoader: Boolean, userProfileRequest: UserProfileRequest) {
        scope.launch {
            try {
                userprofileLiveData.postValue(
                    homeRepository.createUserProfile(
                        showBlockingLoader, userProfileRequest
                    )
                )
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    /** Exchanges a verified Cognito ID token (+ mobile for delivery) for app-session tokens. */
    fun emailLogin(showBlockingLoader: Boolean, request: EmailLoginRequest) {
        scope.launch {
            try {
                emailLoginLiveData.postValue(
                    homeRepository.emailLogin(showBlockingLoader, request)
                )
            } catch (e: Throwable) {
                emailLoginErrorLiveData.postValue(e.message ?: "Email login failed")
                e.printStackTrace()
            }
        }
    }

    fun getChattingListResponse(showBlockingLoader: Boolean, hashcode: String) {
        scope.launch {
            try {
                chatListLiveData.postValue(
                    chatRepository.getChatListResponse(
                        showBlockingLoader, hashcode = hashcode
                    )
                )
            } catch (e: NoConnectionException) {
                // A server error / timeout here must not crash the app — the repo
                // rethrows failures (incl. HTTP 500) as NoConnectionException, and
                // scope uses a plain Job with no handler, so an uncaught throw here
                // reaches the default uncaught handler and kills the process.
                errorListLiveData.postValue(e.message)
            } catch (e: Throwable) {
                errorListLiveData.postValue(e.message)
            }
        }
    }

    fun getEnterpriseDiscoverListResponse(
        showBlockingLoader: Boolean,
        hashCode: String,
        page: Int,
        search: String,
        industries: String
    ) {
        scope.launch {
            try {
                enterprisesDiscoverListLiveData.postValue(
                    enterprisesDiscoverRepository.getEnterpriseDiscoverListResponse(
                        showBlockingLoader, hashCode, page, search, industries
                    )
                )
            } catch (e: NoConnectionException) {
                errorListLiveData.postValue(e.message)
            } catch (e: Throwable) {
                errorListLiveData.postValue(e.message)
            }
        }
    }

    fun getIndustriesListResponse(showBlockingLoader: Boolean) {
        scope.launch {
            try {
                industryListLiveData.postValue(
                    enterprisesDiscoverRepository.getIndustriesResponse(
                        showBlockingLoader
                    )
                )
            } catch (e: Throwable) {
                errorListLiveData.postValue(e.message)
            }
        }
    }

    fun getSelectedIndustries(): String {
        return _selectedIndustries
    }

    fun setSelectedIndustries(industries: String) {
        _selectedIndustries = industries
    }
}