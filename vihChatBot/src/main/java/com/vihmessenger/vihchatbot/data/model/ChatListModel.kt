package com.vihmessenger.vihchatbot.data.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class ChatListModelResponse(
    @SerializedName("data") var data: List<ChatListModel>,
)

data class ChatListModel(
    @SerializedName("id") var id: Int,
    @SerializedName("mobile") var mobile: String,
    @SerializedName("session_id") var session_id: String,
    @SerializedName("created_at") var created_at: String,
    @SerializedName("updated_at") var updated_at: String,
    @SerializedName("chat_id") var chat_id: String,
    @SerializedName("enterprise") var enterprise: EnterPriseModel,
    @SerializedName("last_message") var last_message: LastMessageModel,
    @SerializedName("unseen_count") var unseen_count: Int,
    var profileImage: Int,
    var profileName: String,
    var lastChatMesage: String,
    var unReadMessagesCount: String,
    var dateTime: String,
    var isTyping: Boolean
): Serializable


/**
 * `main/enterprise-details/`.
 *
 * `data` is deliberately a raw [JsonElement] rather than an [EnterPriseModel]: the backend
 * reuses the same field for the failure message, so an error comes back as
 * `{"data":"Enterprise not found","status":false,"error_code":"EC_ENTERPRISE_4041"}`. Typing it
 * as the model made Gson throw `IllegalStateException: Expected BEGIN_OBJECT but was STRING at
 * $.data` on every such response, which reached users as a raw-exception toast instead of a
 * handled error.
 */
data class EnterpriseApiResponse(
    @SerializedName("data") private val rawData: JsonElement? = null,
    @SerializedName("status") var status: Boolean = false,
    @SerializedName("error_code") val errorCode: String? = null,
) {
    /** The enterprise, or null when the server returned an error envelope. */
    val data: EnterPriseModel?
        get() = rawData?.takeIf { it.isJsonObject }
            ?.let { runCatching { Gson().fromJson(it, EnterPriseModel::class.java) }.getOrNull() }

    /** The server's message when [data] is absent — e.g. "Enterprise not found". */
    val message: String?
        get() = rawData?.takeIf { it.isJsonPrimitive }?.asString
}

data class EnterPriseModel(
    @SerializedName("id") var id: Int,
    @SerializedName("comp_name") var comp_name: String,
    @SerializedName("comp_address") var comp_address: String,
    @SerializedName("email") var email: String,
    @SerializedName("comp_website") var comp_website: String,
    @SerializedName("customercare") var customercare: String,
    @SerializedName("phone") var phone: String,
    @SerializedName("user_id") var user_id: String,
    @SerializedName("category") var category: String,
    @SerializedName("industry") var industry: String,
    @SerializedName("profile_picture") var profile_picture: String?,
    @SerializedName("display_img") var display_img: String?,
    @SerializedName("created_at") var created_at: String?,
    @SerializedName("updated_at") var updated_at: String?,
    @SerializedName("raw_cpaas_json") var displayNameModel: DisplayNameModel?,
    // The discover API populates these enterprise_* fields (the display_name/display_img
    // and raw_cpaas_json fields come back null), so they carry the real name + logo.
    @SerializedName("enterprise_display_name") var enterprise_display_name: String? = null,
    @SerializedName("enterprise_name") var enterprise_name: String? = null,
    @SerializedName("enterprise_display_img") var enterprise_display_img: String? = null,
    @SerializedName("enterprise_logo") var enterprise_logo: String? = null,
    // Per-enterprise state flags computed server-side against the user's active channel
    // (EnterpriseSerializer). Backend sends them nullable; default null and always compare
    // with `== true` so a null/omitted flag reads as false. Populated by the discover,
    // session-list and enterprise-details endpoints (each fills a different subset).
    @SerializedName("is_blacklisted_by_channel") var is_blacklisted_by_channel: Boolean? = null,
    @SerializedName("is_blacklisted_by_user") var is_blacklisted_by_user: Boolean? = null,
    @SerializedName("is_muted_by_user") var is_muted_by_user: Boolean? = null,
    // promotional_opt_in and is_promotional_message_blocked are INVERSE: opt_in=true => not blocked.
    @SerializedName("is_promotional_message_blocked") var is_promotional_message_blocked: Boolean? = null,
    @SerializedName("promotional_opt_in") var promotional_opt_in: Boolean? = null
) : Serializable {

    /**
     * The channel name to display. The backend carries it in different fields depending on the
     * endpoint (the discover API leaves display_name/raw_cpaas_json null and fills enterprise_*),
     * so fall back through all of them and use the first non-blank value.
     */
    val resolvedDisplayName: String
        get() = listOf(
            displayNameModel?.display_name,
            enterprise_display_name,
            enterprise_name,
            comp_name
        ).firstOrNull { !it.isNullOrBlank() } ?: ""

    /**
     * The channel logo URL, with the same cross-endpoint fallback as [resolvedDisplayName].
     * Returns null when no image field is populated (caller shows a placeholder).
     */
    val resolvedLogoUrl: String?
        get() = listOf(
            display_img,
            profile_picture,
            enterprise_logo,
            enterprise_display_img
        ).firstOrNull { !it.isNullOrBlank() }
}


data class DisplayNameModel(
    @SerializedName("display_name") var display_name: String?,
    @SerializedName("display_msg") var display_msg: String?,
    @SerializedName("description") var description: String?,
) : Serializable


data class LastMessageModel(
    @SerializedName("id") var id: Int,
    @SerializedName("session_id") var session_id: String,
    @SerializedName("cpaas_json") var cpaas_json: CpassJsonModel? = null,
    @SerializedName("sent_by") var sent_by: String,
    @SerializedName("message") var message: String,
    @SerializedName("profile_picture") var profile_picture: String,
    @SerializedName("created_at") var created_at: String,
    @SerializedName("updated_at") var updated_at: String,
)

