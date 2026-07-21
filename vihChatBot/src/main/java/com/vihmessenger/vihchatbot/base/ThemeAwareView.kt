package com.vihmessenger.vihchatbot.base

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.vihmessenger.vihchatbot.utils.DynamicThemeManager

abstract class ThemeAwareView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), DynamicThemeManager.ThemeChangeListener {

    protected var primaryColor: Int = DynamicThemeManager.getPrimaryColor()
    protected var secondaryColor: Int = DynamicThemeManager.getSecondaryColor()
    protected var primaryTextColor: Int = DynamicThemeManager.getPrimaryTextColor()
    protected var secondaryTextColor: Int = DynamicThemeManager.getSecondaryTextColor()

    init {
        DynamicThemeManager.registerListener(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        DynamicThemeManager.registerListener(this)
    }

    override fun onDetachedFromWindow() {
        DynamicThemeManager.unregisterListener(this)
        super.onDetachedFromWindow()
    }

    override fun onThemeChanged(
        primaryColor: Int,
        secondaryColor: Int,
        primaryTextColor: Int,
        secondaryTextColor: Int,
        headerColor: Int,
        defaultTextColor: Int
    ) {
        this.primaryColor = primaryColor
        this.secondaryColor = secondaryColor
        this.primaryTextColor = primaryTextColor
        this.secondaryTextColor = secondaryTextColor
        invalidate() // Request redraw with new colors
    }

    // Optional: Add a backward-compatible method for subclasses that only care about primary color
    protected open fun applyPrimaryColorChange(primaryColor: Int) {
        // Subclasses can override this if they only need to respond to primary color changes
    }
}