package com.vihmessenger.vihchatbot.listener

import com.vihmessenger.vihchatbot.data.model.ButtonModel

interface OnChatTemplateButtonRvItemClickListener {
    fun onChatButtonClick(position: Int, model: ButtonModel)
}