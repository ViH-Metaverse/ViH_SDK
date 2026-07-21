package com.vihmessenger.vihchatbot.ui.custom

import android.content.Context
import android.util.AttributeSet
import androidx.viewpager.widget.ViewPager
import com.vihmessenger.vihchatbot.R

class CustomViewPager : ViewPager {
    constructor(context: Context?) : super(context!!) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        init()
    }

    private fun init() {
        // Add padding to show part of adjacent pages
        val padding = resources.getDimensionPixelOffset(R.dimen._15sdp)
        setPadding(5, 0, padding, 0)
        clipToPadding = false

        // Set page margin to create space between items
        val pageMargin = resources.getDimensionPixelOffset(R.dimen._10sdp)
        setPageMargin(pageMargin)

        // Add layout change listener to handle delayed height adjustments
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var maxHeight = 0

        // Use UNSPECIFIED to let children determine their own size
        val childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(widthMeasureSpec, childHeightMeasureSpec)
            maxHeight = Math.max(maxHeight, child.measuredHeight)
        }

        // Add padding to the max height
        maxHeight += paddingTop + paddingBottom

        // Ensure we have a minimum height if needed
        if (maxHeight <= 0) {
            maxHeight = resources.getDimensionPixelOffset(com.intuit.sdp.R.dimen._400sdp)
        }

        // Add some extra buffer to prevent cutting off
        maxHeight += resources.getDimensionPixelOffset(com.intuit.sdp.R.dimen._20sdp)

        val finalHeightSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, finalHeightSpec)
    }
}