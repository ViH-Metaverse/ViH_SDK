package com.vihmessenger.vihchatbot.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
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
        secondaryColor = prefs.getInt(KEY_SECONDARY_COLOR, getDefaultSecondaryColor(context))
        primaryTextColor = prefs.getInt(KEY_PRIMARY_TEXT_COLOR, getDefaultPrimaryTextColor(context))
        secondaryTextColor =
            prefs.getInt(KEY_SECONDARY_TEXT_COLOR, getDefaultSecondaryTextColor(context))
        headerColor = prefs.getInt(
            KEY_HEADER_COLOR,
            Color.parseColor("#FEFEFE")
        )
        defaultTextColor = prefs.getInt(
            KEY_DEFAULT_TEXT_COLOR,
            Color.parseColor("#333333") // <-- Load default text color or fallback
        )
    }

    private fun getDefaultPrimaryColor(context: Context): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.color.primarycolor, typedValue, true)
        return typedValue.data
    }

    private fun getDefaultSecondaryColor(context: Context): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.color.secondarycolor, typedValue, true)
        return typedValue.data
    }

    private fun getDefaultPrimaryTextColor(context: Context): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.color.primarytextcolor, typedValue, true)
        return typedValue.data
    }

    private fun getDefaultSecondaryTextColor(context: Context): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.color.secondarytextcolor, typedValue, true)
        return typedValue.data
    }

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
            Log.e(TAG, "Invalid color format", e)
        }
    }
}