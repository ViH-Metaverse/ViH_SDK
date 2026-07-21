package com.vihmessenger.vihchatbot.data.repository

import BaseActivity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.vihmessenger.vihchatbot.AppController
import com.vihmessenger.vihchatbot.data.services.BaseApiService
import com.vihmessenger.vihchatbot.utils.result.Results
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

open class BaseRepository(
    private val baseActivity: BaseActivity?, private val baseApiService: BaseApiService?
) {

    companion object {
        private const val AUTH_FAILED_ERROR = "Authentication failed"

        // One-shot guard: when the JWT expires, multiple in-flight requests will all
        // 401 — only the first one should navigate to login. It is re-armed on every fresh
        // app entry via [resetSessionExpiredGuard] (called from the launcher activity), so a
        // later expiry in a new session is handled again instead of being silently swallowed.
        @Volatile
        private var sessionExpiredHandled = false

        /**
         * Re-arm the session-expiry guard. Call this when the app (re)enters through its
         * launcher so that a subsequent token expiry routes to login again. Without this the
         * guard stayed latched for the whole process, leaving the app stuck on the dashboard
         * with a dead token (empty Discover, "Something Went Wrong" on chats).
         */
        fun resetSessionExpiredGuard() {
            synchronized(BaseRepository::class.java) {
                sessionExpiredHandled = false
            }
        }
    }

    // This version returns non-nullable T and handles errors by throwing custom exceptions
    suspend fun <T : Any> doSafeAPIRequest(
        call: suspend () -> Response<T>, showBlockingLoader: Boolean
    ): T {
        val result: Results<T> = returnSafeAPIResponse(call, baseActivity, showBlockingLoader)

        return when (result) {
            is Results.Success -> result.data
            is Results.Error -> {
                Log.e("BaseRepository", "API Error: ${result.error}")

                if (result.error == AUTH_FAILED_ERROR) {
                    handleSessionExpired()
                    // CancellationException is treated as cooperative coroutine cancel —
                    // it stops the calling coroutine without bubbling to the uncaught
                    // handler, so the app doesn't crash while we route to login.
                    throw CancellationException("Session expired")
                }

                baseActivity?.let {
                    it.runOnUiThread {
                        Toast.makeText(
                            it,
                            result.error ?: "Network error occurred",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                throw NoConnectionException(result.error ?: "Network request failed")
            }
        }
    }

    private fun handleSessionExpired() {
        if (sessionExpiredHandled) return
        synchronized(BaseRepository::class.java) {
            if (sessionExpiredHandled) return
            sessionExpiredHandled = true
        }

        val prefs = AppController.prefs
        prefs?.accessToken = null
        prefs?.refreshToken = null

        val app = AppController.appController ?: return
        baseActivity?.runOnUiThread {
            // Relaunch through the launcher → SplashFragment, which silently restores the
            // session via Cognito and only shows login if that restore fails. No toast here so
            // a recoverable expiry doesn't wrongly tell the user they must log in again.
            val launchIntent = app.packageManager.getLaunchIntentForPackage(app.packageName)
                ?: return@runOnUiThread
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
            app.startActivity(launchIntent)
        }
    }

    suspend fun <T : Any> doSafeAPIRequest(call: suspend () -> Response<T>): T {
        return doSafeAPIRequest(call, false)
    }

    private suspend fun <T : Any> returnSafeAPIResponse(
        call: suspend () -> Response<T>, baseActivity: BaseActivity?, showBlockingLoader: Boolean
    ): Results<T> {
        if (showBlockingLoader) {
            baseActivity?.showBlockingLoader()
        }

        try {
            val response = call.invoke()
            baseActivity?.hideBlockingLoader()

            if (response.isSuccessful && response.body() != null) {
                return Results.Success(response.body()!!)
            }

            return when (response.code()) {
                413 -> Results.Error(error = "Payload size too large")
                404 -> Results.Error(error = "Resource not found")
                500, 501, 502, 503 -> Results.Error(error = "Server error, please try again later")
                401 -> Results.Error(error = AUTH_FAILED_ERROR)
                403 -> Results.Error(error = "Access denied")
                else -> Results.Error(
                    error = response.errorBody()?.string() ?: "Unknown error occurred"
                )
            }

        } catch (e: Exception) {
            baseActivity?.hideBlockingLoader()

            val errorMessage = when (e) {
                is UnknownHostException -> "Cannot connect to server. Please check your internet connection."
                is SocketTimeoutException -> "Connection timed out. Please try again."
                is ConnectException -> "Failed to connect to server. Please check your internet connection."
                is SSLException -> "Secure connection failed. Please try again later."
                else -> e.localizedMessage ?: "An unexpected error occurred"
            }

            Log.e("BaseRepository", "API Exception: ${e.javaClass.simpleName} - $errorMessage")
            return Results.Error(errorMessage)
        }
    }
}
