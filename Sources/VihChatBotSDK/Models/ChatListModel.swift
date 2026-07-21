import Foundation

/// Mirrors `data/model/ChatListModel.kt`.
public struct ChatListModelResponse: Codable {
    public var data: [ChatListModel]
}

public struct ChatListModel: Codable {
    public var id: Int
    // Nullable on the wire — the backend sends `mobile` and `chat_id` as null, which a
    // non-optional String decode rejects (failing the whole chat-list response). Keep optional.
    public var mobile: String?
    public var session_id: String
    public var created_at: String
    public var updated_at: String
    public var chat_id: String?
    public var enterprise: EnterPriseModel
    public var last_message: LastMessageModel
    public var unseen_count: Int

    // Non-wire UI fields (mirror Android — for cell binding).
    public var profileImage: Int?
    public var profileName: String?
    public var lastChatMesage: String?
    public var unReadMessagesCount: String?
    public var dateTime: String?
    public var isTyping: Bool?

    enum CodingKeys: String, CodingKey {
        case id, mobile, session_id, created_at, updated_at, chat_id,
             enterprise, last_message, unseen_count
    }
}

public struct EnterpriseApiResponse: Codable {
    public var data: EnterPriseModel
    public var status: Bool
}

public struct EnterPriseModel: Codable {
    public var id: Int
    public var comp_name: String
    public var comp_address: String
    public var email: String
    public var comp_website: String
    public var customercare: String
    public var phone: String
    public var user_id: String
    public var category: String
    public var industry: String
    public var profile_picture: String?
    public var display_img: String?
    public var enterprise_logo: String?
    public var enterprise_display_img: String?
    public var created_at: String?
    public var updated_at: String?
    public var displayNameModel: DisplayNameModel?

    enum CodingKeys: String, CodingKey {
        case id, comp_name, comp_address, email, comp_website, customercare,
             phone, user_id, category, industry, profile_picture, display_img,
             created_at, updated_at
        case displayNameModel = "raw_cpaas_json"
    }

    /// Extra wire keys used only as fallbacks for the channel's display name.
    /// Kept out of `CodingKeys` so the auto-synthesized `Encodable` (which
    /// requires every `CodingKeys` case to map to a stored property) still works.
    private enum FallbackKeys: String, CodingKey {
        case display_name
        case enterprise_display_name
        case enterprise_name
        case enterprise_logo
        case enterprise_display_img
    }

    /// Lenient decode. The backend returns `null` for (or omits) several of these
    /// string fields — e.g. `comp_address` — and the previous non-optional decode
    /// threw, which silently emptied the Discover list. Decode each string with a
    /// default so one null field never fails the whole list.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)

        func string(_ key: CodingKeys) -> String {
            ((try? c.decodeIfPresent(String.self, forKey: key)) ?? nil) ?? ""
        }
        func optString(_ key: CodingKeys) -> String? {
            (try? c.decodeIfPresent(String.self, forKey: key)) ?? nil
        }

        id = ((try? c.decodeIfPresent(Int.self, forKey: .id)) ?? nil) ?? 0
        comp_name = string(.comp_name)
        comp_address = string(.comp_address)
        email = string(.email)
        comp_website = string(.comp_website)
        customercare = string(.customercare)
        phone = string(.phone)
        user_id = string(.user_id)
        category = string(.category)
        industry = string(.industry)
        profile_picture = optString(.profile_picture)
        display_img = optString(.display_img)
        created_at = optString(.created_at)
        updated_at = optString(.updated_at)

        // Channel display name: prefer raw_cpaas_json.display_name, then the
        // top-level display_name / enterprise_display_name the backend sends.
        // (comp_name is often empty, so without this the row title is blank.)
        let f = try? decoder.container(keyedBy: FallbackKeys.self)
        func fallbackString(_ key: FallbackKeys) -> String? {
            guard let f = f else { return nil }
            return (try? f.decodeIfPresent(String.self, forKey: key)) ?? nil
        }

        // The discover API carries the real logo in enterprise_logo / enterprise_display_img
        // (profile_picture / display_img come back null).
        enterprise_logo = fallbackString(.enterprise_logo)
        enterprise_display_img = fallbackString(.enterprise_display_img)

        var dn = (try? c.decodeIfPresent(DisplayNameModel.self, forKey: .displayNameModel)) ?? nil
        if dn?.display_name?.isEmpty ?? true {
            let fallback = [fallbackString(.display_name), fallbackString(.enterprise_display_name), fallbackString(.enterprise_name)]
                .compactMap { $0 }
                .first(where: { !$0.isEmpty })
            if let fallback = fallback {
                if dn == nil {
                    dn = DisplayNameModel(display_name: fallback, display_msg: nil, description: nil)
                } else {
                    dn?.display_name = fallback
                }
            }
        }
        displayNameModel = dn
    }
}

public struct DisplayNameModel: Codable {
    public var display_name: String?
    public var display_msg: String?
    public var description: String?
}

public struct LastMessageModel: Codable {
    public var id: Int
    public var session_id: String
    public var cpaas_json: CpassJsonModel?
    public var sent_by: String
    public var message: String
    // Nullable on the wire (bot/user messages have no avatar) — optional so a null doesn't
    // fail the chat-list decode.
    public var profile_picture: String?
    public var created_at: String
    public var updated_at: String
}
