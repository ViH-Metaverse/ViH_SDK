package com.vihmessenger.vihchatbot.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class EnterPriseDiscoverModel(
    @SerializedName("data") var data: List<EnterPriseModel> = mutableListOf()
)

data class EnterprisesDiscoverData(
    @SerializedName("id") var id: Int,
    @SerializedName("user_id") var user_id: String,
    @SerializedName("name") var name: String,
    @SerializedName("comp_name") var comp_name: String,
    @SerializedName("comp_address") var comp_address: String,
    @SerializedName("comp_website") var comp_website: String,
    @SerializedName("customercare") var customercare: String,
    @SerializedName("phone") var phone: String,
    @SerializedName("mobile") var mobile: String,
    @SerializedName("email") var email: String,
    @SerializedName("industry") var industry: String,
    @SerializedName("category") var category: String,
    @SerializedName("profile_picture") var profile_picture: String,
    @SerializedName("is_chat") var is_chat: Boolean,
    @SerializedName("created_at") var created_at: String,
    @SerializedName("updated_at") var updated_at: String
) : Serializable