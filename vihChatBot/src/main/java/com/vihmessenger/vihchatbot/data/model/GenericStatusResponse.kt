package com.vihmessenger.vihchatbot.data.model

import com.google.gson.annotations.SerializedName

/**
 * Minimal `{ status, message }` envelope returned by the enterprise state-mutation
 * endpoints (block/unblock, mute/unmute, promotional opt-in/out). All are nullable so a
 * terse backend response (e.g. status only) still decodes.
 */
data class GenericStatusResponse(
    @SerializedName("status") var status: Boolean? = null,
    @SerializedName("message") var message: String? = null
)
