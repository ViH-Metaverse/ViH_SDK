package com.vihmessenger.vihchatbot.data.model

import com.google.gson.annotations.SerializedName

data class ErrorModel(
//    @SerializedName("data") var data: String,
    @SerializedName("message") var message: String,
    @SerializedName("status") var status: Boolean,
)
