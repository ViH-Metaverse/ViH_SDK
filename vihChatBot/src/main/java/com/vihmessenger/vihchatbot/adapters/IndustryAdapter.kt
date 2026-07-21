package com.vihmessenger.vihchatbot.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import com.vihmessenger.vihchatbot.R

class IndustryAdapter(
    private val context: Context,
    private val industries: List<Industry>,
    initialDefaultTextColor: Int,
    initialPrimaryColor: Int
) : BaseAdapter() {

    data class Industry(val name: String, var isSelected: Boolean = false)

    private val selectedIndustries = mutableSetOf<String>()

    // Store current theme colors
    private var currentDefaultTextColor: Int = initialDefaultTextColor
    private var currentPrimaryColor: Int = initialPrimaryColor

    init {
        industries.forEach { industry ->
            if (industry.isSelected) {
                selectedIndustries.add(industry.name)
            }
        }
    }

    override fun getCount(): Int = industries.size

    override fun getItem(position: Int): Industry = industries[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_industry, parent, false)

        val industry = getItem(position)
        val checkBox = view.findViewById<CheckBox>(R.id.checkbox_industry)

        checkBox.text = industry.name
        checkBox.setOnCheckedChangeListener(null)
        checkBox.isChecked = industry.isSelected
        val colorChecked = currentPrimaryColor
        val colorUnchecked = currentDefaultTextColor

        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )

        val colors = intArrayOf(
            colorChecked,   // Checked state color
            colorUnchecked  // Unchecked state color
        )

// Apply the tint
        checkBox.buttonTintList = ColorStateList(states, colors)

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            industry.isSelected = isChecked
            if (isChecked) {
                selectedIndustries.add(industry.name)
            } else {
                selectedIndustries.remove(industry.name)
            }
        }

        return view
    }

    fun getSelectedIndustries(): String {
        return selectedIndustries.joinToString(", ")
    }

    fun clearSelections() {
        industries.forEach { it.isSelected = false }
        selectedIndustries.clear()
        notifyDataSetChanged()
    }
}