package com.vihmessenger.vihchatbot.ui.fragments

import BaseActivity
import BaseFragment
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.adapters.ChatListAdapter
import com.vihmessenger.vihchatbot.constants.AppConstants
import com.vihmessenger.vihchatbot.data.model.ChatListModel
import com.vihmessenger.vihchatbot.databinding.FragmentChatListBinding
import com.vihmessenger.vihchatbot.ui.activity.home.ChatActivity
import com.vihmessenger.vihchatbot.ui.bottomsheet.ChatItemBottomSheetFragment
import com.vihmessenger.vihchatbot.utils.BaseBottomSheetDialog
import com.vihmessenger.vihchatbot.utils.extensions.parseDateToTimestamp
import com.vihmessenger.vihchatbot.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatListFragment : BaseFragment() {

    companion object {
        private val TAG = ChatListFragment::class.java.simpleName

        fun getInstance(hashCode: String) = ChatListFragment().apply {
            arguments = Bundle().apply {
                putString(AppConstants.HASHCODE_EXTRA, hashCode)
            }
        }
    }

    private var _viewBinder: FragmentChatListBinding? = null
    private val viewBinder get() = _viewBinder!!

    private lateinit var homeViewModel: HomeViewModel

    private val chatListAdapter by lazy {
        ChatListAdapter(
            requireContext(),
            ::onItemClick,
            ::onItemLongClick,
            ::handleFilterResults // Callback to handle search results
        )
    }

    private val chatUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.vihmessenger.vihchatbot.FCM_MESSAGE") {
                getChatList()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        getChatList()
        val filter = IntentFilter("com.vihmessenger.vihchatbot.FCM_MESSAGE")
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(chatUpdateReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(chatUpdateReceiver)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _viewBinder = FragmentChatListBinding.inflate(inflater, container, false)
        return viewBinder.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _viewBinder = null
    }

    override fun initViewModels() {
        homeViewModel = getViewModel(
            fragment = activity as BaseActivity,
            viewModel = HomeViewModel(activity as BaseActivity),
            className = HomeViewModel::class.java
        )
    }

    override fun onViewClick(view: View?) {}

    override fun initView(view: View) {
        // Ensure the search-specific layout is hidden initially
        viewBinder.lyBottomNavItem.comingSoon.root.visibility = View.GONE
        setRecyclerView()
        setupSwipeRefresh()
    }

    private fun setRecyclerView() {
        viewBinder.lyBottomNavItem.rvChatList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatListAdapter
        }
    }

    private fun setupSwipeRefresh() {
        viewBinder.lyBottomNavItem.srlChatList.apply {
            setOnRefreshListener { getChatList() }
            setColorSchemeColors(Color.parseColor("#0049E6"), Color.parseColor("#9C15F7"))
            setDistanceToTriggerSync(900)
        }
    }

    private fun getChatList() {
        val hashCode = arguments?.getString(AppConstants.HASHCODE_EXTRA) ?: return
        homeViewModel.getChattingListResponse(false, hashCode)
    }

    override fun setObservers() {
        homeViewModel.chatListLiveData.observe(viewLifecycleOwner) { res ->
            viewBinder.lyBottomNavItem.srlChatList.isRefreshing = false // Stop refreshing indicator

            // Always hide the search-specific layout when new data arrives
            viewBinder.lyBottomNavItem.comingSoon.root.visibility = View.GONE

            val isChatListEmpty = res?.data.isNullOrEmpty()
            Log.d(TAG, "ChatListLiveData: Is empty? $isChatListEmpty. Data: ${res?.data}")

            viewBinder.lyBottomNavItem.apply {
                if (isChatListEmpty) {
                    linear.visibility = View.VISIBLE     // Show initial empty state
                    rvChatList.visibility = View.GONE      // Hide RecyclerView
                } else {
                    linear.visibility = View.GONE        // Hide initial empty state
                    rvChatList.visibility = View.VISIBLE   // Show RecyclerView
                    res?.data?.let { chatData ->
                        chatListAdapter.updateChatList(chatData.sortedByDescending {
                            parseDateToTimestamp(it.last_message.created_at)
                        })
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            isInternetAvailable.collectLatest { available ->
                with(viewBinder) {
                    noInternet.root.visibility = if (available) View.GONE else View.VISIBLE
                    lyBottomNavItem.root.visibility = if (available) View.VISIBLE else View.GONE
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
        _viewBinder?.let { vb ->
            vb.lyBottomNavItem.srlChatList.setColorSchemeColors(primaryColor, secondaryColor)

            val tvStartChatBackground = vb.lyBottomNavItem.tvStartChat.background.mutate()
            if (tvStartChatBackground is GradientDrawable) {
                tvStartChatBackground.setColor(primaryColor)
            }

            vb.lyBottomNavItem.tvNoMessages.setTextColor(defaultTextColor)
            vb.lyBottomNavItem.tvDescription.setTextColor(defaultTextColor)

            val layerDrawable = vb.lyBottomNavItem.ivMessageIcon.drawable as? LayerDrawable
            val mutatedLayerDrawable = layerDrawable?.mutate() as? LayerDrawable

            mutatedLayerDrawable?.let {
                val linesDrawable = it.findDrawableByLayerId(R.id.themed_lines)
                linesDrawable?.setColorFilter(primaryColor, android.graphics.PorterDuff.Mode.SRC_IN)
            }
        }
    }

    override fun setListeners() {
        viewBinder.lyBottomNavItem.tvStartChat.setOnClickListener {
            homeViewModel.fragmentTransitionLiveData.postValue("")
        }
    }

    private fun onItemClick(chat: ChatListModel) {
        Log.e(TAG, "onItemClick: ${chat.enterprise}")
        ChatActivity.startIntent(
            requireContext(),
            chat.session_id,
            channel_name = chat.enterprise.displayNameModel?.display_name,
            channel_image = chat.enterprise.display_img,
            chat.enterprise,
            id = chat.enterprise.user_id,
            hashcode = arguments?.getString(AppConstants.HASHCODE_EXTRA)
        )
    }

    private fun onItemLongClick(chat: ChatListModel) {
        val bottomSheet = ChatItemBottomSheetFragment.newInstance(chat)
        bottomSheet.show(childFragmentManager, BaseBottomSheetDialog.TAG)
    }

    fun receiveData(data: String) {
        chatListAdapter.filterChatList(data)
    }

    /**
     * Handles the UI visibility when a search filter is applied.
     * This is triggered by the callback from the ChatListAdapter.
     */
    private fun handleFilterResults(isFilterEmpty: Boolean) {
        viewBinder.lyBottomNavItem.apply {
            comingSoon.txtComingSoon.setText("No Results")

            // During an active search we never show the initial "No Messages Yet" empty
            // state (linear) — that would overlap the results / the "No Results" view.
            linear.visibility = View.GONE

            // Show the "no search results" layout only when the filter has no matches,
            // otherwise show the RecyclerView with the filtered results.
            comingSoon.root.visibility = if (isFilterEmpty) View.VISIBLE else View.GONE
            rvChatList.visibility = if (isFilterEmpty) View.GONE else View.VISIBLE
        }
    }
}