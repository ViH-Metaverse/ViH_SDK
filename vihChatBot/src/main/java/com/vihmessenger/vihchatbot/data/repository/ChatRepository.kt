package com.vihmessenger.vihchatbot.data.repository

import BaseActivity
import com.vihmessenger.vihchatbot.data.model.ChatHistoryModel
import com.vihmessenger.vihchatbot.data.model.ChatListModelResponse
import com.vihmessenger.vihchatbot.data.model.ChatMessageModel
import com.vihmessenger.vihchatbot.data.model.EnterPriseModel
import com.vihmessenger.vihchatbot.data.model.EnterpriseApiResponse
import com.vihmessenger.vihchatbot.data.services.ApiService

class ChatRepository(
    private val apiService: ApiService, private val baseActivity: BaseActivity?
) : BaseRepository(baseActivity, apiService) {

    suspend fun getChatResponse(
        showBlockingLoader: Boolean,
        question: String,
        sessionId: String,
        hashcode: String,
        enterpriseId: String
    ): ChatMessageModel {
        return doSafeAPIRequest(
            call = { apiService.getChatResponse(hashcode, enterpriseId, question, sessionId) },
            showBlockingLoader = showBlockingLoader
        ) ?: throw NoConnectionException("Failed to get chat response")
    }

    suspend fun getChatHistory(
        showBlockingLoader: Boolean, channelId: String, enterpriseId: String
    ): ChatHistoryModel {
        return doSafeAPIRequest(
            call = {
                apiService.getChatHistory(
                    channelId, enterpriseId
                )
            }, showBlockingLoader = showBlockingLoader
        ) ?: throw NoConnectionException("Failed to get chat history")
    }

    suspend fun getChatListResponse(
        showBlockingLoader: Boolean,
        hashcode: String,
    ): ChatListModelResponse {
        return doSafeAPIRequest(
            call = { apiService.getChatListResponse(hashcode) },
            showBlockingLoader = showBlockingLoader
        ) ?: throw NoConnectionException("Failed to get chat list")
    }

    suspend fun getEnterpriseDetails(
        showBlockingLoader: Boolean,
        enterprise_id: String,
    ): EnterpriseApiResponse {
        return doSafeAPIRequest(
            call = { apiService.getEnterprises(enterprise_id) },
            showBlockingLoader = showBlockingLoader
        ) ?: throw NoConnectionException("Failed to get chat list")
    }
}

class NoConnectionException(message: String) : Exception(message)