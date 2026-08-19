package com.vihmessenger.vihchatbot.ui.activity.home

import BaseActivity
import com.vihmessenger.vihchatbot.utils.CustomImageLoader
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import com.vihmessenger.vihchatbot.utils.VihLog
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import android.content.res.ColorStateList
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.ImageViewCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.vanniktech.emoji.EmojiPopup
import com.vihmessenger.vihchatbot.AppController
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.adapters.ChatAdapter
import com.vihmessenger.vihchatbot.constants.AppConstants
import com.vihmessenger.vihchatbot.data.model.ButtonModel
import com.vihmessenger.vihchatbot.data.model.EnterPriseModel
import com.vihmessenger.vihchatbot.data.model.MessageModel
import com.vihmessenger.vihchatbot.databinding.ActivityChatBinding
import com.vihmessenger.vihchatbot.listener.onItemChatClickListener
import com.vihmessenger.vihchatbot.ui.activity.CompanyProfileActivity
import com.vihmessenger.vihchatbot.ui.activity.VideoActivity
import com.vihmessenger.vihchatbot.utils.currentDateTime
import com.vihmessenger.vihchatbot.utils.extensions.setSolidColorFilterCompat
import com.vihmessenger.vihchatbot.data.model.VoiceBot
import com.vihmessenger.vihchatbot.utils.getProfileData
import com.vihmessenger.vihchatbot.utils.getVihSettings
import com.vihmessenger.vihchatbot.utils.sharedPreference.Prefs
import com.vihmessenger.vihchatbot.viewmodel.ChatViewModel
import com.vihmessenger.vihchatbot.viewmodel.ProfileViewModel
import org.json.JSONObject
import java.io.File
import com.vihmessenger.vihchatbot.utils.ExternalUrl

class ChatActivity : BaseActivity() {

    companion object {
        private const val TAG = "ChatActivity"
        const val SESSION_ID = "SESSION_ID"

        // A shoot can land in get-user-session (the chat list) before it's returned by
        // chat-history for its (channel_id, enterprise_id). When we open the chat straight
        // from a notification tap, the pushed message may therefore be missing on the first
        // fetch. Retry a bounded number of times before settling on the empty/welcome state.
        private const val MAX_HISTORY_RETRIES = 4
        private const val HISTORY_RETRY_DELAY_MS = 1500L

        // A Flow Builder response is produced after the send call has already returned its
        // acknowledgement. Four polls over ~10s covers a normal flow turnaround without
        // becoming a background poller — it stops as soon as the history grows.
        private const val MAX_FLOW_RESPONSE_POLLS = 4
        private const val FLOW_RESPONSE_POLL_DELAY_MS = 2500L

        // The backend returns a non-reply placeholder when a message is routed to a chatbot
        // flow (which may be unconfigured) — e.g. "Message handled by flow" / "Message handled
        // by the flow", sometimes with is_flow == 0. Match on both distinctive tokens so minor
        // wording differences ("the") are still suppressed.
        fun isFlowAcknowledgement(message: String): Boolean {
            val m = message.trim()
            return m.contains("handled by", ignoreCase = true) && m.contains("flow", ignoreCase = true)
        }

        fun startIntent(
            context: Context,
            session_id: String,
            channel_name: String?,
            channel_image: String?,
            channel: EnterPriseModel?,
            id: String?,
            hashcode: String?
        ) {
            val intent = Intent(context, ChatActivity::class.java)
            intent.putExtra(SESSION_ID, session_id)
            intent.putExtra(AppConstants.ID, id)
            intent.putExtra(AppConstants.CHANNEL_NAME, channel_name)
            intent.putExtra(AppConstants.CHANNEL_LOGO, channel_image)
            intent.putExtra(AppConstants.CHANNEL_EXTRA, channel)
            intent.putExtra(AppConstants.HASHCODE_EXTRA, hashcode)
            // Headless hosts (React Native / Expo bridges) commonly hand us the
            // application-scoped context. startActivity() throws AndroidRuntimeException
            // from a non-Activity context unless NEW_TASK is set.
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private val _viewBinder by lazy { ActivityChatBinding.inflate(layoutInflater) }
    lateinit var emojiPopup: EmojiPopup
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var profileViewModel: ProfileViewModel
    private lateinit var chatAdapter: ChatAdapter

    private var sessionId: String? = null
    private var hashCodeStr: String? = null

    // Callable voice-bot for this conversation (v2): the descriptor carried by the most recent
    // message that has one. Null while no message carries a voice_bot — gates the call button.
    private var voiceBot: VoiceBot? = null

    private var hasLoadedChats = false
    private var currentChannel: EnterPriseModel? = null

    // CompanyProfile can change block/mute/promo state; it returns the updated enterprise so
    // we refresh currentChannel (and the composer guard) in place on return.
    private val companyProfileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val updated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getSerializableExtra(AppConstants.CHANNEL_EXTRA, EnterPriseModel::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getSerializableExtra(AppConstants.CHANNEL_EXTRA) as? EnterPriseModel
            }
            if (updated != null) {
                currentChannel = updated
                updateBlacklistState()
            }
        }

    // Whether this screen was opened by tapping a push notification (vs. from the chat
    // list). Only then do we retry an empty history fetch, since a pushed message is
    // expected to be present.
    private var isLaunchedFromNotification = false
    private var historyRetryCount = 0
    private val historyRetryHandler = Handler(Looper.getMainLooper())
    private val flowPollHandler = Handler(Looper.getMainLooper())


    val prefs: Prefs by lazy {
        AppController.prefs!!
    }

    private val chatUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.vihmessenger.vihchatbot.FCM_MESSAGE") {
                getAllChats()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(_viewBinder.root)
        applyThemeAndSetupListeners()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        VihLog.d("ChatActivityStatus", "onCreate: View and theme setup process initiated.")

        val initialFallbackColor = ContextCompat.getColor(this, R.color.status_bar_grey)
        updateStatusBarColor(initialFallbackColor)
    }

    override fun initViewModels() {
        chatViewModel = getViewModel(
            viewModel = ChatViewModel(this), className = ChatViewModel::class.java
        )
        profileViewModel = getViewModel(
            viewModel = ProfileViewModel(this), className = ProfileViewModel::class.java
        )
    }

    /**
     * Hide the composer and show a "blocked" banner (with an Unblock action) when the user
     * has blocked this enterprise. The backend rejects such sends with 2003, so this stops
     * the user firing a message that would silently fail. Safe to call before [currentChannel]
     * is loaded — a null flag reads as not-blocked.
     */
    private fun updateBlacklistState() {
        val blocked = currentChannel?.is_blacklisted_by_user == true
        _viewBinder.llChatInbox.visibility = if (blocked) View.GONE else View.VISIBLE
        _viewBinder.lyBlacklistedState.visibility = if (blocked) View.VISIBLE else View.GONE
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun initView() {
        setupCustomToolbar()

        updateBlacklistState()

        val enterpriseId = intent.getStringExtra(AppConstants.ID)
        if (currentChannel == null && !enterpriseId.isNullOrBlank()) {
            VihLog.d(TAG, "currentChannel is null (likely from FCM). Fetching enterprise details for ID: $enterpriseId")
            chatViewModel.getEnterpriceModel(showBlockingLoader = false, enterpriseId = enterpriseId)
        }

        // EmojiPopup's constructor throws IllegalStateException unless an EmojiProvider has been
        // installed process-wide. BaseActivity.onCreate installs one, but the install itself can
        // fail on a device without the Google fonts provider — and an emoji keyboard is not worth
        // taking the chat screen down for.
        runCatching {
            emojiPopup = EmojiPopup(
                rootView = _viewBinder.clChat,
                editText = _viewBinder.lyChatInbox.edtChatInbox
            )
        }.onFailure {
            VihLog.e(TAG, "EmojiPopup unavailable: ${it.javaClass.simpleName} - ${it.message}")
        }

        sessionId = intent.getStringExtra(SESSION_ID)
        // The FCM content intent always carries "notification_id" (see
        // MyFirebaseMessagingService.createContentIntent); the chat-list path does not.
        isLaunchedFromNotification = intent.hasExtra("notification_id")
        hashCodeStr = intent.getStringExtra(AppConstants.HASHCODE_EXTRA)?.takeIf { it.isNotBlank() }
            ?: prefs.hashcode

        setRecyclerView()

        _viewBinder.btnUnblock.setOnClickListener {
            currentChannel?.let { profileViewModel.blacklistEnterprise(it.id, blacklist = false) }
        }

        _viewBinder.lyChatInbox.ibSendChat.setOnClickListener {
            val messageText = _viewBinder.lyChatInbox.edtChatInbox.editableText.toString().trim()
            if (messageText.isEmpty()) {
                return@setOnClickListener
            }

            chatAdapter.insertMessage(
                MessageModel(
                    session_id = sessionId ?: "",
                    message = messageText,
                    suggested_questions = mutableListOf(),
                    sent_by = AppConstants.RECEIVER_ID,
                    created_at = currentDateTime().toString(),
                    updated_at = "",
                    session = sessionId ?: "",
                    cpaas_json = null
                )
            )
            (_viewBinder.rvMessage.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                chatAdapter.itemCount - 1,
                0
            )

            setChatProgressLayout(messageText)

            chatViewModel.getChatResponse(
                false,
                question = messageText,
                sessionId = sessionId ?: "",
                hashcode = hashCodeStr ?: "",
                enterpriseId = intent.getStringExtra(AppConstants.ID) ?: ""
            )
            _viewBinder.lyChatInbox.edtChatInbox.setText("")
        }

        getVihSettings()?.let { settings ->
            if (settings.background_style == "image" && settings.choose_other_image.isNotEmpty()) {
                CustomImageLoader.loadImageView(
                    imageView = _viewBinder.ivChatBackground,
                    url = settings.choose_other_image,
                    onError = {
                        _viewBinder.ivChatBackground.setImageResource(R.drawable.placeholder)
                    }
                )
            }

            if (settings.background_style == "color" && settings.solid_color.isNotEmpty()) {
                _viewBinder.ivChatBackground.setSolidColorFilterCompat()
            }
        }
    }

    /**
     * Re-fetches chat history a few times after a flow acknowledgement, until the flow's real
     * response shows up. Cancelled by [onDestroy] and restarted by each new acknowledgement.
     */
    private fun scheduleFlowResponsePoll() {
        flowPollHandler.removeCallbacksAndMessages(null)
        val messagesBefore = if (::chatAdapter.isInitialized) chatAdapter.itemCount else 0
        var attempt = 0
        lateinit var poll: Runnable
        poll = Runnable {
            attempt++
            getAllChats()
            val grew = ::chatAdapter.isInitialized && chatAdapter.itemCount > messagesBefore
            if (!grew && attempt < MAX_FLOW_RESPONSE_POLLS) {
                flowPollHandler.postDelayed(poll, FLOW_RESPONSE_POLL_DELAY_MS)
            } else {
                VihLog.d(TAG, "Flow response poll finished after $attempt attempt(s), grew=$grew")
            }
        }
        flowPollHandler.postDelayed(poll, FLOW_RESPONSE_POLL_DELAY_MS)
    }

    private fun getAllChats() {
        _viewBinder.pbChat.visibility = View.VISIBLE
        updateFirstMessageVisibility()
        val currentHashCode =
            intent.getStringExtra(AppConstants.HASHCODE_EXTRA)?.takeIf { it.isNotBlank() }
                ?: prefs.hashcode

        val enterpriseId = intent.getStringExtra(AppConstants.ID) ?: ""

        if (currentHashCode != null) {
            chatViewModel.getChatHistoryResponse(
                false,
                currentHashCode,
                enterpriseId
            )
        } else {
            VihLog.e(TAG, "Hashcode is null")
            _viewBinder.pbChat.visibility = View.GONE
            updateFirstMessageVisibility()
        }

        _viewBinder.chatAppBar.llToolChat.setOnClickListener {
            if (currentChannel != null) {
                companyProfileLauncher.launch(
                    Intent(this@ChatActivity, CompanyProfileActivity::class.java).apply {
                        VihLog.d(TAG, "Starting CompanyProfileActivity with channel: $currentChannel")
                        putExtra(AppConstants.CHANNEL_EXTRA, currentChannel)
                        putExtra(AppConstants.HASHCODE_EXTRA, hashCodeStr)
                    }
                )
            } else {
                Toast.makeText(this, "Loading details...", Toast.LENGTH_SHORT).show()
                VihLog.w(TAG, "Toolbar clicked but currentChannel details are not yet available.")
            }
        }
        _viewBinder.chatAppBar.ivBackArrow.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        _viewBinder.chatAppBar.btnVoicebot.setOnClickListener {
            launchVoicebot()
        }
    }

    private fun launchVoicebot() {
        val vb = voiceBot
        if (vb == null || !vb.isCallable) {
            VihLog.w(TAG, "launchVoicebot: no callable voice_bot on this conversation — aborting")
            return
        }
        // Pass the caller's name so the agent can fill a {{customer_name}} placeholder in its
        // greeting. Sourced from the cached profile; empty is fine (the agent stays generic).
        val customerName = getProfileData()?.full_name.orEmpty()
        startActivity(
            com.vihmessenger.vihchatbot.ui.activity.VoicebotActivity.startIntent(
                context = this,
                wsUrl = vb.wsUrl!!,
                agentName = vb.name.orEmpty(),
                customerName = customerName
            )
        )
    }

    /**
     * Voice-bot v2 gate: a conversation is callable when its messages carry a `voice_bot`, so
     * re-derive it from the messages currently on screen — the most recent one that has a
     * descriptor wins. A newly created bot makes future messages carry one; a removed bot makes
     * them stop, and a history reload (which rebuilds the adapter list) then clears the button.
     */
    private fun refreshVoiceBotFromMessages() {
        if (!::chatAdapter.isInitialized) return
        val vb = chatAdapter.messageList
            .filterIsInstance<MessageModel>()
            .lastOrNull { it.voice_bot?.isCallable == true }
            ?.voice_bot
        voiceBot = vb
        _viewBinder.chatAppBar.btnVoicebot.visibility =
            if (vb != null) View.VISIBLE else View.GONE
    }

    private fun setupCustomToolbar() {
        setToolbar()
        _viewBinder.chatAppBar.tvToolBarMain.visibility = View.GONE
        _viewBinder.chatAppBar.llToolChat.visibility = View.VISIBLE

        var channelName: String?
        var channelLogoUrl: String?

        val channelFromIntent: EnterPriseModel? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(AppConstants.CHANNEL_EXTRA, EnterPriseModel::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(AppConstants.CHANNEL_EXTRA) as? EnterPriseModel
        }

        if (channelFromIntent != null) {
            VihLog.d(TAG, "Setting up toolbar using CHANNEL_EXTRA (EnterPriseModel).")
            this.currentChannel = channelFromIntent
            // Resolve across all name/logo fields — the discover API leaves display_name and
            // display_img null, so reading them directly showed a blank "Chat" toolbar and made
            // the screen look like a dummy chat window.
            channelName = currentChannel?.resolvedDisplayName?.takeIf { it.isNotBlank() }
            channelLogoUrl = currentChannel?.resolvedLogoUrl
        } else {
            VihLog.d(TAG, "CHANNEL_EXTRA is null. Setting up toolbar using fallback extras (CHANNEL_NAME, CHANNEL_LOGO).")
            this.currentChannel = null
            channelName = intent.getStringExtra(AppConstants.CHANNEL_NAME)
            channelLogoUrl = intent.getStringExtra(AppConstants.CHANNEL_LOGO)
        }

        _viewBinder.chatAppBar.tvToolBarChatName.text = channelName?.takeIf { it.isNotEmpty() } ?: "Chat"

        if (!channelLogoUrl.isNullOrBlank()) {
            _viewBinder.chatAppBar.ivToolbar.visibility = View.VISIBLE
            CustomImageLoader.loadImageView(
                imageView = _viewBinder.chatAppBar.ivToolbar,
                url = channelLogoUrl,
                name = channelName ?: "C",
                onError = {
                    _viewBinder.chatAppBar.ivToolbar.setImageResource(R.drawable.placeholder)
                }
            )
        } else {
            _viewBinder.chatAppBar.ivToolbar.visibility = View.GONE
        }
    }

    private fun chatWelcomeMessage() {
        // This function is now only responsible for hiding the progress bar.
        // It does NOT add any items to the adapter.
        _viewBinder.pbChat.visibility = View.GONE
    }

    private fun setChatProgressLayout(messageBeingSent: String) {
        chatAdapter.insertMessage(
            MessageModel(
                session_id = sessionId ?: "",
                message = "",
                suggested_questions = mutableListOf(),
                sent_by = AppConstants.LEFT_CHAT_PROGRESS,
                created_at = currentDateTime(),
                updated_at = currentDateTime(),
                session = sessionId ?: "",
                cpaas_json = null
            )
        )
        _viewBinder.rvMessage.scrollToPosition(chatAdapter.itemCount - 1)
    }

    private fun setToolbar() {
        setSupportActionBar(_viewBinder.chatAppBar.tbMain)
        supportActionBar?.let {
            it.setHomeAsUpIndicator(R.drawable.ic_arrow_back)
            it.setHomeButtonEnabled(true)
            it.setDisplayHomeAsUpEnabled(true)
            it.setDisplayShowTitleEnabled(false)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun setObservers() {
        chatViewModel.chatMessageLiveData.observe(this) { response ->
            chatAdapter.removeProgressMessage()
            response?.data?.let { data ->
                if (response.status) {
                    // Preserve the session id so the conversation continues even when the reply
                    // itself is suppressed below.
                    sessionId = data.session_id

                    // Suppress flow acknowledgements: when a message is routed to a chatbot flow
                    // the backend returns is_flow == 1 and/or a generic "…handled by the flow"
                    // placeholder. Neither is a real reply, so don't render it as a bot bubble.
                    val isFlowAck = data.is_flow == 1 || isFlowAcknowledgement(data.message)

                    if (isFlowAck) {
                        // The acknowledgement is not the reply — the flow produces that
                        // asynchronously and delivers it by push. Relying on the push alone is
                        // fragile: it needs the SDK's FirebaseMessagingService to be the one
                        // Android picked for MESSAGING_EVENT, which is not guaranteed in a host
                        // that ships its own (expo-notifications, for one). Poll chat-history a
                        // bounded number of times so the flow's response appears on its own.
                        scheduleFlowResponsePoll()
                    }

                    if (!isFlowAck) {
                        chatAdapter.insertMessage(
                            MessageModel(
                                session_id = data.session_id,
                                message = data.message ?: "",
                                suggested_questions = data.suggested_questions ?: mutableListOf(),
                                sent_by = data.sent_by ?: "",
                                created_at = data.created_at ?: "",
                                updated_at = data.updated_at ?: "",
                                session = sessionId ?: "",
                                cpaas_json = null,
                                interactive = data.interactive,
                                voice_bot = data.voice_bot
                            )
                        )
                        refreshVoiceBotFromMessages()
                        (_viewBinder.rvMessage.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                            chatAdapter.itemCount - 1,
                            0
                        )
                    }
                }
            }
        }

        chatViewModel.enterpriseDetails.observe(this) { response ->
            val enterpriseModelFromApi = response?.data
            if (response != null && response.status && enterpriseModelFromApi != null) {
                this.currentChannel = enterpriseModelFromApi
                VihLog.d(TAG, "Successfully fetched and stored enterprise details: ${currentChannel?.displayNameModel?.display_name}")
                // Re-update visibility in case the display_msg is now available
                updateFirstMessageVisibility()
                // enterprise-details carries the fresh is_blacklisted_by_user flag.
                updateBlacklistState()
            } else {
                // Non-fatal: the chat itself works from the intent extras. This is only the
                // enrichment lookup, and it is expected to miss when the caller passed a
                // `user_id` — see VihDiscover.knownEnterprises.
                VihLog.e(TAG, "Failed to fetch enterprise details: ${response?.message ?: "no data"}")
            }
        }

        chatViewModel.chatHistoryLiveData.observe(this) { response ->
            val chatHistoryData = response?.data

            if (chatHistoryData.isNullOrEmpty()) {
                // Empty history right after a notification tap usually means the pushed
                // message hasn't propagated to chat-history yet. Keep the spinner up and
                // retry a few times before falling back to the empty/welcome state.
                if (isLaunchedFromNotification && historyRetryCount < MAX_HISTORY_RETRIES) {
                    historyRetryCount++
                    VihLog.d(TAG, "Chat history empty after notification tap — retry $historyRetryCount/$MAX_HISTORY_RETRIES in ${HISTORY_RETRY_DELAY_MS}ms")
                    historyRetryHandler.postDelayed({ getAllChats() }, HISTORY_RETRY_DELAY_MS)
                    return@observe
                }
                // History is empty. Do nothing to the adapter.
                // The welcome text will be shown by updateFirstMessageVisibility.
                _viewBinder.pbChat.visibility = View.GONE
                VihLog.d(TAG, "Chat history is empty. The 'tvfirstMsg' view will be shown.")
            } else {
                // History exists — the message arrived; stop any pending retries.
                historyRetryCount = 0
                historyRetryHandler.removeCallbacksAndMessages(null)
                _viewBinder.pbChat.visibility = View.GONE
                // History exists. Populate the adapter.
                VihLog.d(TAG, "Chat history received with ${chatHistoryData.size} messages.")
                val lastMessage = chatHistoryData.lastOrNull()
                val targetCpaasIdToClear: String? = lastMessage?.cpaas_json?.id?.toString()

                if (targetCpaasIdToClear != null) {
                    clearNotificationsByCpaasId(this@ChatActivity, targetCpaasIdToClear)
                }

                // Drop flow acknowledgements so a persisted "…handled by the flow" placeholder
                // doesn't reappear as a bot bubble when history reloads.
                val displayMessages = chatHistoryData.filterNot { msg ->
                    msg.is_flow == 1 || isFlowAcknowledgement(msg.message)
                }
                chatAdapter.insertAllMessage(displayMessages)
                refreshVoiceBotFromMessages()

                if (chatAdapter.itemCount > 0) {
                    (_viewBinder.rvMessage.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                        chatAdapter.itemCount - 1,
                        0
                    )
                }
            }

            // This single call correctly updates the UI based on the adapter's state.
            updateFirstMessageVisibility()
        }

        chatViewModel.errorLiveData.observe(this) {
            if (it != null) {
                // Always clear the history loader — otherwise a failed chat-history fetch
                // (common right after a push tap while the backend is still catching up)
                // leaves the chat window spinning indefinitely.
                _viewBinder.pbChat.visibility = View.GONE
                chatAdapter.removeProgressMessage()
                updateFirstMessageVisibility()
                Toast.makeText(this, "Something Went Wrong", Toast.LENGTH_SHORT).show()
            }
        }

        // Unblock from the blacklisted banner restores the composer in place.
        profileViewModel.blacklistResultLiveData.observe(this) { blocked ->
            currentChannel?.is_blacklisted_by_user = blocked
            updateBlacklistState()
            if (!blocked) Toast.makeText(this, "Business unblocked", Toast.LENGTH_SHORT).show()
        }
        profileViewModel.enterpriseMutationError.observe(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun setListeners() {}

    override fun onViewClick(view: View?) {}

    override fun onThemeChanged(
        primaryColor: Int,
        secondaryColor: Int,
        primaryTextColor: Int,
        secondaryTextColor: Int,
        headerColor: Int,
        defaultTextColor: Int
    ) {
        _viewBinder.chatAppBar.tbMain.setBackgroundColor(headerColor)
        _viewBinder.clChat.setBackgroundColor(headerColor)
        _viewBinder.chatAppBar.tvToolBarMain.setTextColor(defaultTextColor)
        _viewBinder.chatAppBar.tvToolBarChatName.setTextColor(defaultTextColor)
        AppCompatResources.getDrawable(this, R.drawable.ic_arrow_back)?.let { drawable ->
            val mutableDrawable = DrawableCompat.wrap(drawable).mutate()
            DrawableCompat.setTint(mutableDrawable, defaultTextColor)
            _viewBinder.chatAppBar.ivBackArrow.setImageDrawable(mutableDrawable)
        }
        // The call button sits on the same themed header as the back arrow, so it has to be
        // tinted from the same colour. Its drawable used to carry a hardcoded dark grey, which
        // on a dark header rendered it all but invisible.
        ImageViewCompat.setImageTintList(
            _viewBinder.chatAppBar.btnVoicebot,
            ColorStateList.valueOf(defaultTextColor)
        )

        val desiredStatusBarColorForChat = ContextCompat.getColor(this, R.color.status_bar_grey)
        updateStatusBarColor(desiredStatusBarColorForChat)

        val sendButtonBackground = GradientDrawable()
        sendButtonBackground.shape = GradientDrawable.OVAL
        sendButtonBackground.setColor(primaryColor)
        _viewBinder.lyChatInbox.ibSendChat.background = sendButtonBackground
    }

    private fun setRecyclerView() {
        // --- THIS IS THE FIX ---
        // Set the layout manager without reversing it.
        _viewBinder.rvMessage.layoutManager = LinearLayoutManager(this)

        _viewBinder.rvMessage.setHasFixedSize(false)

        val channel: EnterPriseModel? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(AppConstants.CHANNEL_EXTRA, EnterPriseModel::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(AppConstants.CHANNEL_EXTRA) as? EnterPriseModel
        }
        val channelName = channel?.displayNameModel?.display_name ?: ""
        val channelLogo = channel?.display_img.takeIf { !it.isNullOrBlank() } ?: channel?.profile_picture

        chatAdapter = ChatAdapter(
            this@ChatActivity,
            rvlistener,
            channelName,
            channelLogo,
            emptyView = _viewBinder.tvfirstMsg
        )

        _viewBinder.rvMessage.setItemViewCacheSize(20)
        _viewBinder.rvMessage.adapter = chatAdapter
        _viewBinder.rvMessage.itemAnimator = null

        updateFirstMessageVisibility()
    }

    private fun updateFirstMessageVisibility() {
        if (::chatAdapter.isInitialized && chatAdapter.messageList.isEmpty() && _viewBinder.pbChat.visibility != View.VISIBLE) {
            _viewBinder.tvfirstMsg.text = currentChannel?.displayNameModel?.display_msg ?: "Hello! Need support, info, or have a question? Just send a message to get started."
            _viewBinder.tvfirstMsg.visibility = View.VISIBLE
        } else {
            _viewBinder.tvfirstMsg.visibility = View.GONE
        }
    }

    var rvlistener = object : onItemChatClickListener {
        override fun onItemClick(item: String, session_id_from_item: String) {
            if (item.trim().isEmpty()) return

            chatAdapter.insertMessage(
                MessageModel(
                    session_id = session_id_from_item,
                    message = item,
                    suggested_questions = mutableListOf(),
                    sent_by = AppConstants.RECEIVER_ID,
                    created_at = currentDateTime().toString(),
                    updated_at = currentDateTime().toString(),
                    session = session_id_from_item,
                    cpaas_json = null
                )
            )
            (_viewBinder.rvMessage.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                chatAdapter.itemCount - 1,
                0
            )

            setChatProgressLayout(item)

            val currentEnterpriseId = intent.getStringExtra(AppConstants.ID) ?: ""
            chatViewModel.getChatResponse(
                false,
                question = item,
                sessionId = sessionId ?: "",
                hashcode = hashCodeStr ?: "",
                enterpriseId = currentEnterpriseId
            )
        }

        override fun onInteractiveButtonClick(button: com.vihmessenger.vihchatbot.data.model.InteractiveButton) {
            val value = button.value?.trim().orEmpty()
            when (button.type?.lowercase()) {
                // Send the value as the user's next message — identical to tapping a suggestion chip.
                "quick_reply" -> if (value.isNotEmpty()) onItemClick(value, sessionId ?: "")
                "url" -> openInteractiveUrl(value)
                "action" -> handleInteractiveAction(value)
                // Unknown type (the server drops these, but be safe): ignore rather than risk
                // sending a link as a message.
                else -> VihLog.d(TAG, "Ignoring interactive button of unknown type: ${button.type}")
            }
        }

        private fun openInteractiveUrl(rawUrl: String) {
            if (rawUrl.isEmpty()) {
                Toast.makeText(this@ChatActivity, "Invalid link", Toast.LENGTH_SHORT).show()
                return
            }
            // SECURITY (VAPT F-16): scheme allowlist + https upgrade, centrally enforced.
            ExternalUrl.open(this@ChatActivity, rawUrl)
        }

        // Named in-app capability. We handle call-support natively; anything else degrades
        // gracefully per the GLM contract (MESSAGE_FLOW_GLM.md §4) rather than failing.
        private fun handleInteractiveAction(action: String) {
            when (action.lowercase()) {
                "call_support", "call", "call_agent", "contact_support" -> {
                    val number = currentChannel?.customercare?.takeIf { it.isNotBlank() }
                        ?: currentChannel?.phone?.takeIf { it.isNotBlank() }
                    if (!number.isNullOrBlank()) {
                        startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
                    } else {
                        Toast.makeText(this@ChatActivity, "Support number unavailable", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    VihLog.d(TAG, "Unhandled interactive action: $action")
                    Toast.makeText(this@ChatActivity, "This option isn't available yet.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onChatButtonClick(position: Int, model: ButtonModel) {
            val btnType = model.btn_typ ?: ""
            val btnValue = model.btn_value

            when (btnType) {
                "call" -> {
                    if (!btnValue.isNullOrEmpty()) {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", btnValue, null))
                        startActivity(intent)
                    }
                }
                "email" -> {
                    if (!btnValue.isNullOrEmpty()) {
                        val uri = Uri.parse("mailto:$btnValue").buildUpon()
                            .appendQueryParameter("subject", AppConstants.FROM_VIH_TEXT).build()
                        val emailIntent = Intent(Intent.ACTION_SENDTO, uri)
                        startActivity(Intent.createChooser(emailIntent, AppConstants.FROM_VIH_TEXT))
                    }
                }
                "web" -> {
                    if (!btnValue.isNullOrEmpty()) {
                        // SECURITY (VAPT F-16): this branch used to DOWNGRADE a scheme-less
                        // URL to http://. Now allowlisted and upgraded to https.
                        ExternalUrl.open(this@ChatActivity, btnValue)
                    } else {
                        Toast.makeText(this@ChatActivity, "Invalid URL", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    if (btnValue.isNullOrBlank()) {
                        VihLog.d(TAG, "Button click with empty value and unhandled/empty type: $btnType")
                        return
                    }
                    handleDefaultButtonAction(btnValue)
                }
            }
        }

        private fun handleDefaultButtonAction(value: String) {
            chatAdapter.insertMessage(
                MessageModel(
                    session_id = sessionId ?: "",
                    message = value,
                    suggested_questions = mutableListOf(),
                    sent_by = AppConstants.RECEIVER_ID,
                    created_at = currentDateTime().toString(),
                    updated_at = currentDateTime().toString(),
                    session = sessionId ?: "",
                    cpaas_json = null
                )
            )
            _viewBinder.rvMessage.scrollToPosition(chatAdapter.itemCount - 1)

            setChatProgressLayout(value)

            val enterpriseId = intent.getStringExtra(AppConstants.ID) ?: ""
            chatViewModel.getChatResponse(
                false,
                question = value,
                sessionId = sessionId ?: "",
                hashcode = hashCodeStr ?: "",
                enterpriseId = enterpriseId
            )
        }

        override fun onImageClick(imageUrl: String) {
            if (imageUrl.isNotEmpty()) {
                val channel: EnterPriseModel? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getSerializableExtra(
                            AppConstants.CHANNEL_EXTRA,
                            EnterPriseModel::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getSerializableExtra(AppConstants.CHANNEL_EXTRA) as? EnterPriseModel
                    }
                VideoActivity.startIntent(
                    context = this@ChatActivity,
                    mediaUrl = imageUrl,
                    mediaType = VideoActivity.MEDIA_TYPE_IMAGE,
                    channel = channel
                )
            } else {
                Toast.makeText(this@ChatActivity, "Invalid Image URL", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onVideoClick(videoFile: File) {
            if (videoFile.exists()) {
                val channel: EnterPriseModel? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getSerializableExtra(
                            AppConstants.CHANNEL_EXTRA,
                            EnterPriseModel::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getSerializableExtra(AppConstants.CHANNEL_EXTRA) as? EnterPriseModel
                    }
                VihLog.e(TAG, "onVideoClick - Channel retrieved: $channel")

                val videoUriString = Uri.fromFile(videoFile).toString()
                VideoActivity.startIntent(
                    context = this@ChatActivity,
                    mediaUrl = videoUriString,
                    mediaType = VideoActivity.MEDIA_TYPE_VIDEO,
                    channel = channel
                )
            } else {
                Toast.makeText(this@ChatActivity, "Video file not found.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A fresh notification tap can be routed here (FLAG_ACTIVITY_SINGLE_TOP/CLEAR_TOP)
        // instead of creating a new instance. Adopt the new extras and re-fetch so the
        // newly-pushed message is loaded, resetting the retry budget for it.
        setIntent(intent)
        if (intent.hasExtra("notification_id")) {
            isLaunchedFromNotification = true
            historyRetryCount = 0
            historyRetryHandler.removeCallbacksAndMessages(null)
            getAllChats()
        }
    }

    override fun onDestroy() {
        historyRetryHandler.removeCallbacksAndMessages(null)
        flowPollHandler.removeCallbacksAndMessages(null)
        if (::chatAdapter.isInitialized) {
            chatAdapter.cleanupAllAudioPlayers()
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // Always re-fetch on resume, not just the first time. Anything that arrived while the
        // screen was away — a flow response, a push the SDK's service never received — is
        // otherwise invisible until the Activity is destroyed and rebuilt, which is what made
        // "leave the chat and come back" the workaround for a missing reply.
        getAllChats()
        hasLoadedChats = true

        val filter = IntentFilter("com.vihmessenger.vihchatbot.FCM_MESSAGE")
        LocalBroadcastManager.getInstance(this@ChatActivity)
            .registerReceiver(chatUpdateReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        if (::chatAdapter.isInitialized) {
            chatAdapter.cleanupAllAudioPlayers()
        }
        LocalBroadcastManager.getInstance(this@ChatActivity).unregisterReceiver(chatUpdateReceiver)
    }

    fun getActiveAppNotifications(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                val activeNotifications: Array<StatusBarNotification> =
                    notificationManager.activeNotifications

                VihLog.d("ActiveNotifications", "Found ${activeNotifications.size} active notifications for this app.")
                for (statusBarNotification in activeNotifications) {
                    val notification = statusBarNotification.notification
                    val title = notification.extras.getString("android.title")
                    val text = notification.extras.getString("android.text")
                    VihLog.d("ActiveNotifications", "ID: ${statusBarNotification.id}, Tag: ${statusBarNotification.tag}, Title: $title, Text: $text")
                }
            } catch (e: Exception) {
                VihLog.e("ActiveNotifications", "Error getting active notifications", e)
            }
        }
    }

    fun clearNotificationsByCpaasId(context: Context, targetCpaasContentId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                val activeNotifications: Array<StatusBarNotification> =
                    notificationManager.activeNotifications
                var clearedCount = 0

                for (statusBarNotification in activeNotifications) {
                    val extras = statusBarNotification.notification.extras
                    val cpaasJsonString = extras.getString("cpaas_json")

                    if (cpaasJsonString != null) {
                        try {
                            val cpaasJson = JSONObject(cpaasJsonString)
                            val cpaasIdInNotification = cpaasJson.optString("id")

                            if (cpaasIdInNotification == targetCpaasContentId) {
                                VihLog.i("NotificationClear", "MATCH FOUND! Clearing Android Notification ID: ${statusBarNotification.id}")
                                notificationManager.cancel(statusBarNotification.id)
                                clearedCount++
                            }
                        } catch (e: org.json.JSONException) {
                            VihLog.e("NotificationClear", "Error parsing cpaas_json from notification extras.", e)
                        }
                    }
                }

                if (clearedCount > 0) {
                    VihLog.i("NotificationClear", "Cleared $clearedCount notifications matching cpaas_id: $targetCpaasContentId")
                }
            } catch (e: Exception) {
                VihLog.e("NotificationClear", "Error accessing/clearing active notifications", e)
            }
        }
    }
}