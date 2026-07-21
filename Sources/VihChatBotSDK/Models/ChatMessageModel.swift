import Foundation

/// Mirrors `data/model/ChatMessageModel.kt`. Swift `Codable` replaces Gson
/// `@SerializedName`; JSON keys preserve snake_case via `CodingKeys`.
public struct ChatMessageModel: Codable {
    public var data: MessageModel?
    public var status: Bool
    /// Non-wire field. Initialised to `AppConstants.sendId` to match Android default.
    public var id: String = AppConstants.sendId

    enum CodingKeys: String, CodingKey { case data, status }

    public init(data: MessageModel?, status: Bool) {
        self.data = data
        self.status = status
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        data = (try? c.decodeIfPresent(MessageModel.self, forKey: .data)) ?? nil
        status = decodeLenientBool(c, .status) ?? true
    }
}

public struct ChatHistoryModel: Codable {
    public var data: [MessageModel]?
    public var status: Bool
    public var id: String = AppConstants.sendId

    enum CodingKeys: String, CodingKey { case data, status }

    public init(data: [MessageModel]?, status: Bool) {
        self.data = data
        self.status = status
    }

    /// Lenient decode. Unlike `main/chat`, the `main/chat-history` response does not carry a
    /// top-level `status` (the sibling `ChatListModelResponse` omits it entirely for the same
    /// reason). A strict non-optional `status: Bool` therefore threw `keyNotFound` and failed the
    /// WHOLE history decode — the ViewModel swallowed the error and the chat window came up empty
    /// while the (status-free) chat-list decoded fine. Tolerate a missing / bool / int / string
    /// `status` so a real message list always renders. See docs/ios-codable-audit.md.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        data = (try? c.decodeIfPresent([MessageModel].self, forKey: .data)) ?? nil
        status = decodeLenientBool(c, .status) ?? true
    }
}

/// Decodes a JSON bool that a loose backend may serialize as a real bool, `1`/`0`, or
/// `"true"`/`"1"` — or omit. Returns nil only when the key is absent or an unrecognized shape.
func decodeLenientBool<K: CodingKey>(_ c: KeyedDecodingContainer<K>, _ key: K) -> Bool? {
    if let b = ((try? c.decodeIfPresent(Bool.self, forKey: key)) ?? nil) { return b }
    if let i = ((try? c.decodeIfPresent(Int.self, forKey: key)) ?? nil) { return i != 0 }
    if let s = ((try? c.decodeIfPresent(String.self, forKey: key)) ?? nil) {
        return s.lowercased() == "true" || s == "1"
    }
    return nil
}

public struct MessageModel: Codable {
    public var session_id: String?
    public var message: String
    public var suggested_questions: [String]?
    public var sent_by: String
    public var created_at: String
    public var updated_at: String
    public var session: String
    public var cpaas_json: CpassJsonModel?
    public var is_flow: Int

    /// CPaaS template type: 1=OTP, 2=promotional, 3=transactional, 4=normal. Exposed at the
    /// message level by `ChatMessageSerializer` (see OTP_MOBILE_SPEC §1b). Drives OTP-card
    /// rendering ([isOtp]); nil/absent for plain chat replies. cpaas_json.templ_typ mirrors it.
    public var template_type: Int?

    /// Where the send originated: "api" (external client backend via /v1/otp/send) or
    /// "portal" (enterprise console/CPaaS). Optional origin badge on the OTP card.
    public var source: String?

    /// Structured interactive buttons from the GLM flow (flow_chk == 2). Additive/optional —
    /// `message` + `suggested_questions` are unchanged, so this is nil for older/OpenAI replies.
    public var interactive: InteractiveModel?

    /// Non-wire UI flag carried in the Android model. Defaults to false.
    public var isVideo: Bool = false

    // Correlation IDs (architecture §6.2).
    public var shoot_id: String?
    public var message_id: String?
    public var trace_id: String?

    enum CodingKeys: String, CodingKey {
        case session_id, message, suggested_questions, sent_by, created_at,
             updated_at, session, cpaas_json, is_flow, template_type, source,
             interactive, shoot_id, message_id, trace_id
    }

    /// True when this is an OTP message and should render as the dedicated OTP card
    /// (OTP_MOBILE_SPEC §3). Keys off the message-level `template_type == 1`, falling back to
    /// cpaas_json.templ_typ == "1" for payloads that only carry the template field.
    public var isOtp: Bool { template_type == 1 || cpaas_json?.templ_typ == "1" }

    public init(
        session_id: String?,
        message: String,
        suggested_questions: [String]? = [],
        sent_by: String,
        created_at: String,
        updated_at: String,
        session: String,
        cpaas_json: CpassJsonModel? = nil,
        is_flow: Int = 0,
        template_type: Int? = nil,
        source: String? = nil,
        interactive: InteractiveModel? = nil,
        isVideo: Bool = false,
        shoot_id: String? = nil,
        message_id: String? = nil,
        trace_id: String? = nil
    ) {
        self.session_id = session_id
        self.message = message
        self.suggested_questions = suggested_questions
        self.sent_by = sent_by
        self.created_at = created_at
        self.updated_at = updated_at
        self.session = session
        self.cpaas_json = cpaas_json
        self.is_flow = is_flow
        self.template_type = template_type
        self.source = source
        self.interactive = interactive
        self.isVideo = isVideo
        self.shoot_id = shoot_id
        self.message_id = message_id
        self.trace_id = trace_id
    }

    /// Lenient decode. The chat endpoints return this in several shapes:
    ///  - the sparse "flow handled" placeholder omits sent_by/created_at/updated_at/session,
    ///  - history & bot replies send `session` as an Int (the DB id) — not a String,
    ///  - various string fields arrive as JSON null.
    /// A strict decode threw on all of these and failed the whole chat/history response, so
    /// decode every field defensively (mirrors `EnterPriseModel`).
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func str(_ k: CodingKeys) -> String? { ((try? c.decodeIfPresent(String.self, forKey: k)) ?? nil) }

        session_id = str(.session_id)
        message = str(.message) ?? ""
        suggested_questions = (try? c.decodeIfPresent([String].self, forKey: .suggested_questions)) ?? nil
        sent_by = str(.sent_by) ?? ""
        created_at = str(.created_at) ?? ""
        updated_at = str(.updated_at) ?? ""
        // `session` is an Int (DB id) in history/reply payloads and absent in the placeholder —
        // accept either form as a String.
        if let s = str(.session) {
            session = s
        } else if let i = ((try? c.decodeIfPresent(Int.self, forKey: .session)) ?? nil) {
            session = String(i)
        } else {
            session = ""
        }
        cpaas_json = (try? c.decodeIfPresent(CpassJsonModel.self, forKey: .cpaas_json)) ?? nil
        is_flow = ((try? c.decodeIfPresent(Int.self, forKey: .is_flow)) ?? nil) ?? 0
        // template_type is an IntegerField server-side but tolerate a numeric String too.
        if let i = ((try? c.decodeIfPresent(Int.self, forKey: .template_type)) ?? nil) {
            template_type = i
        } else {
            template_type = Int(str(.template_type) ?? "")
        }
        source = str(.source)
        interactive = (try? c.decodeIfPresent(InteractiveModel.self, forKey: .interactive)) ?? nil
        shoot_id = str(.shoot_id)
        message_id = str(.message_id)
        trace_id = str(.trace_id)
        isVideo = false
    }
}

/// Optional interactive payload attached to a bot [MessageModel] by the GLM flow
/// (see docs `MESSAGE_FLOW_GLM.md` §4). The server caps buttons at 5 and clamps labels.
public struct InteractiveModel: Codable {
    public var version: Int?
    public var type: String?
    public var buttons: [InteractiveButton]?
}

public struct InteractiveButton: Codable {
    public var label: String?
    /// "quick_reply" (send value as next message), "url" (open link),
    /// or "action" (named in-app capability; degrades gracefully if unhandled).
    public var type: String?
    public var value: String?
}

// All fields optional: a CPaaS/template message only populates the fields relevant to
// its template type, and the others arrive as JSON null. With Codable, a non-optional
// field + null/missing key makes the WHOLE decode throw, so the chat would fail to load
// a pushed template message. Keep them optional and null-guard at the call sites.
public struct CpassJsonModel: Codable {
    // The backend serializes this as a string (e.g. "71"). Android's Gson coerces it to Int,
    // but Swift's Codable is strict, so decode it as the String it actually is on the wire.
    public var id: String?
    public var templ_name: String?
    public var templ_typ: String?
    public var media_size: String?
    public var thumbnail: String?
    public var templ_lang: String?
    public var head_typ: String?
    public var is_headertxt: String?
    public var header_txt: String?
    public var is_header_img: String?
    public var image_url: String?
    public var is_header_aud: String?
    public var audio_url: String?
    public var is_header_vid: String?
    public var video_url: String?
    public var is_header_doc: String?
    public var doc_url: String?
    public var msg: String?
    public var footer: String?
    public var button: [ButtonModel]?
    public var product: [ProductModel]?

    enum CodingKeys: String, CodingKey {
        case id, templ_name, templ_typ, media_size, thumbnail, templ_lang, head_typ,
             is_headertxt, header_txt, is_header_img, image_url, is_header_aud, audio_url,
             is_header_vid, video_url, is_header_doc, doc_url, msg, footer, button, product
    }

    public init(id: String? = nil, templ_typ: String? = nil, is_header_img: String? = nil,
                image_url: String? = nil, is_header_vid: String? = nil, video_url: String? = nil,
                is_header_doc: String? = nil, doc_url: String? = nil, thumbnail: String? = nil,
                msg: String? = nil, footer: String? = nil,
                button: [ButtonModel]? = nil, product: [ProductModel]? = nil) {
        self.id = id; self.templ_typ = templ_typ; self.is_header_img = is_header_img
        self.image_url = image_url; self.is_header_vid = is_header_vid; self.video_url = video_url
        self.is_header_doc = is_header_doc; self.doc_url = doc_url; self.thumbnail = thumbnail
        self.msg = msg; self.footer = footer; self.button = button; self.product = product
    }

    /// Lenient decode. A CPaaS/template message only populates the fields relevant to its type
    /// (others arrive as null), and the backend is inconsistent about numbers-as-strings — e.g.
    /// `id` and the `is_header_*` flags come back as `71` / `1` (Int) on some endpoints and
    /// `"71"` / `"1"` (String) on others. With the synthesized decoder any single such mismatch
    /// threw and — because MessageModel swallows a cpaas_json decode error — dropped the ENTIRE
    /// template, so a real template/OTP message rendered as the bare "FROM CPAAS" placeholder.
    /// Decode every scalar as an Int-or-String tolerant String. See docs/ios-codable-audit.md.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func flex(_ k: CodingKeys) -> String? { CpassJsonModel.flexString(c, k) }
        id = flex(.id); templ_name = flex(.templ_name); templ_typ = flex(.templ_typ)
        media_size = flex(.media_size); thumbnail = flex(.thumbnail); templ_lang = flex(.templ_lang)
        head_typ = flex(.head_typ); is_headertxt = flex(.is_headertxt); header_txt = flex(.header_txt)
        is_header_img = flex(.is_header_img); image_url = flex(.image_url)
        is_header_aud = flex(.is_header_aud); audio_url = flex(.audio_url)
        is_header_vid = flex(.is_header_vid); video_url = flex(.video_url)
        is_header_doc = flex(.is_header_doc); doc_url = flex(.doc_url)
        msg = flex(.msg); footer = flex(.footer)
        button = (try? c.decodeIfPresent([ButtonModel].self, forKey: .button)) ?? nil
        product = (try? c.decodeIfPresent([ProductModel].self, forKey: .product)) ?? nil
    }

    /// Decodes a scalar as a String whether the wire sends a String, an Int, or a Double.
    static func flexString<K: CodingKey>(_ c: KeyedDecodingContainer<K>, _ key: K) -> String? {
        if let s = ((try? c.decodeIfPresent(String.self, forKey: key)) ?? nil) { return s }
        if let i = ((try? c.decodeIfPresent(Int.self, forKey: key)) ?? nil) { return String(i) }
        if let d = ((try? c.decodeIfPresent(Double.self, forKey: key)) ?? nil) { return String(d) }
        return nil
    }
}

public struct ButtonModel: Codable {
    public var btn_typ: String?
    public var btn_txt: String?
    public var btn_value: String?
}

public struct ProductModel: Codable {
    public var id: String?
    public var templ_id: String?
    public var prod_id: String?
    public var img_opt: String?
    public var prod_nm: String?
    public var prod_prc: String?
    public var prod_dsc: String?
    public var img_url: String?
    public var addtocrt: String?
    public var addttocrt_url: String?
    public var buynw: String?
    public var buynw_url: String?
    public var created_at: String?
}
