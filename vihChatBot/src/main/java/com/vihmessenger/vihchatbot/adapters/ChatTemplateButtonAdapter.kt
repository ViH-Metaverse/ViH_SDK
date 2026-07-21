package com.vihmessenger.vihchatbot.adapters

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.base.ThemeAwareAdapter
import com.vihmessenger.vihchatbot.data.model.ButtonModel
import com.vihmessenger.vihchatbot.databinding.ItemChatTemplateButtonBinding
import com.vihmessenger.vihchatbot.listener.onItemChatClickListener
import com.vihmessenger.vihchatbot.utils.createEmojiDrawable
import com.vihmessenger.vihchatbot.utils.extensions.setColorFilterCompatSRCIN
import com.vihmessenger.vihchatbot.utils.extensions.setSecondaryColorFilterCompat

class ChatTemplateButtonAdapter(
    var context: Context, var listener: onItemChatClickListener
) : ThemeAwareAdapter<ChatTemplateButtonAdapter.ChatTemplateButtonViewHolder>() {

    var buttonModelList = mutableListOf<ButtonModel>()

   inner class ChatTemplateButtonViewHolder(
        private val binding: ItemChatTemplateButtonBinding, var listener: onItemChatClickListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(get: ButtonModel, position: Int) {
            binding.apply {
                when (get.btn_typ) {
                    "call" -> {
                        ivTemplateButton.setImageResource(R.drawable.ic_call)
                        tvTemplateButtonName.text = get.btn_txt?.toString() ?: "-"
                    }
                    "email" -> {
                        ivTemplateButton.setImageResource(R.drawable.ic_email_background)
                        tvTemplateButtonName.text = get.btn_txt?.toString() ?: "-"
                    }
                    "web" -> {
                        ivTemplateButton.setImageResource(R.drawable.ic_link_background)
                        tvTemplateButtonName.text = get.btn_txt?.toString() ?: "-"
                    }

                    else -> {
                        val emojiType = get.btn_typ ?: "🙂"
                        val emojiDrawable = createEmojiDrawable(binding.root.context, emojiType, 30f)

                        ivTemplateButton.setImageDrawable(emojiDrawable)
                        tvTemplateButtonName.text = get.btn_txt ?: ""
                    }
                }

                clCallBtn.setOnClickListener {
                    listener.onChatButtonClick(position, get)
                }

                val colorWithAlpha = (0x10 shl 24) or (secondaryColor and 0x00FFFFFF)
                val backgroundDrawable = clCallBtn.background
                if (backgroundDrawable is GradientDrawable) {
                    backgroundDrawable.setColor(colorWithAlpha)
                }

            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): ChatTemplateButtonViewHolder {
        val binding = ItemChatTemplateButtonBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChatTemplateButtonViewHolder(binding, listener)
    }

    override fun onBindViewHolder(
        holder: ChatTemplateButtonViewHolder, position: Int
    ) {
        holder.bind(buttonModelList[position], position)
    }

    override fun getItemCount(): Int {
        return buttonModelList.size
    }

    fun insertButtonList(message: List<ButtonModel>) {
        this.buttonModelList.clear()
        this.buttonModelList.addAll(message)
        notifyItemInserted(buttonModelList.size)
    }
}