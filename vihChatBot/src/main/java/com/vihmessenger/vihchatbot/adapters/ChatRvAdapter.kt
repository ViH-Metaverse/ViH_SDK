package com.vihmessenger.vihchatbot.adapters


import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vihmessenger.vihchatbot.base.ThemeAwareAdapter
import com.vihmessenger.vihchatbot.databinding.ItemChatRvCardBinding
import com.vihmessenger.vihchatbot.listener.onItemChatClickListener

class ChatRvAdapter(
    var context: Context,
    var list: List<String>,
    var listener: onItemChatClickListener
) :
    ThemeAwareAdapter<ChatRvAdapter.ChatRvViewHolder>() {

    inner class ChatRvViewHolder(
        private val binding: ItemChatRvCardBinding, var listener: onItemChatClickListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(get: String) {
            binding.apply {
                tvCharRvCard.text = get
                clChatRvCard.setOnClickListener {
                    listener.onItemClick(get, "")
                }
                val colorWithAlpha = (0x10 shl 24) or (secondaryColor and 0x00FFFFFF)
                val backgroundDrawable = clChatRvCard.background
                if (backgroundDrawable is GradientDrawable) {
                    backgroundDrawable.setColor(colorWithAlpha)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatRvViewHolder {
        val binding =
            ItemChatRvCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatRvViewHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: ChatRvViewHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int {
        return list.size
    }
}