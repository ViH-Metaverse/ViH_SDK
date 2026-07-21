import Foundation

public struct SdkFeatureResponse: Codable {
    public var status: Bool
    public var message: String
    public var data: SdkFeatureModel
}

public struct SdkFeatureModel: Codable {
    public var id: Int
    // The API returns vih_features as a single object, not an array — decoding it as
    // [SdkVihFeatureModel] failed the whole getSdkFeatures response. Optional so a
    // missing/null value doesn't throw.
    public var vih_features: SdkVihFeatureModel?
    public var title: String
    // The backend key is "chat_bot_name"/"chat_bot_logo" (with "bot", not "boat"); the old
    // CodingKeys typo made these never decode. Also nullable on the wire → optional.
    public var chat_boat_name: String?
    public var chat_boat_logo: String?
    public var chatboat_placement_type: String?
    public var page: String?
    public var welcome_message: String
    public var status: String
    public var style_primary_color: String
    public var style_secondary_color: String
    public var style_accent_color: String
    public var style_font_color: String
    public var background_style: String
    public var solid_color: String
    public var choose_other_image: String
    public var welcome_message_pop_up: Bool
    public var created_at: String
    public var updated_at: String
    public var hash_code: String
    public var is_connected: String
    public var knowledge_base_search: Bool
    public var email_capture: Bool
    public var email_capture_message: String?
    public var sla_time_to_first_reply: Bool
    public var sla_time_to_close: Bool
    public var access_available_to_everyone: Bool
    public var consent_to_collect_chat_cookies: Bool
    public var consent_to_process_data: Bool
    public var consent_to_communicate: Bool
    public var collect_feedback_from_new_users: Bool
    public var user: Int

    enum CodingKeys: String, CodingKey {
        case id, vih_features, title
        case chat_boat_name = "chat_bot_name"
        case chat_boat_logo = "chat_bot_logo"
        case chatboat_placement_type = "chatbot_placement_type"
        case page, welcome_message, status,
             style_primary_color, style_secondary_color, style_accent_color,
             style_font_color, background_style, solid_color
        case choose_other_image = "Choose_other_image"
        case welcome_message_pop_up, created_at, updated_at, hash_code,
             is_connected, knowledge_base_search, email_capture,
             email_capture_message, sla_time_to_first_reply, sla_time_to_close,
             access_available_to_everyone, consent_to_collect_chat_cookies,
             consent_to_process_data, consent_to_communicate,
             collect_feedback_from_new_users, user
    }
}

public struct SdkVihFeatureModel: Codable {
    public var id: Int
    public var created_at: String
    public var updated_at: String
    public var is_nlp: Bool
    public var is_metaverse: Bool
    public var is_promotional_campaign: Bool
    public var is_campaign_analytics: Bool
    public var is_loyalty_points_campaign: Bool
    public var is_customisable_templates: Bool
    public var is_response_generation: Bool
    public var is_multimedia_support: Bool
    public var is_multiplatform_support: Bool
    public var is_ticket_support: Bool
    public var is_offer_listing: Bool
    public var is_billing: Bool
    public var is_shopping_vih: Bool
    public var is_order_managment: Bool
    public var is_personalization: Bool
    public var is_external_service: Bool
    public var is_analytics_insight: Bool
    public var is_human_handoff: Bool
    public var is_chatboat_builder: Bool
    public var is_geo_fencing: Bool
    public var is_promotional_toll: Bool
    public var channel: Int
}

public struct IndustryResponse: Codable {
    public var data: [String]
    public var status: Bool
}

public struct EnterPriseDiscoverModel: Codable {
    public var data: [EnterPriseModel]
}
