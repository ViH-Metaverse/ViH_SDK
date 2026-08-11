package com.vihmessenger.vihchatbot.viewmodel

import BaseActivity
import com.vihmessenger.vihchatbot.utils.VihLog
import androidx.lifecycle.MutableLiveData
import com.google.gson.GsonBuilder
import com.vihmessenger.vihchatbot.api.services.ApiClient
import com.vihmessenger.vihchatbot.data.model.ChatHistoryModel
import com.vihmessenger.vihchatbot.data.model.ChatMessageModel
import com.vihmessenger.vihchatbot.data.model.EnterPriseModel
import com.vihmessenger.vihchatbot.data.model.EnterpriseApiResponse
import com.vihmessenger.vihchatbot.data.model.SdkFeatureModel
import com.vihmessenger.vihchatbot.data.repository.ChatRepository
import com.vihmessenger.vihchatbot.data.repository.NoConnectionException
import kotlinx.coroutines.launch

class ChatViewModel(baseActivity: BaseActivity?) : BaseViewModel() {

    private val customerHomeRepository = ChatRepository(
        ApiClient.apiService, baseActivity
    )

    internal val chatMessageLiveData = MutableLiveData<ChatMessageModel>()

    internal val chatHistoryLiveData = MutableLiveData<ChatHistoryModel>()

    internal val errorLiveData = MutableLiveData<String>()

    internal val enterpriseDetails = MutableLiveData<EnterpriseApiResponse>()

    // SDK-features for the *open* channel — used to decide whether the toolbar shows the
    // call button (voice_bot != null) and to carry the ws_url/bot_key into the call screen.
    // Fetched per-channel because the cached prefs.vihSettings is scoped to the dashboard's
    // default channel, which may differ from the one being viewed (e.g. a Discover deep-link).
    internal val sdkFeatureLiveData = MutableLiveData<SdkFeatureModel?>()

    fun fetchSdkFeatures(hashCode: String) {
        if (hashCode.isBlank()) {
            sdkFeatureLiveData.postValue(null)
            return
        }
        scope.launch {
            try {
                val response = ApiClient.apiService.getSdkFeatures(hashCode)
                val data = if (response.isSuccessful) response.body()?.data else null
                // DEBUG: log the full per-channel details each time a channel is opened.
                // Grep logcat for tag "ChannelDetails". Pretty-printed JSON of the whole payload.
                val json = GsonBuilder().setPrettyPrinting().create().toJson(data)
                VihLog.i(
                    "ChannelDetails",
                    "hashCode=$hashCode httpCode=${response.code()} " +
                        "voice_bot=${data?.voice_bot} is_voice_bot=${data?.vih_features?.is_voice_bot}\n$json"
                )
                sdkFeatureLiveData.postValue(data)
            } catch (e: Throwable) {
                // Non-critical: on failure we simply don't show the call button.
                VihLog.e("ChannelDetails", "fetch failed for hashCode=${VihLog.tail(hashCode)}", e)
                sdkFeatureLiveData.postValue(null)
                e.printStackTrace()
            }
        }
    }

    fun getChatResponse(
        showBlockingLoader: Boolean,
        question: String,
        sessionId: String,
        hashcode: String,
        enterpriseId: String,
    ) {
        scope.launch {
            try {
                chatMessageLiveData.postValue(
                    customerHomeRepository.getChatResponse(
                        showBlockingLoader,
                        question = question,
                        sessionId = sessionId,
                        hashcode = hashcode,
                        enterpriseId = enterpriseId
                    )
                )
            } catch (e: NoConnectionException) {
                errorLiveData.postValue(e.message)
                e.printStackTrace()
            } catch (e: Throwable) {
                errorLiveData.postValue(e.message)
                e.printStackTrace()
            }
        }

    }

    fun getChatHistoryResponse(
        showBlockingLoader: Boolean, channelId: String, enterpriseId: String
    ) {
        scope.launch {
            try {
                chatHistoryLiveData.postValue(
                    customerHomeRepository.getChatHistory(
                        showBlockingLoader, channelId = channelId, enterpriseId = enterpriseId
                    )
                )
            } catch (e: Throwable) {
                // Surface the failure so the UI can dismiss its loading spinner. pbChat is
                // only hidden when chatHistoryLiveData fires, so swallowing this here leaves
                // the chat window spinning forever (e.g. when the history request 500s or
                // times out right after a push-notification tap).
                errorLiveData.postValue(e.message)
                e.printStackTrace()
            }
        }
    }


    fun getEnterpriceModel(
        showBlockingLoader: Boolean, enterpriseId: String
    ) {
        scope.launch {
            try {
                enterpriseDetails.postValue(
                    customerHomeRepository.getEnterpriseDetails(
                        showBlockingLoader, enterprise_id = enterpriseId
                    )
                )
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}