package com.vihmessenger.vihchatbot.utils

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow

class NetworkConnectivityManager(private val application: Application) : DefaultLifecycleObserver {

    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val isConnectedFlow = MutableStateFlow(false)
    private var noInternetDialog: AlertDialog? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentActivity: Activity? = null

    private var isAppInForeground = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mainHandler.post {
                isConnectedFlow.value = true
//                dismissNoInternetDialog()
            }
        }

        override fun onLost(network: Network) {
            mainHandler.post {
                // Don't assume we're offline just because ONE network dropped. On a device with
                // both WiFi and cellular, a WiFi<->cellular handoff fires onLost for the departing
                // network while the other is still connected — blindly setting false here left the
                // Discover tab stuck on "No Internet" despite a working connection. Re-evaluate the
                // actual active network instead.
                isConnectedFlow.value = isInternetAvailable()
                if (isAppInForeground && currentActivity != null && !isConnectedFlow.value) {
//                    showNoInternetDialog()
                }
            }
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        application.registerActivityLifecycleCallbacks(object :
            Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
                if (!isConnectedFlow.value) {
//                    showNoInternetDialog()
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (currentActivity == activity) {
//                    dismissNoInternetDialog()
                }
            }

            override fun onActivityStopped(activity: Activity) {}

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity == activity) {
                    currentActivity = null
//                    dismissNoInternetDialog()
                }
            }
        })

        isConnectedFlow.value = isInternetAvailable()
    }

    override fun onStart(owner: LifecycleOwner) {
        isAppInForeground = true
        if (!isConnectedFlow.value && currentActivity != null) {
//            showNoInternetDialog()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        isAppInForeground = false
//        dismissNoInternetDialog()
    }

    fun startMonitoring() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    fun stopMonitoring() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Handle exception if callback was not registered
        }

//        dismissNoInternetDialog()
    }

    private fun isInternetAvailable(): Boolean {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun showNoInternetDialog() {
        // Only show dialog if we have a valid activity and the previous dialog is not showing
        val activity = currentActivity ?: return

        if ((noInternetDialog == null || !noInternetDialog!!.isShowing) && !activity.isFinishing) {
            try {
                val builder = AlertDialog.Builder(activity)
                    .setTitle("No Internet Connection")
                    .setMessage("Please check your internet connection.")
                    .setCancelable(false) // Non-cancellable

                noInternetDialog = builder.create()
                noInternetDialog?.show()
            } catch (e: Exception) {
                // Safely handle any exceptions when showing dialog
            }
        }
    }

    private fun dismissNoInternetDialog() {
        try {
            if (noInternetDialog?.isShowing == true) {
                noInternetDialog?.dismiss()
            }
        } catch (e: Exception) {
            // Safely handle any exceptions when dismissing dialog
        }
    }
}
