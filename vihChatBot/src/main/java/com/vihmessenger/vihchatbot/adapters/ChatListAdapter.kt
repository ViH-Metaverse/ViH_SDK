package com.vihmessenger.vihchatbot.adapters

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.vihmessenger.vihchatbot.base.ThemeAwareAdapter
import com.vihmessenger.vihchatbot.data.model.ChatListModel
import com.vihmessenger.vihchatbot.databinding.ItemChatListBinding
import com.vihmessenger.vihchatbot.utils.CustomImageLoader
import com.vihmessenger.vihchatbot.utils.parseTimestamp

class ChatListAdapter(
    private val context: Context,
    private val onItemClick: (ChatListModel) -> Unit,
    private val onItemLongClick: (ChatListModel) -> Unit,
    private val onFilterComplete: (isListEmpty: Boolean) -> Unit
) :
    ThemeAwareAdapter<ChatListAdapter.ChatListViewHolder>() {

    val originalChatList = mutableListOf<ChatListModel>()
    val chatList = mutableListOf<ChatListModel>()

    fun updateChatList(newChatList: List<ChatListModel>, isFiltering: Boolean = false) {
        if (!isFiltering) {
            originalChatList.clear()
            originalChatList.addAll(newChatList)
        }

        val diffCallback = ChatDiffCallback(chatList, newChatList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        chatList.clear()
        chatList.addAll(newChatList)

        diffResult.dispatchUpdatesTo(this)
    }

    fun filterChatList(query: String) {
        val filteredList = if (query.isEmpty()) {
            originalChatList
        } else {
            originalChatList.filter { chat ->
                chat.enterprise.comp_name.contains(query, ignoreCase = true) ||
                        chat.last_message.message.contains(query, ignoreCase = true)
            }
        }
        // Notify the fragment if the search result is empty (and the query is not)
        onFilterComplete(filteredList.isEmpty() && query.isNotEmpty())

        updateChatList(filteredList, isFiltering = true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatListViewHolder {
        val binding =
            ItemChatListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatListViewHolder, position: Int) {
        holder.bind(chatList[position])
    }

    override fun getItemCount(): Int = chatList.size

    inner class ChatListViewHolder(private val binding: ItemChatListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chatItem: ChatListModel) {
            with(binding) {
                tvChatTitle.text = chatItem.enterprise.displayNameModel?.display_name
                val background = GradientDrawable()
                background.shape = GradientDrawable.OVAL
                background.setColor(secondaryColor)
                background.setSize(100, 100)

                txtCountUnread.background = background

                binding.root.setOnClickListener {
                    onItemClick(chatItem)
                }

                binding.root.setOnLongClickListener {
                    onItemLongClick(chatItem)
                    true
                }
                val companyLogo = if (chatItem.enterprise.display_img.isNullOrBlank()) {
                    chatItem.enterprise.profile_picture
                } else {
                    chatItem.enterprise.display_img
                }
                CustomImageLoader.loadImageView(
                    imageView = ivProfileImage,
                    url = companyLogo,
                    name = chatItem.enterprise.displayNameModel?.display_name ?: "NA",
                    onError = {
                        ivProfileImage.visibility = View.INVISIBLE
                    }
                )
                setLimitedText(
                    binding.tvChatSubTitle, chatItem.last_message.cpaas_json?.templ_name
                        ?: chatItem.last_message.message
                )

                tvLastChatDate.text = parseTimestamp(chatItem.last_message.created_at)

                if (chatItem.unseen_count > 0) {
                    txtCountUnread.apply {
                        visibility = View.VISIBLE
                        text = chatItem.unseen_count.toString()
                    }
                } else {
                    txtCountUnread.visibility = View.GONE
                }
            }
        }
    }

    fun setLimitedText(textView: TextView, text: String, maxLength: Int = 35) {
        if (text.length > maxLength) {
            val limitedText = text.substring(0, maxLength) + "..."
            textView.text = limitedText
        } else {
            textView.text = text
        }
    }
}

/**
 * DiffUtil.Callback to efficiently update the RecyclerView.
 * This class definition resolves the "Unresolved reference" error.
 */
class ChatDiffCallback(
    private val oldList: List<ChatListModel>,
    private val newList: List<ChatListModel>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].session_id == newList[newItemPosition].session_id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // For this to work correctly, ChatListModel should be a data class
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}