package com.vihmessenger.vihchatbot.listener

import com.vihmessenger.vihchatbot.data.model.ChatListModel
import com.vihmessenger.vihchatbot.data.model.EnterPriseModel
import com.vihmessenger.vihchatbot.data.model.EnterprisesDiscoverData

interface OnDiscoverRvItemClickListener {
    fun onChatClick(position: Int, model: ChatListModel)
    fun onStartChatClick(position: Int, model: EnterPriseModel)
}