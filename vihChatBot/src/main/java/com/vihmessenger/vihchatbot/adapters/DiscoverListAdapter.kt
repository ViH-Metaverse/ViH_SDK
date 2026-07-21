package com.vihmessenger.vihchatbot.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.base.ThemeAwareAdapter
import com.vihmessenger.vihchatbot.data.model.EnterPriseModel
import com.vihmessenger.vihchatbot.databinding.ItemDiscoverBinding
import com.vihmessenger.vihchatbot.utils.CustomImageLoader

class DiscoverListAdapter(private val context: Context) :
    ThemeAwareAdapter<DiscoverListAdapter.DiscoverListViewHolder>() {

    private val enterpriseDiscoverList = mutableListOf<EnterPriseModel>()
    private var onDiscoverRvItemClickListener: com.vihmessenger.vihchatbot.listener.OnDiscoverRvItemClickListener? =
        null

    // Interface for click listener (assuming you have this)
    interface OnDiscoverRvItemClickListener {
        fun onStartChatClick(position: Int, item: EnterPriseModel)
        // Add other click actions if needed
    }

    fun setOnDiscoverRvItemClickListener(listener: com.vihmessenger.vihchatbot.listener.OnDiscoverRvItemClickListener) {
        this.onDiscoverRvItemClickListener = listener
    }

    // *** Make sure DiscoverListViewHolder is an inner class ***
    // It needs access to the adapter's color fields
    inner class DiscoverListViewHolder(private val binding: ItemDiscoverBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EnterPriseModel, position: Int) {
            with(binding) {
                // The backend may carry the name/logo in different fields; resolvedDisplayName
                // and resolvedLogoUrl centralise that fallback so the list, the chat toolbar and
                // the launch intent all agree.
                tvDiscoverTitle.text = item.resolvedDisplayName
                tvDiscoverCompanyType.text = item.industry

                tvDiscoverTitle.setTextColor(defaultTextColor) // Or maybe defaultTextColor? Choose what fits your design.
                tvDiscoverCompanyType.setTextColor(defaultTextColor) // Or maybe defaultTextColor? Choose what fits your design.

                val alpha = (255 * 0.10f).toInt()
                val backgroundColor = (primaryColor and 0x00FFFFFF) or (alpha shl 24)

                val backgroundTintList = ColorStateList.valueOf(backgroundColor)
                ViewCompat.setBackgroundTintList(chatButton, backgroundTintList)

                ivChatIcon.setColorFilter(primaryColor, PorterDuff.Mode.SRC_IN)

                tvChatLabel.setTextColor(primaryColor)


                // Prefer a real logo; fall back through the other image fields the API uses.
                val imageUrl = item.resolvedLogoUrl

                if (!imageUrl.isNullOrBlank()) {
                    CustomImageLoader.loadImageView(
                        imageView = ivDiscoverImage,
                        url = imageUrl,
                        onError = {
                            ivDiscoverImage.setImageResource(R.drawable.placeholder)
                        }
                    )
                } else {
                    ivDiscoverImage.setImageResource(R.drawable.placeholder)
                }

                // Click listener
                chatButton.setOnClickListener {
                    onDiscoverRvItemClickListener?.onStartChatClick(
                        absoluteAdapterPosition,
                        item
                    ) // Use absoluteAdapterPosition for safety
                }

                // You might also want to set click listener for the whole item view
                root.setOnClickListener {
                    // Handle item click if needed
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiscoverListViewHolder {
        val binding =
            ItemDiscoverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DiscoverListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DiscoverListViewHolder, position: Int) {
        // This will call the updated bind method which now sets the text colors
        holder.bind(enterpriseDiscoverList[position], position)
    }

    fun addDiscoverList(newItems: List<EnterPriseModel>) {
        val startPosition = enterpriseDiscoverList.size
        enterpriseDiscoverList.addAll(newItems)
        notifyItemRangeInserted(startPosition, newItems.size)
    }

    fun clearDiscoverList() {
        val oldSize = enterpriseDiscoverList.size
        enterpriseDiscoverList.clear()
        notifyItemRangeRemoved(0, oldSize) // More efficient than notifyDataSetChanged for clearing
    }

    override fun getItemCount(): Int = enterpriseDiscoverList.size
}
