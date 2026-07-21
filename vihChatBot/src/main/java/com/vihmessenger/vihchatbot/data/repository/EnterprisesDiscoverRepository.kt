package com.vihmessenger.vihchatbot.data.repository

import BaseActivity
import com.vihmessenger.vihchatbot.data.model.EnterPriseDiscoverModel
import com.vihmessenger.vihchatbot.data.model.IndustryResponse
import com.vihmessenger.vihchatbot.data.services.ApiService

class EnterprisesDiscoverRepository(
    private val apiService: ApiService, private val baseActivity: BaseActivity?
) : BaseRepository(baseActivity, apiService) {

    suspend fun getEnterpriseDiscoverListResponse(
        showBlockingLoader: Boolean,
        hashcode: String,
        page: Int,
        search: String,
        industries: String
    ): EnterPriseDiscoverModel {
        return doSafeAPIRequest(
            call = {
                apiService.getEnterpriseDiscoverListResponse(
                    hashcode,
                    page,
                    search,
                    industries
                )
            },
            showBlockingLoader = showBlockingLoader
        ) ?: throw NoConnectionException("Failed to get enterprise discover list")
    }

    suspend fun getIndustriesResponse(
        showBlockingLoader: Boolean
    ): IndustryResponse {
        return doSafeAPIRequest(
            call = { apiService.getIndustries() },
            showBlockingLoader = showBlockingLoader
        ) ?: throw NoConnectionException("Failed to get industries")
    }
}