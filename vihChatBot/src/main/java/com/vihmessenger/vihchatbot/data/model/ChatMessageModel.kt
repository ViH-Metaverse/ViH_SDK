package com.vihmessenger.vihchatbot.data.model

import com.google.gson.annotations.SerializedName
import com.vihmessenger.vihchatbot.constants.AppConstants

data class ChatMessageModel(
    @SerializedName("data") var data: MessageModel?,
    @SerializedName("status") var status: Boolean,
    var id: String = AppConstants.SEND_ID
)

data class ChatHistoryModel(
    @SerializedName("data") var data: List<MessageModel>?,
    @SerializedName("status") var status: Boolean,
    var id: String = AppConstants.SEND_ID
)

data class MessageModel(
    @SerializedName("session_id") var session_id: String?,
    @SerializedName("message") var message: String,
    @SerializedName("suggested_questions") var suggested_questions: List<String>? = mutableListOf(),
    @SerializedName("sent_by") var sent_by: String,
    @SerializedName("created_at") var created_at: String,
    @SerializedName("updated_at") var updated_at: String,
    @SerializedName("session") var session: String,
    @SerializedName("cpaas_json") var cpaas_json: CpassJsonModel?,
    @SerializedName("is_flow") var is_flow: Int = 0,
    // CPaaS template type: 1=OTP, 2=promotional, 3=transactional, 4=normal. Exposed at the
    // message level by the backend's ChatMessageSerializer (see OTP_MOBILE_SPEC §1b). Drives
    // OTP-card rendering ([isOtp]); null for plain chat replies. cpaas_json.templ_typ mirrors it.
    @SerializedName("template_type") var template_type: Int? = null,
    // Where the send originated: "api" (external client backend via /v1/otp/send) or "portal"
    // (enterprise console/CPaaS). Optional origin badge on the OTP card.
    @SerializedName("source") var source: String? = null,
    // Structured interactive buttons from the GLM flow (flow_chk == 2). Additive and optional:
    // `message` + `suggested_questions` are unchanged, so this is null for older/OpenAI replies.
    @SerializedName("interactive") var interactive: InteractiveModel? = null,
    var isVideo: Boolean = false,
    // Correlation IDs propagated from the backend for end-to-end tracing per
    // architecture §6.2. Nullable until the server starts emitting them.
    @SerializedName("shoot_id") var shoot_id: String? = null,
    @SerializedName("message_id") var message_id: String? = null,
    @SerializedName("trace_id") var trace_id: String? = null
)

/**
 * True when this is an OTP message and should render as the dedicated OTP card
 * (OTP_MOBILE_SPEC §3). Keys off the message-level `template_type == 1`, falling back to
 * cpaas_json.templ_typ == "1" for payloads that only carry the template field.
 */
val MessageModel.isOtp: Boolean
    get() = template_type == 1 || cpaas_json?.templ_typ == "1"

/**
 * Optional interactive payload attached to a bot [MessageModel] by the GLM flow. See
 * docs `MESSAGE_FLOW_GLM.md` §4 for the contract. The server caps buttons at 5 and clamps
 * labels; the client renders them as tappable buttons under the bot bubble.
 */
data class InteractiveModel(
    @SerializedName("version") var version: Int? = null,
    @SerializedName("type") var type: String? = null,
    @SerializedName("buttons") var buttons: List<InteractiveButton>? = null
)

data class InteractiveButton(
    @SerializedName("label") var label: String? = null,
    // One of: "quick_reply" (send value as next message), "url" (open link),
    // "action" (named in-app capability, handled by the host / degrades gracefully).
    @SerializedName("type") var type: String? = null,
    @SerializedName("value") var value: String? = null
)

// All fields are nullable: a CPaaS/template message only populates the fields relevant
// to its template type (a text template has null image_url/audio_url/video_url/doc_url,
// etc.). Gson writes JSON null into the field regardless of any Kotlin default, so
// declaring these non-null caused NullPointerExceptions when the chat rendered a pushed
// template message. Keep them nullable and null-guard at the call sites.
data class CpassJsonModel(
    @SerializedName("id") var id: Int? = null,
    @SerializedName("templ_name") var templ_name: String? = null,
    @SerializedName("templ_typ") var templ_typ: String? = null,
    @SerializedName("media_size") var media_size: String? = null,
    @SerializedName("thumbnail") var thumbnail: String? = null,
    @SerializedName("templ_lang") var templ_lang: String? = null,
    @SerializedName("head_typ") var head_typ: String? = null,
    @SerializedName("is_headertxt") var is_headertxt: String? = null,
    @SerializedName("header_txt") var header_txt: String? = null,
    @SerializedName("is_header_img") var is_header_img: String? = null,
    @SerializedName("image_url") var image_url: String? = null,
    @SerializedName("is_header_aud") var is_header_aud: String? = null,
    @SerializedName("audio_url") var audio_url: String? = null,
    @SerializedName("is_header_vid") var is_header_vid: String? = null,
    @SerializedName("video_url") var video_url: String? = null,
    @SerializedName("is_header_doc") var is_header_doc: String? = null,
    @SerializedName("doc_url") var doc_url: String? = null,
    @SerializedName("msg") var msg: String? = null,
    @SerializedName("footer") var footer: String? = null,
    @SerializedName("button") var button: List<ButtonModel>? = null,
    @SerializedName("product") var product: List<ProductModel>? = null,
)

data class ButtonModel(
    @SerializedName("btn_typ") var btn_typ: String? = null,
    @SerializedName("btn_txt") var btn_txt: String? = null,
    @SerializedName("btn_value") var btn_value: String? = null,
    )
data class ProductModel(
    @SerializedName("id") var id: String? = null,
    @SerializedName("templ_id") var templ_id: String? = null,
    @SerializedName("prod_id") var prod_id: String? = null,
    @SerializedName("img_opt") var img_opt: String? = null,
    @SerializedName("prod_nm") var prod_nm: String? = null,
    @SerializedName("prod_prc") var prod_prc: String? = null,
    @SerializedName("prod_dsc") var prod_dsc: String? = null,
    @SerializedName("img_url") var img_url: String? = null,
    @SerializedName("addtocrt") var addtocrt: String? = null,
    @SerializedName("addttocrt_url") var addttocrt_url: String? = null,
    @SerializedName("buynw") var buynw: String? = null,
    @SerializedName("buynw_url") var buynw_url: String? = null,
    @SerializedName("created_at") var created_at: String? = null,

    )