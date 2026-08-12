package com.vihmessenger.vihchatbot.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.vihmessenger.vihchatbot.R

// Create this as a new file: DynamicThemeManager.kt
object DynamicThemeManager {

    private const val TAG = "DynamicThemeManager"
    private var primaryColor: Int = 0
    private var secondaryColor: Int = 0
    private var primaryTextColor: Int = 0
    private var secondaryTextColor: Int = 0
    private var headerColor: Int = Color.parseColor("#000000") // <-- Default value
    private var defaultTextColor: Int = Color.parseColor("#333333") // <-- New field
    private val listeners = mutableListOf<ThemeChangeListener>()

    // Readability-first dark mode: in dark mode body text is forced light so it stays
    // legible on dark backgrounds, regardless of the tenant's (often dark) font color.
    // Tenant primary/accent/header colors are left untouched.
    @Volatile private var appContext: Context? = null
    private val NIGHT_DEFAULT_TEXT = Color.parseColor("#ECECEC")
    private val NIGHT_SURFACE = Color.parseColor("#1E1E1E")

    private fun isNightMode(): Boolean {
        val cfg = appContext?.resources?.configuration ?: return false
        return (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    /** True for near-white / light colors (perceptual luminance). Used to decide which
     *  tenant surfaces are "light defaults" that should flip to dark in dark mode. A
     *  genuinely dark/branded header keeps its color. */
    private fun isLight(color: Int): Boolean {
        val lum = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
        return lum > 0.6
    }

    /**
     * Replaces a fully transparent color with [fallback]. A zero alpha is never a legitimate
     * theme value — it is what a failed lookup or an uninitialised field leaves behind, and it
     * renders as an invisible bubble or button rather than as an obvious error.
     */
    private fun Int.orDefault(fallback: Int): Int = if (Color.alpha(this) == 0) fallback else this

    /**
     * Ensures a usable palette exists, without overwriting colors already applied from the
     * server or from a host [com.vihmessenger.vihchatbot.config.VihTheme].
     *
     * The tenant's real colors arrive via [setColorsFromApi], which only [DashboardFragment]
     * calls. Screens reachable without the Dashboard — chat opened directly through
     * `VihDiscover.openChat` — must still render something legible, so they call this.
     */
    fun ensureDefaults(context: Context) {
        if (Color.alpha(primaryColor) != 0) return
        loadSavedTheme(context)
    }

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_PRIMARY_COLOR = "primary_color"
    private const val KEY_SECONDARY_COLOR = "secondary_color"
    private const val KEY_PRIMARY_TEXT_COLOR = "primary_text_color"
    private const val KEY_SECONDARY_TEXT_COLOR = "secondary_text_color"
    private const val KEY_HEADER_COLOR = "header_color"
    private const val KEY_DEFAULT_TEXT_COLOR = "default_text_color" // <-- New key

    interface ThemeChangeListener {
        fun onThemeChanged(
            primaryColor: Int, secondaryColor: Int,
            primaryTextColor: Int, secondaryTextColor: Int,
            headerColor: Int, defaultTextColor: Int // <-- Updated interface
        )
    }

    fun registerListener(listener: ThemeChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            listener.onThemeChanged(
                primaryColor, secondaryColor,
                primaryTextColor, secondaryTextColor,
                getHeaderColor(), getDefaultTextColor() // night-aware header + body text
            )
        }
    }

    fun unregisterListener(listener: ThemeChangeListener) {
        listeners.remove(listener)
    }

    fun setThemeColors(
        context: Context,
        primary: Int, secondary: Int,
        primaryText: Int, secondaryText: Int,
        header: Int, defaultText: Int // <-- Updated parameter list
    ) {
        appContext = context.applicationContext
        this.primaryColor = primary
        this.secondaryColor = secondary
        this.primaryTextColor = primaryText
        this.secondaryTextColor = secondaryText
        this.headerColor = header
        this.defaultTextColor = defaultText // <-- Set new field

        saveColorsToPrefs(context)
        notifyListeners()
    }

    fun getPrimaryColor(): Int = primaryColor
    fun getSecondaryColor(): Int = secondaryColor
    fun getPrimaryTextColor(): Int = primaryTextColor
    fun getSecondaryTextColor(): Int = secondaryTextColor
    // Night-aware: a light/near-white header (the default, and light tenant headers)
    // becomes a dark surface in dark mode so its light title/icons stay readable; a
    // genuinely dark/branded header is kept as-is.
    fun getHeaderColor(): Int = if (isNightMode() && isLight(headerColor)) NIGHT_SURFACE else headerColor
    // Night-aware: light text in dark mode, tenant/default text color otherwise.
    fun getDefaultTextColor(): Int = if (isNightMode()) NIGHT_DEFAULT_TEXT else defaultTextColor

    private fun notifyListeners() {
        listeners.forEach {
            it.onThemeChanged(
                primaryColor, secondaryColor,
                primaryTextColor, secondaryTextColor,
                getHeaderColor(), getDefaultTextColor() // night-aware header + body text
            )
        }
    }

    private fun saveColorsToPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt(KEY_PRIMARY_COLOR, primaryColor)
            putInt(KEY_SECONDARY_COLOR, secondaryColor)
            putInt(KEY_PRIMARY_TEXT_COLOR, primaryTextColor)
            putInt(KEY_SECONDARY_TEXT_COLOR, secondaryTextColor)
            putInt(KEY_HEADER_COLOR, headerColor)
            putInt(KEY_DEFAULT_TEXT_COLOR, defaultTextColor) // <-- Save default text color
            apply()
        }
    }

    fun loadSavedTheme(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        primaryColor = prefs.getInt(KEY_PRIMARY_COLOR, getDefaultPrimaryColor(context))
            .orDefault(getDefaultPrimaryColor(context))
        secondaryColor = prefs.getInt(KEY_SECONDARY_COLOR, getDefaultSecondaryColor(context))
            .orDefault(getDefaultSecondaryColor(context))
        primaryTextColor = prefs.getInt(KEY_PRIMARY_TEXT_COLOR, getDefaultPrimaryTextColor(context))
            .orDefault(getDefaultPrimaryTextColor(context))
        secondaryTextColor =
            prefs.getInt(KEY_SECONDARY_TEXT_COLOR, getDefaultSecondaryTextColor(context))
                .orDefault(getDefaultSecondaryTextColor(context))
        headerColor = prefs.getInt(
            KEY_HEADER_COLOR,
            Color.parseColor("#FEFEFE")
        )
        defaultTextColor = prefs.getInt(
            KEY_DEFAULT_TEXT_COLOR,
            Color.parseColor("#333333") // <-- Load default text color or fallback
        )
    }

    /**
     * SDK brand defaults, used whenever the tenant's server colors have not been applied.
     *
     * These used to call `context.theme.resolveAttribute(R.color.primarycolor, …)`, which is a
     * misuse of the API — `resolveAttribute` takes an **attr** id, not a color res id. It failed
     * silently and left `typedValue.data` at 0, i.e. fully transparent. That never showed on the
     * SDK's own flows because [DashboardFragment] overwrites every color from `sdk-features`
     * before any chat screen appears, but on the host-driven path (VihDiscover.openChat straight
     * into ChatActivity) the Dashboard never runs, so every color stayed 0 and the outgoing
     * message bubble, its text and the send button all rendered fully transparent.
     */
    private fun getDefaultPrimaryColor(context: Context): Int =
        ContextCompat.getColor(context, R.color.primarycolor)

    private fun getDefaultSecondaryColor(context: Context): Int =
        ContextCompat.getColor(context, R.color.secondarycolor)

    private fun getDefaultPrimaryTextColor(context: Context): Int =
        ContextCompat.getColor(context, R.color.primarytextcolor)

    private fun getDefaultSecondaryTextColor(context: Context): Int =
        ContextCompat.getColor(context, R.color.secondarytextcolor)

    fun setColorsFromApi(
        context: Context,
        primaryColorHex: String,
        secondaryColorHex: String,
        primaryTextColorHex: String,
        secondaryTextColorHex: String,
        headerColorHex: String,
        defaultTextColorHex: String // <-- New parameter
    ) {
        try {
            val primary = Color.parseColor(primaryColorHex)
            val secondary = Color.parseColor(secondaryColorHex)
            val primaryText = Color.parseColor(primaryTextColorHex)
            val secondaryText = Color.parseColor(secondaryTextColorHex)
            val header = Color.parseColor(headerColorHex)
            val defaultText = Color.parseColor(defaultTextColorHex) // <-- Parse new color

            setThemeColors(context, primary, secondary, primaryText, secondaryText, header, defaultText)
        } catch (e: Exception) {
            VihLog.e(TAG, "Invalid color format", e)
        }
    }

    /**
     * White-label override: applies the host app's brand colors on top of whatever colors are
     * currently set (server features or defaults), overriding ONLY the fields it supplies.
     * This is the "host config wins" precedence for [com.vihmessenger.vihchatbot.config.VihTheme].
     * Malformed hex is ignored per-field. Call after server colors have been applied.
     */
    fun applyHostOverride(
        context: Context,
        primaryHex: String?,
        onPrimaryHex: String?,
        secondaryHex: String?,
        accentHex: String?
    ) {
        appContext = context.applicationContext
        var changed = false
        fun parse(hex: String?): Int? = try {
            hex?.let { Color.parseColor(it) }
        } catch (e: Exception) {
            VihLog.e(TAG, "Invalid host override color: $hex", e); null
        }

        parse(primaryHex)?.let { primaryColor = it; changed = true }
        parse(onPrimaryHex)?.let { primaryTextColor = it; changed = true }
        (parse(secondaryHex) ?: parse(accentHex))?.let { secondaryColor = it; changed = true }

        if (changed) {
            saveColorsToPrefs(context)
            notifyListeners()
        }
    }
}