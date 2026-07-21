package com.vihmessenger.vihchatbot.viewmodel

import BaseActivity
import androidx.lifecycle.MutableLiveData
import com.vihmessenger.vihchatbot.api.services.ApiClient
import com.vihmessenger.vihchatbot.data.model.ChatHistoryModel
import com.vihmessenger.vihchatbot.data.model.ChatMessageModel
import com.vihmessenger.vihchatbot.data.model.EnterPriseModel
import com.vihmessenger.vihchatbot.data.model.EnterpriseApiResponse
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