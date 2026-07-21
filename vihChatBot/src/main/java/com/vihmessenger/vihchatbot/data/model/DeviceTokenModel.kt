package com.vihmessenger.vihchatbot.data.model

import com.google.gson.annotations.SerializedName

/**
 * Request body for the session-registry endpoint introduced in architecture §3.3.
 * The backend persists {device_id -> fcm_token} in ElastiCache Redis so the
 * dispatch Lambda can decide between MQTT push and FCM wake-up.
 *
 * `hashcode` and `platform` give the server enough context to scope the entry per
 * tenant and per OS without an extra round-trip.
 */
data class DeviceTokenRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("fcm_token") val fcmToken: String,
    @SerializedName("hashcode") val hashcode: String?,
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("sdk_version") val sdkVersion: String
)

data class DeviceTokenResponse(
    @SerializedName("status") val status: Boolean,
    @SerializedName("message") val message: String? = null
)
