package com.vihmessenger.vihchatbot.ui.bottomsheet

import com.vihmessenger.vihchatbot.utils.CustomImageLoader
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.adapters.ChatItemBottomSheetListAdapter
import com.vihmessenger.vihchatbot.data.model.ChatListModel
import com.vihmessenger.vihchatbot.data.model.ListItem
import com.vihmessenger.vihchatbot.databinding.ChatItemBottomSheetLayoutBinding
import com.vihmessenger.vihchatbot.utils.BaseBottomSheetDialog

class ChatItemBottomSheetFragment : BaseBottomSheetDialog() {

    private var _binding: ChatItemBottomSheetLayoutBinding? = null
    private val binding get() = _binding!!

    private lateinit var listAdapter: ChatItemBottomSheetListAdapter
    private var chatModel: ChatListModel? = null

    // Host-provided actions. The sheet is transient, so it only reports the choice; the host
    // (ChatListFragment) owns a longer-lived ViewModel that performs the mutation and refreshes.
    var onMuteSelected: ((ChatListModel) -> Unit)? = null
    var onBlockSelected: ((ChatListModel) -> Unit)? = null

    // Built from [chatModel] state so Mute/Block flip to Unmute/Unblock when already applied.
    private var listItems: List<ListItem> = emptyList()

    private fun buildListItems(): List<ListItem> {
        val muted = chatModel?.enterprise?.is_muted_by_user == true
        val blocked = chatModel?.enterprise?.is_blacklisted_by_user == true
        return listOf(
            ListItem(id = 1, title = "Delete", iconResId = R.drawable.ic_delete),
            ListItem(id = 2, title = "Pin", iconResId = R.drawable.ic_pin),
            ListItem(id = 3, title = if (muted) "Unmute" else "Mute", iconResId = R.drawable.ic_mute),
            ListItem(id = 4, title = if (blocked) "Unblock" else "Block", iconResId = R.drawable.ic_block)
        )
    }

    override fun getHeightPercentage(): Float = 0.45f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatModel = arguments?.getSerializable(ARG_CHAT) as? ChatListModel
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ChatItemBottomSheetLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun getLayoutResId(): Int {
        return R.layout.chat_item_bottom_sheet_layout
    }

    override fun setupViews(view: View) {
        listItems = buildListItems()
        listAdapter = ChatItemBottomSheetListAdapter(requireContext(), listItems)
        binding.listView.adapter = listAdapter

        chatModel?.let {
            binding.tvChatTitle.text = it.enterprise.displayNameModel?.display_name
            val logoUrl = it.enterprise.display_img.takeIf { img -> !img.isNullOrBlank() }
                ?: it.enterprise.profile_picture

            CustomImageLoader.loadImageView(
                imageView = binding.ivProfileImage,
                url = logoUrl,
                name = it.enterprise.displayNameModel?.display_name ?: "NA",
                onError = {
                    binding.ivProfileImage.visibility = View.INVISIBLE
                }
            )
        }

        binding.listView.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                val selectedItem = listItems[position]
                onItemSelected(selectedItem)
            }
    }

    private fun onItemSelected(item: ListItem) {
        val chat = chatModel
        when (item.id) {
            1 -> { /* Delete — not yet implemented */
                Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show()
            }

            2 -> { /* Pin — not yet implemented */
                Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show()
            }

            3 -> chat?.let { onMuteSelected?.invoke(it) }   // Mute / Unmute

            4 -> chat?.let { onBlockSelected?.invoke(it) }  // Block / Unblock
        }
        dismiss()
    }
//
//    companion object {
//        fun newInstance(chat: ChatListModel): ChatItemBottomSheetFragment {
//            return ChatItemBottomSheetFragment()
//        }
//    }

    companion object {
        private const val ARG_CHAT = "arg_chat"

        fun newInstance(chat: ChatListModel): ChatItemBottomSheetFragment {
            val fragment = ChatItemBottomSheetFragment()
            val args = Bundle()
            args.putSerializable(ARG_CHAT, chat)
            fragment.arguments = args
            return fragment
        }
    }
}
// From a Fragment
/*
fun showBottomSheet() {
    val bottomSheet = ListBottomSheet.newInstance()
    bottomSheet.show(childFragmentManager, BaseBottomSheetDialog.TAG)
}

// From an Activity
fun showBottomSheet() {
    val bottomSheet = ListBottomSheet.newInstance()
    bottomSheet.show(supportFragmentManager, BaseBottomSheetDialog.TAG)
}
*/