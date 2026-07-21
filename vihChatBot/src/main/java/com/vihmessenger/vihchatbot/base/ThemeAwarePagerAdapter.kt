package com.vihmessenger.vihchatbot.base

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import com.vihmessenger.vihchatbot.utils.DynamicThemeManager

abstract class ThemeAwarePagerAdapter : PagerAdapter(), DynamicThemeManager.ThemeChangeListener {

    // Store theme colors, accessible by subclasses
    protected var primaryColor: Int = DynamicThemeManager.getPrimaryColor()
    protected var secondaryColor: Int = DynamicThemeManager.getSecondaryColor()
    protected var primaryTextColor: Int = DynamicThemeManager.getPrimaryTextColor()
    protected var secondaryTextColor: Int = DynamicThemeManager.getSecondaryTextColor()
    protected var headerColor: Int = DynamicThemeManager.getHeaderColor()
    protected var defaultTextColor: Int = DynamicThemeManager.getDefaultTextColor()

    // Flag to prevent calling notifyDataSetChanged before constructor finishes in subclasses
    private var isInitialized = false

    init {
        DynamicThemeManager.registerListener(this)
        isInitialized = true // Allow notifyDataSetChanged after init
    }

    /**
     * Call this method when the adapter is no longer needed
     * (e.g., in Fragment's onDestroyView or Activity's onDestroy)
     * to prevent memory leaks.
     */
    open fun cleanup() {
        DynamicThemeManager.unregisterListener(this)
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
        this.headerColor = headerColor
        this.defaultTextColor = defaultTextColor

        // Only notify if the adapter has been fully initialized
        if (isInitialized) {
            // This tells the ViewPager that the data might have changed,
            // prompting it to potentially update/recreate views.
            // instantiateItem will then be called with the new colors available.
            notifyDataSetChanged()
        }
    }

    // Optional: Provide context easily if needed often by subclasses
    // protected fun getContext(container: ViewGroup): Context = container.context

    // You might force subclasses to implement certain methods if needed,
    // but for basic theme awareness, inheriting PagerAdapter's abstract methods is enough.
}