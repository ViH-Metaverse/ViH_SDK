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
    val diagnostics: VihDiagnostics = VihDiagnostics(),
    val security: VihSecurity = VihSecurity(),
)

/**
 * Platform hardening the SDK applies to its own screens (VAPT F-09).
 *
 * Defaults are the secure ones. They are configurable because a partner may have a
 * legitimate reason to relax them — but relaxing has to be a deliberate, auditable choice
 * in the host app's code rather than the SDK's silent default.
 */
data class VihSecurity(
    /**
     * Sets `FLAG_SECURE` on SDK windows, which blocks screenshots, screen recording and
     * non-secure display mirroring, and excludes the screen from the recents thumbnail.
     *
     * On by default: this SDK renders OTPs and transactional messages, and screen-capture
     * malware plus accessibility-abuse trojans are the dominant OTP-theft technique on
     * Android. Set false only if a partner explicitly accepts that risk (e.g. they need
     * users to screenshot promotional content).
     */
    val blockScreenCapture: Boolean = true,

    /**
     * Permit the voice-bot call to run over a cleartext `ws://` socket (VAPT F-04).
     *
     * Vestigial for the platform voice bot: v2 agents are served over `wss://`, which this
     * flag never gated, and the SDK no longer ships a cleartext exception for the old fixed
     * voice host. The switch survives only for a self-hosted `ws://` agent.
     *
     * Leave it off. On a cleartext socket the entire PCM conversation traverses the network
     * unencrypted in both directions, so anyone on the path — hostile Wi-Fi, ISP, compromised
     * router — can record the call verbatim and impersonate the bot to the user. Off by
     * default so the SDK fails closed; a partner who accepts that risk must say so explicitly
     * in their own code (and add their own cleartext network-security exception), which makes
     * the decision auditable.
     */
    val allowInsecureVoiceTransport: Boolean = false,
)

/**
 * Controls what leaves the device for diagnostics (VAPT F-14).
 *
 * The SDK ships with a Bugfender integration. Bugfender is a *remote* log sink: explicit
 * `Bugfender.d/i/w/e` calls upload to a third-party processor regardless of build type —
 * the boolean passed to `Bugfender.init` only controls logcat mirroring, not upload. In an
 * SDK embedded in someone else's app that is a data flow the host app's operator must
 * consent to, not one the SDK may assume, so it now defaults to **off**.
 *
 * Host apps that want it must opt in explicitly and are responsible for disclosing the
 * processor in their own privacy notice and DPA.
 */
data class VihDiagnostics(
    /** Upload SDK diagnostic logs to Bugfender. Off by default; requires a configured key. */
    val remoteLoggingEnabled: Boolean = false,
    /** Upload crash reports to Bugfender. Off by default; requires a configured key. */
    val crashReportingEnabled: Boolean = false,
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
        // Diagnostics opt-in only becomes knowable once the host supplies a config, so this
        // is the correct moment to honour it (VAPT F-14).
        com.vihmessenger.vihchatbot.AppController.applyDiagnosticsConfig()
    }

    val navigation: VihNavigation? get() = config?.navigation
    val theme: VihTheme? get() = config?.theme
}
