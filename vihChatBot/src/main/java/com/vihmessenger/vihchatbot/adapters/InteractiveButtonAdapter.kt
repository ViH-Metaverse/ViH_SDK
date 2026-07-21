package com.vihmessenger.vihchatbot.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vihmessenger.vihchatbot.data.model.InteractiveButton
import com.vihmessenger.vihchatbot.databinding.ItemInteractiveButtonBinding
import com.vihmessenger.vihchatbot.listener.onItemChatClickListener

/**
 * Renders the GLM flow's structured interactive buttons under a bot message
 * (see docs `MESSAGE_FLOW_GLM.md` §4). Taps are routed to
 * [onItemChatClickListener.onInteractiveButtonClick], which handles the
 * quick_reply / url / action behaviours.
 */
class InteractiveButtonAdapter(
    private val context: Context,
    private val buttons: List<InteractiveButton>,
    private val listener: onItemChatClickListener
) : RecyclerView.Adapter<InteractiveButtonAdapter.ButtonViewHolder>() {

    inner class ButtonViewHolder(
        private val binding: ItemInteractiveButtonBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(button: InteractiveButton) {
            // Fall back to the value if a label is missing so the button is never blank.
            binding.tvInteractiveButton.text = button.label?.takeIf { it.isNotBlank() }
                ?: button.value.orEmpty()
            binding.clInteractiveButton.setOnClickListener {
                listener.onInteractiveButtonClick(button)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
        val binding = ItemInteractiveButtonBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ButtonViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ButtonViewHolder, position: Int) {
        holder.bind(buttons[position])
    }

    override fun getItemCount(): Int = buttons.size
}
