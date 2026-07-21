package com.vihmessenger.vihchatbot.ui.bottomsheet

import BaseActivity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.vihmessenger.vihchatbot.adapters.IndustryAdapter
import com.vihmessenger.vihchatbot.databinding.IndustryFiltersBottomSheetDialogBinding
import com.vihmessenger.vihchatbot.utils.BaseBottomSheetDialog
import com.vihmessenger.vihchatbot.utils.DynamicThemeManager
import com.vihmessenger.vihchatbot.viewmodel.HomeViewModel

class IndustryFiltersBottomSheetFragment : BaseBottomSheetDialog(),
    DynamicThemeManager.ThemeChangeListener {

    private var _binding: IndustryFiltersBottomSheetDialogBinding? = null
    private val binding get() = _binding!!

    private lateinit var industryAdapter: IndustryAdapter
    private var selectedIndustries: String? = null
    private var selectionListener: OnIndustrySelectionListener? = null

    private lateinit var homeViewModel: HomeViewModel
    private var isFilterClear: Boolean = false


    override fun getHeightPercentage(): Float = 0.6f

    interface OnIndustrySelectionListener {
        fun onIndustriesSelected(industries: String)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = IndustryFiltersBottomSheetDialogBinding.inflate(inflater, container, false)
        homeViewModel = getFragmentViewModel(
            this, HomeViewModel(requireActivity() as BaseActivity), HomeViewModel::class.java
        )
        homeViewModel.getIndustriesListResponse(false)
        return binding.root
    }

    override fun setupViews(view: View) {
        DynamicThemeManager.registerListener(this)

        selectedIndustries = arguments?.getString(ARG_SELECTED_INDUSTRIES)
        isFilterClear = arguments?.getBoolean(IS_FILTER_CLEAR) ?: false

        homeViewModel.industryListLiveData.observe(viewLifecycleOwner) { response ->
            if (response.status && response.data.isNotEmpty()) {
                setupIndustryList(response.data)
                if (isFilterClear && ::industryAdapter.isInitialized) {
                    industryAdapter.clearSelections()
                }
            }
        }

        binding.buttonApply.setOnClickListener {
            if (::industryAdapter.isInitialized) {
                val selected = industryAdapter.getSelectedIndustries()
                selectionListener?.onIndustriesSelected(selected)
            }
            dismiss()
        }

        binding.buttonClear.setOnClickListener {
            if (::industryAdapter.isInitialized) {
                industryAdapter.clearSelections()
                selectionListener?.onIndustriesSelected("")
            }
        }
    }

    private fun setupIndustryList(industries: List<String>) {
        val selectedItems =
            selectedIndustries?.split(", ")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

        val industryItems = industries.map {
            IndustryAdapter.Industry(it, it in selectedItems)
        }

        // Get initial theme colors from DynamicThemeManager
        val initialPrimaryColor = DynamicThemeManager.getPrimaryColor()
        val initialDefaultTextColor = DynamicThemeManager.getDefaultTextColor()


        industryAdapter = IndustryAdapter(
            requireContext(), industryItems, initialDefaultTextColor = initialDefaultTextColor,
            initialPrimaryColor = initialPrimaryColor,
        )

        binding.listViewIndustries.adapter = industryAdapter
        applyThemeToAdapter()
    }

    fun setOnIndustrySelectionListener(listener: OnIndustrySelectionListener) {
        this.selectionListener = listener
    }

    companion object {
        private const val ARG_SELECTED_INDUSTRIES = "arg_selected_industries"
        private const val IS_FILTER_CLEAR = "IS_FILTER_CLEAR"

        fun newInstance(
            selectedIndustries: String? = null,
            isFilterClear: Boolean
        ): IndustryFiltersBottomSheetFragment {
            return IndustryFiltersBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SELECTED_INDUSTRIES, selectedIndustries)
                    putBoolean(IS_FILTER_CLEAR, isFilterClear)
                }
            }
        }
    }

    override fun onThemeChanged(
        primaryColor: Int,
        secondaryColor: Int,
        primaryTextColor: Int,
        secondaryTextColor: Int,
        headerColor: Int,
        defaultTextColor: Int
    ) {
        if (_binding == null) return
        binding.buttonApply.setTextColor(primaryTextColor)
        binding.buttonClear.setTextColor(primaryColor)
        val baseColor = primaryColor
        val colorWithAlpha = (0x10 shl 24) or (baseColor and 0x00FFFFFF)
        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 20f, binding.buttonClear.resources.displayMetrics
            ) // same as 20dp
            setColor(colorWithAlpha)
        }
        binding.buttonClear.background = backgroundDrawable

        val dynamicBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 24f, binding.buttonApply.resources.displayMetrics
            )
            setColor(primaryColor)
        }
        binding.buttonApply.background = dynamicBackground

        applyThemeToAdapter()
    }
    private fun applyThemeToAdapter() {
        if (::industryAdapter.isInitialized) {
            // How you update the adapter depends on its implementation.
            // Option 1: Adapter also listens to DynamicThemeManager (less common for adapters)
            // Option 2: Pass colors to a method in the adapter
            // industryAdapter.updateThemeColors(DynamicThemeManager.getDefaultTextColor(), DynamicThemeManager.getPrimaryColor())

            // Option 3: If adapter reads theme dynamically in getView/onBindViewHolder, just notify it
            industryAdapter.notifyDataSetChanged() // This forces redraw, adapter might pick up new colors then

            // Choose the appropriate method based on your IndustryAdapter design.
            // For simplicity, notifyDataSetChanged() is often sufficient if the
            // adapter's item view inflation/binding logic correctly uses themed attributes
            // or dynamically retrieves colors from DynamicThemeManager or context theme.
        }
    }

    override fun onDestroyView() {
        // Unregister the listener to prevent memory leaks
        DynamicThemeManager.unregisterListener(this)
        super.onDestroyView()
        _binding = null // Important for view binding
    }
}