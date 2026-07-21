package com.vihmessenger.vihchatbot.data.model

import com.google.gson.annotations.SerializedName

data class LoanApprovalRequest(
    @SerializedName("session_id") val session_id: String
)

/**
 * Response shape from POST /main/loan-approval/.
 *
 * The top-level envelope carries [status]/[message]/[errorCode] plus flat
 * conveniences [callId] and [voiceWsUrl]. The nested [data] block holds the
 * full Ultravox payload (the source of truth for the call). We prefer the
 * flat top-level fields when reading.
 */
data class LoanApprovalResponse(
    @SerializedName("status") val status: Boolean?,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: LoanApprovalData?,
    @SerializedName("call_id") val callId: String?,
    @SerializedName("voice_ws_url") val voiceWsUrl: String?,
    @SerializedName("error_code") val errorCode: String?
)

data class LoanApprovalData(
    @SerializedName("url") val url: String?,
    @SerializedName("callId") val callId: String?,
    @SerializedName("requestId") val requestId: String?,
    @SerializedName("voicebot_id") val voicebotId: String?,
    @SerializedName("customerPhone") val customerPhone: String?,
    @SerializedName("callSid") val callSid: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("correlationMethod") val correlationMethod: String?,
    @SerializedName("timestamp") val timestamp: String?
)

data class CallDetailsRequest(
    @SerializedName("call_id") val call_id: String,
    @SerializedName("session_id") val session_id: String
)
