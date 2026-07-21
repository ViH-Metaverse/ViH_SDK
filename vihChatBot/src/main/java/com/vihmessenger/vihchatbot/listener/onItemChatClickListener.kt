package com.vihmessenger.vihchatbot.listener

import com.vihmessenger.vihchatbot.data.model.ButtonModel
import com.vihmessenger.vihchatbot.data.model.InteractiveButton
import java.io.File

interface onItemChatClickListener {
    fun onItemClick(item: String, session_id: String)
    fun onChatButtonClick(position: Int, model: ButtonModel)
    fun onImageClick(imageUrl: String)
    fun onVideoClick(videoFile: File)

    /** A GLM interactive button (quick_reply / url / action) was tapped. */
    fun onInteractiveButtonClick(button: InteractiveButton)
}