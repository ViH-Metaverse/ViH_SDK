package com.vihmessenger.vihchatbot.base

import androidx.recyclerview.widget.RecyclerView
import com.vihmessenger.vihchatbot.utils.DynamicThemeManager

abstract class ThemeAwareAdapter<VH : RecyclerView.ViewHolder> :
    RecyclerView.Adapter<VH>(), DynamicThemeManager.ThemeChangeListener {

    protected var primaryColor: Int = DynamicThemeManager.getPrimaryColor()
    protected var secondaryColor: Int = DynamicThemeManager.getSecondaryColor()
    protected var primaryTextColor: Int = DynamicThemeManager.getPrimaryTextColor()
    protected var secondaryTextColor: Int = DynamicThemeManager.getSecondaryTextColor()
    protected var headerColor: Int = DynamicThemeManager.getHeaderColor()
    protected var defaultTextColor: Int = DynamicThemeManager.getDefaultTextColor()

    init {
        DynamicThemeManager.registerListener(this)
    }

    fun cleanup() {
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
        notifyDataSetChanged()
    }
}