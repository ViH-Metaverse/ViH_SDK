import UIKit

/// White-label / customization configuration for the SDK UI. iOS mirror of Android's
/// `com.vihmessenger.vihchatbot.config.VihConfig`. Pass it via
/// `VihChatBotSDK.shared.configure(_:ui:)` or `setUIConfig(_:)` to reshape the SDK's tabs
/// and brand colors without a custom build. Everything is optional — an unset field falls
/// back to the server SDK-features value, then the SDK default (host config wins).
public struct VihUIConfig {
    public let theme: VihTheme?
    public let navigation: VihNavigation?

    public init(theme: VihTheme? = nil, navigation: VihNavigation? = nil) {
        self.theme = theme
        self.navigation = navigation
    }
}

/// Brand colors. Phase 1a honors `primary` / `onPrimary` (+ `secondary`/`accent`); each is a
/// `"#RRGGBB"` string. Unset fields keep the server/default color.
public struct VihTheme {
    public let primary: String?     // brand / accent — tab bar selection, buttons, headers
    public let onPrimary: String?   // text/icon color on primary surfaces
    public let secondary: String?   // secondary accent (falls back to accent)
    public let accent: String?

    public init(primary: String? = nil, onPrimary: String? = nil, secondary: String? = nil, accent: String? = nil) {
        self.primary = primary
        self.onPrimary = onPrimary
        self.secondary = secondary
        self.accent = accent
    }
}

/// The tab set the SDK renders — exactly `tabs`, in order. Omitting a tab means it isn't
/// shown. `defaultTab` is selected on open (defaults to the first tab, or `.chats` if present).
public struct VihNavigation {
    public let tabs: [VihTab]
    public let defaultTab: VihTabId?

    public init(tabs: [VihTab], defaultTab: VihTabId? = nil) {
        self.tabs = tabs
        self.defaultTab = defaultTab
    }
}

/// A single tab. `label` and `icon` override the SDK's default title / image for that surface.
public struct VihTab {
    public let id: VihTabId
    public let label: String?
    public let icon: UIImage?

    public init(_ id: VihTabId, label: String? = nil, icon: UIImage? = nil) {
        self.id = id
        self.label = label
        self.icon = icon
    }
}

/// The surfaces the SDK can render as a tab. `chats` is the unified conversation list;
/// `promo` / `transactional` / `otp` filter that list by `template_type` (2 / 3 / 1) — though
/// `promo` currently shows all active chats, matching Android (see `templateType`).
public enum VihTabId: CaseIterable {
    case discover
    case chats
    case promo
    case transactional
    case otp
    case settings

    /// The `cpaas_json.templ_typ` this tab filters conversations by, or nil for no filter.
    /// `promo` is nil (shows all active chats) — a conversation-level promo classification
    /// isn't available from the chat-list API, so filtering to it would hide real chats.
    public var templateType: String? {
        switch self {
        case .otp: return "1"
        case .transactional: return "3"
        default: return nil
        }
    }
}
