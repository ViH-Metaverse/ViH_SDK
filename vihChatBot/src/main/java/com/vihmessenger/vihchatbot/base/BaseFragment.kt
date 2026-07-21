import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vihmessenger.vihchatbot.data.services.BaseViewModelFactory
import com.vihmessenger.vihchatbot.utils.DynamicThemeManager
import com.vihmessenger.vihchatbot.utils.PermissionRequestCodes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

abstract class BaseFragment : Fragment(), DynamicThemeManager.ThemeChangeListener {

    companion object {
        var TAG = BaseFragment::class.java.simpleName
    }

    protected val _activity by lazy { context as BaseActivity }

    // Internet connectivity tracking
    private val _isInternetAvailable = MutableStateFlow(false)
    val isInternetAvailable: StateFlow<Boolean> = _isInternetAvailable

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    abstract fun initViewModels()
    abstract fun onViewClick(view: View?)
    abstract fun initView(view: View)
    abstract fun setListeners()
    abstract fun setObservers()

    protected open fun applyTheme(primaryColor: Int) {
        // Override in child fragments to update UI elements with the new color
    }

    override fun onThemeChanged(
        primaryColor: Int,
        secondaryColor: Int,
        primaryTextColor: Int,
        secondaryTextColor: Int,
        headerColor: Int,
        defaultTextColor: Int
    ) {
        // Call the protected method for subclasses to implement
        applyTheme(primaryColor)
    }

    // New methods for internet handling
    protected open fun onInternetAvailable() {
        // Default implementation - can be overridden by child fragments
    }

    protected open fun onInternetUnavailable() {
        // Default implementation - can be overridden by child fragments
    }

    protected open fun onGalleryPermissionGranted() {

    }

    protected open fun onGalleryPermissionDenied() {

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViewModels()
        initView(view)
        setListeners()
        setObservers()

        // Initialize internet connectivity check
        setupConnectivityMonitoring()

        DynamicThemeManager.registerListener(this)
        applyTheme(DynamicThemeManager.getPrimaryColor())

    }

    override fun onResume() {
        super.onResume()
        registerNetworkCallback()
    }

    override fun onPause() {
        super.onPause()
        unregisterNetworkCallback()
    }

    protected fun setOnClickListener(view: View?) {
        view?.setOnClickListener(onClickListener)
    }

    protected val onClickListener = View.OnClickListener { view ->
        onViewClick(view)
    }

    protected fun showBlockingLoader() {
        if (activity is BaseActivity) {
            (activity as BaseActivity).showBlockingLoader()
        }
    }

    protected fun hideBlockingLoader() {
        if (activity is BaseActivity) {
            (activity as BaseActivity).hideBlockingLoader()
        }
    }

    protected fun showToast(message: String?) {
        if (activity is BaseActivity) {
            (activity as BaseActivity).showToast(message)
        }
    }

    protected fun <T : ViewModel> getViewModel(
        fragment: BaseActivity, viewModel: ViewModel, className: Class<T>
    ): T {
        return ViewModelProvider(
            fragment, BaseViewModelFactory(viewModel, className)
        ).get(className)
    }

    protected fun <T : ViewModel> getFragmentViewModel(
        fragment: Fragment, viewModel: ViewModel, className: Class<T>
    ): T {
        return ViewModelProvider(
            fragment, BaseViewModelFactory(viewModel, className)
        ).get(className)
    }

    // Internet connectivity methods
    private fun setupConnectivityMonitoring() {
        connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Create network callback
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isInternetAvailable.value = true
                activity?.runOnUiThread {
                    if (isResumed) {
                        onInternetAvailable()
                    }
                }
            }

            override fun onLost(network: Network) {
                _isInternetAvailable.value = false
                activity?.runOnUiThread {
                    if (isResumed) {
                        onInternetUnavailable()
                    }
                }
            }
        }

        // Check initial state
        _isInternetAvailable.value = isInternetConnected()
    }

    private fun registerNetworkCallback() {
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback?.let {
                connectivityManager?.registerNetworkCallback(networkRequest, it)
            }

            // Call appropriate method based on current connection state
            if (_isInternetAvailable.value) {
                onInternetAvailable()
            } else {
                onInternetUnavailable()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Helper method to check current internet state
    protected fun isInternetConnected(): Boolean {
        val connectivityManager =
            context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        when (requestCode) {
            PermissionRequestCodes.GALLERY_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onGalleryPermissionGranted()
                } else {
                    onGalleryPermissionDenied()
                }
                return
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onDestroyView() {
        // Unregister from theme changes
        DynamicThemeManager.unregisterListener(this)
        super.onDestroyView()
    }
}