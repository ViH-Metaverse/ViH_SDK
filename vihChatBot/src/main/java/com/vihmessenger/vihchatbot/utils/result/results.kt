package com.vihmessenger.vihchatbot.utils.result

sealed class Results<out T : Any> {
    data class Success<out T : Any>(val data: T) : Results<T>()
    data class Error(val error: String?) : Results<Nothing>()
}