package com.vihmessenger.vihchatbot.utils.indicator

import android.content.Context
import android.content.res.TypedArray
import android.database.DataSetObserver
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.shapes.RectShape
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.RadioGroup
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.core.content.ContextCompat
import androidx.viewpager.widget.ViewPager
import com.vihmessenger.vihchatbot.R


class ViewPagerIndicator2(context: Context, attrs: AttributeSet?) :
    RadioGroup(context, attrs) {
    private var mViewPager: ViewPager? = null
    private var mItemDividerWidth = 0
    private var mButtonDrawable: Drawable? = null
    private var mItemWidth = 0
    private var mItemHeight = 0
    private var mItemSelectedColor = 0
    private var mItemUnselectedColor = 0

    private val selectorDrawable: StateListDrawable?
        get() {
            var d: StateListDrawable? = null
            try {
                d = StateListDrawable()
                // Create rectangular shape for selected state
                val selectedDrawable = ShapeDrawable(RectShape())
                selectedDrawable.paint.color = mItemSelectedColor
                selectedDrawable.intrinsicHeight = mItemHeight
                selectedDrawable.intrinsicWidth = mItemWidth

                // Create rectangular shape for unselected state
                val unselectedDrawable = ShapeDrawable(RectShape())
                unselectedDrawable.paint.color = mItemUnselectedColor
                unselectedDrawable.intrinsicHeight = mItemHeight
                unselectedDrawable.intrinsicWidth = mItemWidth

                d.addState(intArrayOf(android.R.attr.state_checked), selectedDrawable)
                d.addState(intArrayOf(), unselectedDrawable)
            } catch (e: Exception) {
                Log.e(TAG, getMessageFor(e))
            }
            return d
        }

    @Throws(IllegalStateException::class)
    fun initWithViewPager(viewPager: ViewPager?) {
        if (viewPager == null) return
        checkNotNull(viewPager.adapter) { "ViewPager has no adapter set." }
        try {
            mViewPager = viewPager
            mViewPager?.addOnPageChangeListener(mOnPageChangeListener)
            mViewPager?.adapter?.registerDataSetObserver(object : DataSetObserver() {
                override fun onChanged() {
                    super.onChanged()
                    addViews()
                }
            })
            mViewPager?.let {
                // For regular ViewPager, we only need horizontal orientation
                this.orientation = LinearLayout.HORIZONTAL
                addViews()
            }
        } catch (e: Exception) {
            Log.e(TAG, getMessageFor(e))
        }
    }

    /**
     * Add page indicators based on the attached ViewPager
     */
    private fun addViews() {
        try {
            if (mViewPager == null || mViewPager!!.adapter == null || mViewPager!!.adapter!!.count == 0) return

            removeAllViews()
            val firstItem = AppCompatRadioButton(context)
            firstItem.text = ""
            firstItem.buttonDrawable = mButtonDrawable!!.constantState!!.newDrawable()
            var params = LayoutParams(mItemWidth, mItemHeight)
            firstItem.layoutParams = params
            firstItem.isClickable = false
            addView(firstItem)

            for (i in 1 until mViewPager!!.adapter!!.count) {
                val item = AppCompatRadioButton(context)
                item.text = ""
                item.buttonDrawable = mButtonDrawable!!.constantState!!.newDrawable()
                params = LayoutParams(mItemWidth, mItemHeight)
                params.setMargins(mItemDividerWidth, 0, 0, 0)
                item.layoutParams = params
                item.isClickable = false
                addView(item)
            }
            check(firstItem.id)
        } catch (e: Exception) {
            Log.e(TAG, getMessageFor(e))
        }
    }

    private val mOnPageChangeListener = object : ViewPager.OnPageChangeListener {
        override fun onPageSelected(position: Int) {
            try {
                this@ViewPagerIndicator2.check(getChildAt(position).id)
            } catch (e: Exception) {
                Log.e(TAG, getMessageFor(e))
            }
        }

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int
        ) {
            // Not needed for basic indicator functionality
        }

        override fun onPageScrollStateChanged(state: Int) {
            // Not needed for basic indicator functionality
        }
    }

    /**
     * Always get a message for an exception
     * @param e an Exception
     * @return a String describing the Exception
     */
    private fun getMessageFor(e: Exception?): String {
        e?.let {
            it.message?.let { message ->
                return message
            }
            return e.javaClass.name + ": No Message."
        }
        return "$TAG: No Message."
    }

    companion object {
        private const val TAG = "ViewPagerIndicator"
    }

    init {
        try {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            val a: TypedArray = context.theme.obtainStyledAttributes(
                attrs,
                R.styleable.ViewPagerIndicator,
                0, 0
            )

            // Get width and height for rectangular indicators
            mItemWidth = resources.getDimensionPixelSize(R.dimen.default_item_width)
            mItemWidth =
                a.getDimensionPixelSize(R.styleable.ViewPagerIndicator_itemWidth, mItemWidth)

            mItemHeight = resources.getDimensionPixelSize(R.dimen.default_item_height)
            mItemHeight =
                a.getDimensionPixelSize(R.styleable.ViewPagerIndicator_itemHeight, mItemHeight)

            mItemDividerWidth = a.getDimensionPixelSize(
                R.styleable.ViewPagerIndicator_itemDividerWidth,
                resources.getDimensionPixelSize(R.dimen.default_item_divider_width)
            )

            val theme = a.getInt(R.styleable.ViewPagerIndicator_defaultIndicatorTheme, 0)
            if (theme == 0) {
                mItemSelectedColor =
                    ContextCompat.getColor(getContext(), R.color.default_indicator_on)
                mItemUnselectedColor =
                    ContextCompat.getColor(getContext(), R.color.default_indicator_off)
            } else {
                mItemSelectedColor =
                    ContextCompat.getColor(getContext(), R.color.default_indicator_light_on)
                mItemUnselectedColor =
                    ContextCompat.getColor(getContext(), R.color.default_indicator_light_off)
            }

            mItemSelectedColor =
                a.getColor(R.styleable.ViewPagerIndicator_itemSelectedColor, mItemSelectedColor)
            mItemUnselectedColor =
                a.getColor(R.styleable.ViewPagerIndicator_itemUnselectedColor, mItemUnselectedColor)

            mButtonDrawable = selectorDrawable
            val drawableResId =
                a.getResourceId(R.styleable.ViewPagerIndicator_pagerIndicatorDrawable, 0)
            if (drawableResId != 0) {
                mButtonDrawable = ContextCompat.getDrawable(getContext(), drawableResId)
            }

            a.recycle()
        } catch (e: Exception) {
            Log.e(TAG, getMessageFor(e))
        }
    }

    fun setItemSelectedColor(@ColorInt color: Int) {
        mItemSelectedColor = color
        mButtonDrawable = selectorDrawable
        updateRadioButtonsDrawable()
    }
    private fun updateRadioButtonsDrawable() {
        for (i in 0 until childCount) {
            val button = getChildAt(i)
            if (button is AppCompatRadioButton) {
                button.buttonDrawable = mButtonDrawable?.constantState?.newDrawable()
            }
        }
    }
}