package com.vihmessenger.vihchatbot.adapters

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.base.ThemeAwareAdapter
import com.vihmessenger.vihchatbot.constants.AppConstants
import com.vihmessenger.vihchatbot.data.model.MessageModel
import com.vihmessenger.vihchatbot.data.model.isOtp
import com.vihmessenger.vihchatbot.databinding.ItemChatOtpBinding
import com.vihmessenger.vihchatbot.databinding.ItemChatRvBinding
import com.vihmessenger.vihchatbot.databinding.ItemChatTemplateBinding
import com.vihmessenger.vihchatbot.databinding.ItemDateHeaderBinding
import com.vihmessenger.vihchatbot.databinding.ItemLeftChatProgressBinding
import com.vihmessenger.vihchatbot.databinding.ItemRightChatBinding
import com.vihmessenger.vihchatbot.listener.onItemChatClickListener
import java.util.regex.Pattern

class ChatAdapter(
    var context: Context,
    var listener: onItemChatClickListener,
    var channelName: String,
    var channelLogo: String?,
    private val emptyView: View? = null
) : ThemeAwareAdapter<ViewHolder>() {

    var messageList = mutableListOf<Any>() // Contains MessageModel or DateHeader

    private var activeTemplateViewHolders = mutableSetOf<TemplateMessageViewHolder>()

    private val mediaDownloader by lazy { MediaDownloader(context) }
    private val fileHandler by lazy { FileHandler(context) }

    companion object {
        const val VIEW_TYPE_DATE_HEADER = 1
        // WELCOME_MESSAGE view type is removed
        const val VIEW_TYPE_LEFT_CHAT_PROGRESS = 3
        const val VIEW_TYPE_RIGHT_CHAT = 4
        const val VIEW_TYPE_CHAT_RV = 5
        const val VIEW_TYPE_CHAT_TEMPLATE = 6
        const val VIEW_TYPE_CHAT_OTP = 7
    }

    data class DateHeader(val date: String)

    private fun checkEmptyState() {
        emptyView?.visibility = if (messageList.isEmpty()) View.VISIBLE else View.GONE
    }

    fun cleanupAllAudioPlayers() {
        val iterator = activeTemplateViewHolders.iterator()
        while(iterator.hasNext()) {
            val viewHolder = iterator.next()
            viewHolder.cleanup()
            iterator.remove()
        }
        activeTemplateViewHolders.clear()
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder is TemplateMessageViewHolder) {
            holder.onViewAttached()
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (holder is TemplateMessageViewHolder) {
            holder.onViewDetached()
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = messageList[position]
        return when (item) {
            is DateHeader -> VIEW_TYPE_DATE_HEADER
            is MessageModel -> when (item.sent_by) {
                // WELCOME_MESSAGE case is removed
                AppConstants.LEFT_CHAT_PROGRESS -> VIEW_TYPE_LEFT_CHAT_PROGRESS
                AppConstants.RECEIVER_ID -> VIEW_TYPE_RIGHT_CHAT
                AppConstants.SEND_ID -> VIEW_TYPE_CHAT_RV
                // An OTP (template_type == 1) arrives as a cpaas message; render the dedicated
                // OTP card instead of the generic media/product template.
                AppConstants.CPASS -> if (item.isOtp) VIEW_TYPE_CHAT_OTP else VIEW_TYPE_CHAT_TEMPLATE
                else -> VIEW_TYPE_RIGHT_CHAT
            }
            else -> VIEW_TYPE_RIGHT_CHAT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DATE_HEADER -> {
                val binding = ItemDateHeaderBinding.inflate(inflater, parent, false)
                DateHeaderViewHolder(binding)
            }
            VIEW_TYPE_RIGHT_CHAT -> {
                val binding = ItemRightChatBinding.inflate(inflater, parent, false)
                ReceiverViewHolder(binding)
            }
            VIEW_TYPE_CHAT_RV -> {
                val binding = ItemChatRvBinding.inflate(inflater, parent, false)
                ChatRvViewHolder(binding, context, listener)
            }
            // WELCOME_MESSAGE ViewHolder creation is removed
            VIEW_TYPE_CHAT_TEMPLATE -> {
                val binding = ItemChatTemplateBinding.inflate(inflater, parent, false)
                val audioPlayerManager = AudioPlayerManager(context)
                TemplateMessageViewHolder(
                    binding, context, listener, channelName, channelLogo,
                    mediaDownloader, audioPlayerManager, fileHandler
                ).also { activeTemplateViewHolders.add(it) }
            }
            VIEW_TYPE_CHAT_OTP -> {
                val binding = ItemChatOtpBinding.inflate(inflater, parent, false)
                OtpViewHolder(binding, context)
            }
            VIEW_TYPE_LEFT_CHAT_PROGRESS -> {
                val binding = ItemLeftChatProgressBinding.inflate(inflater, parent, false)
                LeftChatProgressViewHolder(binding)
            }
            else -> {
                val binding = ItemRightChatBinding.inflate(inflater, parent, false)
                ReceiverViewHolder(binding)
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
        super.onThemeChanged(
            primaryColor,
            secondaryColor = secondaryTextColor,
            primaryTextColor = primaryTextColor,
            secondaryTextColor = secondaryTextColor,
            headerColor = headerColor,
            defaultTextColor = defaultTextColor
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = messageList[position]
        val animation = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in)
        holder.itemView.startAnimation(animation)

        when (holder) {
            is DateHeaderViewHolder -> if (item is DateHeader) holder.bind(item)
            is ReceiverViewHolder -> if (item is MessageModel) holder.bind(item)
            is ChatRvViewHolder -> if (item is MessageModel) holder.bind(item)
            // WELCOME_MESSAGE binding is removed
            is LeftChatProgressViewHolder -> if (item is MessageModel) holder.bind(item)
            is TemplateMessageViewHolder -> if (item is MessageModel) holder.bind(item)
            is OtpViewHolder -> if (item is MessageModel) holder.bind(item)
        }
        applyThemeToViewHolder(holder)
    }

    private fun applyThemeToViewHolder(holder: ViewHolder) {
        when (holder) {
            is DateHeaderViewHolder -> {
                holder.binding.tvDateHeader.setTextColor(defaultTextColor)
                val baseColor = primaryColor
                val colorWithAlpha = (0x10 shl 24) or (baseColor and 0x00FFFFFF)
                val backgroundDrawable = holder.binding.tvDateHeader.background
                if (backgroundDrawable is GradientDrawable) {
                    backgroundDrawable.setColor(colorWithAlpha)
                }
            }
            is ReceiverViewHolder -> {
                val bubbleDrawable = holder.binding.clUserChat.background
                if (bubbleDrawable is GradientDrawable) {
                    bubbleDrawable.setColor(primaryColor)
                }
                holder.binding.tvRightChat.setTextColor(primaryTextColor)
                val alpha = (0.6f * 255).toInt()
                val alphaColor = (primaryTextColor and 0x00FFFFFF) or (alpha shl 24)
                holder.binding.tvRightChatTime.setTextColor(alphaColor)
            }
            is ChatRvViewHolder -> {}
            is TemplateMessageViewHolder -> {
                holder.binding.viewPagerIndicator.setItemSelectedColor(primaryColor)
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is TemplateMessageViewHolder) {
            holder.cleanup()
            holder.audioPlayerManager.cleanup()
            activeTemplateViewHolders.remove(holder)
        }
        holder.itemView.clearAnimation()
    }

    override fun getItemCount(): Int {
        val count = messageList.size
        checkEmptyState()
        return count
    }

    fun insertMessage(message: MessageModel) {
        val dateStr = DateTimeUtils.getFormattedDateHeader(message.created_at)
        val needsHeader = shouldAddDateHeader(dateStr)

        val startingPosition = messageList.size
        if (needsHeader) {
            messageList.add(DateHeader(dateStr))
        }
        messageList.add(message)
        notifyItemRangeInserted(startingPosition, messageList.size - startingPosition)
        checkEmptyState()
    }

    fun insertAllMessage(messages: List<MessageModel>) {
        val newList = mutableListOf<Any>()
        if (messages.isEmpty()) {
            val oldSize = messageList.size
            messageList.clear()
            notifyItemRangeRemoved(0, oldSize)
            checkEmptyState()
            return
        }

        val sortedMessages = messages.sortedBy { DateTimeUtils.parseDate(it.created_at).time }

        var currentDateHeader = ""
        for (message in sortedMessages) {
            val dateStr = DateTimeUtils.getFormattedDateHeader(message.created_at)
            if (dateStr != currentDateHeader) {
                newList.add(DateHeader(dateStr))
                currentDateHeader = dateStr
            }
            newList.add(message)
        }

        val diffCallback = MessageDiffCallback(messageList, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        messageList.clear()
        messageList.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
        checkEmptyState()
    }

    fun removeProgressMessage() {
        val index =
            messageList.indexOfFirst { it is MessageModel && it.sent_by == AppConstants.LEFT_CHAT_PROGRESS }
        if (index != -1) {
            messageList.removeAt(index)
            notifyItemRemoved(index)
            checkEmptyState()
        }
    }

    private fun shouldAddDateHeader(newDateStr: String): Boolean {
        if (messageList.isEmpty()) return true
        val lastItem = messageList.last()
        val lastDateStr = when (lastItem) {
            is DateHeader -> lastItem.date
            is MessageModel -> DateTimeUtils.getFormattedDateHeader(lastItem.created_at)
            else -> return true
        }
        return lastDateStr != newDateStr
    }

    class DateHeaderViewHolder(val binding: ItemDateHeaderBinding) :
        ViewHolder(binding.root) {
        fun bind(dateHeader: DateHeader) {
            binding.tvDateHeader.text = dateHeader.date
        }
    }

    class ReceiverViewHolder(val binding: ItemRightChatBinding) : ViewHolder(binding.root) {
        fun bind(message: MessageModel) {
            binding.tvRightChat.text = message.message
            binding.tvRightChatTime.visibility = View.VISIBLE
            binding.tvRightChatTime.text = DateTimeUtils.parseTimestampToTime(message.created_at)
        }
    }

    class ChatRvViewHolder(
        val binding: ItemChatRvBinding,
        val context: Context,
        val listener: onItemChatClickListener
    ) : ViewHolder(binding.root) {
        fun bind(message: MessageModel) {
            binding.tvChatBotAnswer.text = message.message
            binding.tvChatRvTime.text = DateTimeUtils.parseTimestampToTime(message.created_at)
            val buttons = message.interactive?.buttons?.filter { !it.value.isNullOrBlank() }
            val hasButtons = !buttons.isNullOrEmpty()
            // Interactive buttons supersede suggestion chips: the backend mirrors the same
            // labels into suggested_questions for older clients, so showing both duplicates
            // them — and a chip only sends its label as text (it can't open a url / run an
            // action). When buttons exist, render only the buttons.
            setupRecyclerView(if (hasButtons) null else message.suggested_questions)
            setupInteractiveButtons(buttons)
        }
        private fun setupRecyclerView(suggestions: List<String>?) {
            binding.rvChatCard.layoutManager = LinearLayoutManager(context)
            binding.rvChatCard.setHasFixedSize(false)
            val chatRvAdapter = suggestions?.takeIf { it.isNotEmpty() }?.let {
                ChatRvAdapter(context, it, listener)
            }
            binding.rvChatCard.adapter = chatRvAdapter
            binding.rvChatCard.visibility = if (chatRvAdapter != null) View.VISIBLE else View.GONE
        }
        private fun setupInteractiveButtons(buttons: List<com.vihmessenger.vihchatbot.data.model.InteractiveButton>?) {
            val valid = buttons?.filter { !it.value.isNullOrBlank() }
            val adapter = valid?.takeIf { it.isNotEmpty() }?.let {
                binding.rvInteractiveButtons.layoutManager = LinearLayoutManager(context)
                binding.rvInteractiveButtons.setHasFixedSize(false)
                InteractiveButtonAdapter(context, it, listener)
            }
            binding.rvInteractiveButtons.adapter = adapter
            binding.rvInteractiveButtons.visibility = if (adapter != null) View.VISIBLE else View.GONE
        }
    }

    class LeftChatProgressViewHolder(binding: ItemLeftChatProgressBinding) :
        ViewHolder(binding.root) {
        fun bind(message: MessageModel) {}
    }

    /**
     * Renders an OTP message (template_type == 1) as a distinct OTP card: lock badge,
     * "One-time password" label, the code in monospace with a Copy button, and an optional
     * "VIA API/PORTAL" origin badge. OTP text is shown verbatim — never through the template
     * media/link-preview path (OTP_MOBILE_SPEC §3).
     */
    class OtpViewHolder(val binding: ItemChatOtpBinding, val context: Context) :
        ViewHolder(binding.root) {

        private val otpPattern = Pattern.compile("\\b\\d{4,8}\\b")

        fun bind(message: MessageModel) {
            val raw = message.cpaas_json?.msg?.takeIf { it.isNotEmpty() } ?: message.message
            val body = raw.replace("<br />", "\n").replace("<br>", "\n")
            binding.tvOtpBody.text = body
            binding.tvOtpBody.visibility = if (body.isBlank()) View.GONE else View.VISIBLE

            // Extract the first 4–8 digit run as the copyable code; hide the chip if none.
            val matcher = otpPattern.matcher(body)
            val code = if (matcher.find()) matcher.group() else null
            if (code != null) {
                binding.tvOtpCode.text = code
                binding.clOtpCode.visibility = View.VISIBLE
            } else {
                binding.clOtpCode.visibility = View.GONE
            }

            val copyText = code ?: body
            binding.tvOtpCopy.setOnClickListener {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("OTP", copyText))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }

            val src = message.source
            if (!src.isNullOrEmpty()) {
                binding.tvOtpSource.text = "VIA ${src.uppercase()}"
                binding.tvOtpSource.visibility = View.VISIBLE
            } else {
                binding.tvOtpSource.visibility = View.GONE
            }

            binding.tvOtpTime.text = DateTimeUtils.parseTimestampToTime(message.created_at)
        }
    }

    class MessageDiffCallback(
        private val oldList: List<Any>,
        private val newList: List<Any>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return when {
                oldItem is DateHeader && newItem is DateHeader -> oldItem.date == newItem.date
                oldItem is MessageModel && newItem is MessageModel -> oldItem.created_at == newItem.created_at && oldItem.message == newItem.message
                else -> false
            }
        }
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}