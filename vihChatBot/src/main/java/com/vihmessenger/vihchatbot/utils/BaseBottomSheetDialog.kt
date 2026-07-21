package com.vihmessenger.vihchatbot.utils

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.data.services.BaseViewModelFactory

/**
 * Base BottomSheetDialog that can be extended and used anywhere.
 * Features rounded top corners and a centered view indicator.
 * Supports view binding implementation.
 */
abstract class BaseBottomSheetDialog : BottomSheetDialogFragment() {

    // For backward compatibility - subclasses can override either this
    // or onCreateView if using view binding
    open fun getLayoutResId(): Int = -1

    // Default implementation that can be overridden by view binding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layoutId = getLayoutResId()
        return if (layoutId != -1) {
            inflater.inflate(layoutId, container, false)
        } else {
            // If using view binding, this will be overridden by subclass
            super.onCreateView(inflater, container, savedInstanceState)
        }
    }

    protected fun <T : ViewModel> getFragmentViewModel(
        fragment: Fragment, viewModel: ViewModel, className: Class<T>
    ): T {
        return ViewModelProvider(
            fragment, BaseViewModelFactory(viewModel, className)
        ).get(className)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): BottomSheetDialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet =
                bottomSheetDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)

            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED

                // Prevent dismissing when sliding down
                behavior.isDraggable = false

                // Apply the background with rounded corners
                it.setBackgroundResource(R.drawable.bg_bottom_sheet)

                // Set max height
                setBottomSheetHeight(it)
            }
        }

        return dialog
    }

    private fun setBottomSheetHeight(bottomSheet: View) {
        val windowManager =
            requireActivity().getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenHeight: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            screenHeight = bounds.height()
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            screenHeight = displayMetrics.heightPixels
        }

        // Set a max height of 50% of screen height (or custom percentage)
        val layoutParams = bottomSheet.layoutParams
        layoutParams.height = (screenHeight * getHeightPercentage()).toInt()
        bottomSheet.layoutParams = layoutParams
    }

    /**
     * Setup views and listeners
     */
    abstract fun setupViews(view: View)

    // Add ability to customize height percentage in subclasses
    open fun getHeightPercentage(): Float = 0.5f

    companion object {
        const val TAG = "BaseBottomSheetDialog"
    }
}