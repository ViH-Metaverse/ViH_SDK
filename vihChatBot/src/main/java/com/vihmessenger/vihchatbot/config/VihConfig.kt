package com.vihmessenger.vihchatbot.config

/**
 * White-label / customization configuration passed by the integrating host app at launch.
 *
 * Everything is optional — an unset field falls back to the server SDK-features value, then
 * the SDK default (host config wins; see the customization design spec). Pass a [VihConfig]
 * to [com.vihmessenger.vihchatbot.utils.FloatingButtonView.startSdk] to reshape the SDK UI
 * (tabs + brand colors) without a custom build.
 */
data class VihConfig(
    val theme: VihTheme? = null,
    val navigation: VihNavigation? = null,
)

/**
 * Brand colors. Phase 1a honors [primary] / [onPrimary] (+ [secondary]/[accent]); each is a
 * `"#RRGGBB"` string. Unset fields keep the server/default color.
 */
data class VihTheme(
    val primary: String? = null,     // brand / accent — bottom nav, buttons, headers
    val onPrimary: String? = null,   // text/icon color on primary surfaces
    val secondary: String? = null,   // secondary accent (falls back to accent)
    val accent: String? = null,
)

/**
 * The tab set the SDK renders — exactly [tabs], in order, left→right. Omitting a tab from the
 * list means it is not shown. [defaultTab] is selected on open (defaults to the first tab, or
 * CHATS if present).
 */
data class VihNavigation(
    val tabs: List<VihTab>,
    val defaultTab: VihTabId? = null,
)

/**
 * A single tab. [label] and [icon] override the SDK's default title / drawable for that
 * surface. [icon] is a drawable resource id in the host app or the SDK.
 */
data class VihTab(
    val id: VihTabId,
    val label: String? = null,
    val icon: Int? = null,
)

/**
 * The surfaces the SDK can render as a tab. `CHATS` is the unified conversation list;
 * `PROMO` / `TRANSACTIONAL` / `OTP` are the same list filtered to conversations whose latest
 * message is that category (`template_type` 2 / 3 / 1). Use either `CHATS` or a subset of the
 * category tabs — not both.
 */
enum class VihTabId(val itemId: Int) {
    DISCOVER(1001),
    CHATS(1002),
    PROMO(1003),
    TRANSACTIONAL(1004),
    OTP(1005),
    SETTINGS(1006);

    /** The `cpaas_json.templ_typ` this tab filters conversations by, or null for non-chat tabs. */
    val templateType: String?
        get() = when (this) {
            OTP -> "1"
            PROMO -> "2"
            TRANSACTIONAL -> "3"
            else -> null
        }

    companion object {
        fun fromItemId(id: Int): VihTabId? = entries.firstOrNull { it.itemId == id }
    }
}

/**
 * Process-wide holder for the active [VihConfig]. Set by `startSdk` before the dashboard
 * launches; read by the dashboard when composing tabs and applying the theme override. A null
 * config means "legacy behavior" (the three static tabs, server-driven colors).
 */
object VihConfigStore {
    @Volatile
    var config: VihConfig? = null
        private set

    fun set(newConfig: VihConfig?) {
        config = newConfig
    }

    val navigation: VihNavigation? get() = config?.navigation
    val theme: VihTheme? get() = config?.theme
}
