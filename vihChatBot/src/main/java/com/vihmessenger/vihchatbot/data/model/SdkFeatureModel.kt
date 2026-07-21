package com.vihmessenger.vihchatbot.data.model

import com.google.gson.annotations.SerializedName


data class SdkFeatureResponse(
    @SerializedName("status") var status: Boolean,
    @SerializedName("message") var message: String,
    @SerializedName("data") var data: SdkFeatureModel,

    )

data class SdkFeatureModel(
    @SerializedName("id") var id: Int,
    // The API returns vih_features as a single object, not a list — decoding it as a
    // List threw "Expected BEGIN_ARRAY but was BEGIN_OBJECT" and failed getSdkFeatures.
    @SerializedName("vih_features") var vih_features: SdkVihFeatureModel? = null,
    @SerializedName("title") var title: String,
    @SerializedName("chat_boat_name") var chat_boat_name: String,
    @SerializedName("chat_boat_logo") var chat_boat_logo: String,
    @SerializedName("chatboat_placement_type") var chatboat_placement_type: String,
    @SerializedName("page") var page: String,
    @SerializedName("welcome_message") var welcome_message: String,
    @SerializedName("status") var status: String,
    @SerializedName("style_primary_color") var style_primary_color: String,
    @SerializedName("style_secondary_color") var style_secondary_color: String,
    @SerializedName("style_accent_color") var style_accent_color: String,
    @SerializedName("style_font_color") var style_font_color: String,
    @SerializedName("background_style") var background_style: String,
    @SerializedName("solid_color") var solid_color: String,
    @SerializedName("Choose_other_image") var choose_other_image: String,
    @SerializedName("welcome_message_pop_up") var welcome_message_pop_up: Boolean,
    @SerializedName("created_at") var created_at: String,
    @SerializedName("updated_at") var updated_at: String,
    @SerializedName("hash_code") var hash_code: String,
    @SerializedName("is_connected") var is_connected: String,
    @SerializedName("knowledge_base_search") var knowledge_base_search: Boolean,
    @SerializedName("email_capture") var email_capture: Boolean,
    @SerializedName("email_capture_message") var email_capture_message: String,
    @SerializedName("sla_time_to_first_reply") var sla_time_to_first_reply: Boolean,
    @SerializedName("sla_time_to_close") var sla_time_to_close: Boolean,
    @SerializedName("access_available_to_everyone") var access_available_to_everyone: Boolean,
    @SerializedName("consent_to_collect_chat_cookies") var consent_to_collect_chat_cookies: Boolean,
    @SerializedName("consent_to_process_data") var consent_to_process_data: Boolean,
    @SerializedName("consent_to_communicate") var consent_to_communicate: Boolean,
    @SerializedName("collect_feedback_from_new_users") var collect_feedback_from_new_users: Boolean,
    @SerializedName("user") var user: Int
)

data class SdkVihFeatureModel(
    @SerializedName("id") var id: Int,
    @SerializedName("created_at") var created_at: String,
    @SerializedName("updated_at") var updated_at: String,
    @SerializedName("is_nlp") var is_nlp: Boolean,
    @SerializedName("is_contextual_search") var is_contextual_search: Boolean = false,
    @SerializedName("is_live_agent") var is_live_agent: Boolean = false,
    @SerializedName("is_otp_message") var is_otp_message: Boolean = false,
    @SerializedName("is_transactional_message") var is_transactional_message: Boolean = false,
    @SerializedName("is_create_shortcut") var is_create_shortcut: Boolean = false,
    @SerializedName("is_metaverse") var is_metaverse: Boolean,
    @SerializedName("is_promotional_campaign") var is_promotional_campaign: Boolean,
    @SerializedName("is_campaign_analytics") var is_campaign_analytics: Boolean,
    @SerializedName("is_loyalty_points_campaign") var is_loyalty_points_campaign: Boolean,
    @SerializedName("is_customisable_templates") var is_customisable_templates: Boolean,
    @SerializedName("is_response_generation") var is_response_generation: Boolean,
    @SerializedName("is_multimedia_support") var is_multimedia_support: Boolean,
    @SerializedName("is_multiplatform_support") var is_multiplatform_support: Boolean,
    @SerializedName("is_ticket_support") var is_ticket_support: Boolean,
    @SerializedName("is_offer_listing") var is_offer_listing: Boolean,
    @SerializedName("is_billing") var is_billing: Boolean,
    @SerializedName("is_shopping_vih") var is_shopping_vih: Boolean,
    @SerializedName("is_order_managment") var is_order_managment: Boolean,
    @SerializedName("is_personalization") var is_personalization: Boolean,
    @SerializedName("is_external_service") var is_external_service: Boolean,
    @SerializedName("is_analytics_insight") var is_analytics_insight: Boolean,
    @SerializedName("is_human_handoff") var is_human_handoff: Boolean,
    @SerializedName("is_chatboat_builder") var is_chatboat_builder: Boolean,
    @SerializedName("is_geo_fencing") var is_geo_fencing: Boolean,
    @SerializedName("is_promotional_toll") var is_promotional_toll: Boolean,
    @SerializedName("channel") var channel: Int
)
