package com.vihmessenger.vihchatbot.ui.fragments

import BaseActivity
import BaseFragment
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vihmessenger.vihchatbot.AppController
import com.vihmessenger.vihchatbot.adapters.DiscoverListAdapter
import com.vihmessenger.vihchatbot.constants.AppConstants
import com.vihmessenger.vihchatbot.data.model.ChatListModel
import com.vihmessenger.vihchatbot.data.model.EnterPriseModel
import com.vihmessenger.vihchatbot.databinding.FragmentDiscoverBinding
import com.vihmessenger.vihchatbot.listener.OnDiscoverRvItemClickListener
import com.vihmessenger.vihchatbot.ui.activity.home.ChatActivity
import com.vihmessenger.vihchatbot.utils.NetworkConnectivityManager
import com.vihmessenger.vihchatbot.viewmodel.HomeViewModel
import com.vihmessenger.vihchatbot.viewmodel.HomeViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DiscoverFragment : BaseFragment() {
    companion object {
        fun getInstance(hashCode: String) = DiscoverFragment().apply {
            arguments = Bundle().apply {
                putString(AppConstants.HASHCODE_EXTRA, hashCode)
            }
        }
    }

    private var _viewBinder: FragmentDiscoverBinding? = null
    private val viewBinder get() = _viewBinder!!

    private lateinit var discoverListAdapter: DiscoverListAdapter
    private var homeViewModel: HomeViewModel? = null

    private var isLoading = false
    private var currentPage = 1
    private var isLastPage = false
    private var currentSearchQuery = ""
    private var currentFilterStr = ""
    private var pendingFilter: String? = null
    private var pendingSearchData: String? = null


    private var isInitialized = false
    private var isNoResultsState = false

    private lateinit var networkConnectivityManager: NetworkConnectivityManager

    // For the delayed loader
    private val handler = Handler(Looper.getMainLooper())
    private var hideLoaderRunnable: Runnable? = null


    override fun initViewModels() {
        homeViewModel = getFragmentViewModel(
            this, HomeViewModel(requireActivity() as BaseActivity), HomeViewModel::class.java
        )
    }

    override fun onViewClick(view: View?) {}

    override fun initView(view: View) {
//        processPendingData()
        with(viewBinder) {
            srlChatList.apply {
                setOnRefreshListener {
                    isRefreshing = false
                    isNoResultsState = false
                    viewBinder.noInternet.root.visibility = View.GONE
                    viewBinder.comingSoon.root.visibility = View.GONE
                    resetPagination()
                }
                setColorSchemeColors(Color.parseColor("#0049E6"), Color.parseColor("#9C15F7"))
            }

            idNestedSV.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                if (!isLoading && !isLastPage && scrollY == idNestedSV.getChildAt(0).measuredHeight - idNestedSV.measuredHeight) {
                    // Show pagination loader before loading next page
                    showPaginationLoader(true)
                    getDiscoverList(++currentPage, currentSearchQuery, currentFilterStr)
                }
            }

            llRecylerview.visibility = View.GONE
            noInternet.root.visibility = View.GONE
            comingSoon.root.visibility = View.GONE
            paginationLoader.visibility = View.GONE // Hide pagination loader initially
        }

        // Get the saved filter from ViewModel
        val savedFilter = homeViewModel?.getSelectedIndustries() ?: ""
        if (savedFilter.isNotEmpty()) {
            currentFilterStr = savedFilter
        }

        // Initial load with saved filter
        getDiscoverList(1, "", currentFilterStr)
        isInitialized = true
    }

    override fun setListeners() {}

    override fun setObservers() {
        homeViewModel?.enterprisesDiscoverListLiveData?.observe(viewLifecycleOwner) { response ->
            isLoading = false
            // Hide pagination loader when data is received
            showPaginationLoader(false)

            // Only process if network is available
            if (!networkConnectivityManager.isConnectedFlow.value && currentPage == 1) {
                viewBinder.llRecylerview.visibility = View.GONE
                isNoResultsState = false
                return@observe
            }

            response?.data?.let { data ->
                if (currentPage == 1) { // For new search or refresh
                    if (!::discoverListAdapter.isInitialized) {
                        setRecyclerView()
                    }
                    discoverListAdapter.clearDiscoverList()

                    if (data.isEmpty()) {
                        // Data is empty: "NO RECORDS FOUND" case
                        isNoResultsState = true
                        viewBinder.comingSoon.root.visibility = View.VISIBLE
                        viewBinder.noInternet.root.visibility = View.GONE
                        viewBinder.llRecylerview.visibility = View.GONE
                        isLastPage = true
                    } else {
                        // Data found
                        isNoResultsState = false
                        viewBinder.comingSoon.root.visibility = View.GONE
                        viewBinder.noInternet.root.visibility = View.GONE
                        viewBinder.llRecylerview.visibility = View.VISIBLE
                        discoverListAdapter.addDiscoverList(data)
                        isLastPage = false
                    }
                } else { // For pagination
                    // If we are paginating, it means initial results were found
                    isNoResultsState = false
                    viewBinder.comingSoon.root.visibility = View.GONE
                    viewBinder.noInternet.root.visibility = View.GONE
                    viewBinder.llRecylerview.visibility = View.VISIBLE
                    if (data.isNotEmpty()) {
                        discoverListAdapter.addDiscoverList(data)
                    } else {
                        isLastPage = true // No more items to paginate
                    }
                }
            } ?: run { // Response or response.data is null
                if (currentPage == 1) {
                    // Treat null response on first page as "no results" or error
                    isNoResultsState = true
                    if (!::discoverListAdapter.isInitialized) setRecyclerView()
                    discoverListAdapter.clearDiscoverList()
                    viewBinder.comingSoon.root.visibility = View.VISIBLE
                    viewBinder.noInternet.root.visibility = View.GONE
                    viewBinder.llRecylerview.visibility = View.GONE
                }
                isLastPage = true
            }
        }

        homeViewModel?.errorListLiveData?.observe(viewLifecycleOwner) {
            isLoading = false
            // Hide pagination loader on error
            showPaginationLoader(false)

            if (!networkConnectivityManager.isConnectedFlow.value && currentPage == 1) {
                viewBinder.llRecylerview.visibility = View.GONE
                isNoResultsState = false
                return@observe
            }

            // Handle error when network IS available. Genuine offline is handled by the
            // networkConnectivityManager collector below, so reaching here means the
            // device is online but the request failed (server error / timeout / 404).
            // BaseRepository already toasts the real reason — don't masquerade it as the
            // "No Internet" screen; show the neutral empty state instead.
            if (currentPage == 1) {
                if (!::discoverListAdapter.isInitialized || discoverListAdapter.itemCount == 0) {
                    isNoResultsState = true
                    viewBinder.noInternet.root.visibility = View.GONE
                    viewBinder.comingSoon.root.visibility = View.VISIBLE
                    viewBinder.llRecylerview.visibility = View.GONE
                }
            }
            // For pagination errors, you might show a Toast or revert the current page
            if (currentPage > 1) {
                currentPage-- // Revert page increment on error
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            networkConnectivityManager.isConnectedFlow.collectLatest { available ->
                if (!available) {
                    isLoading = false
                    showPaginationLoader(false) // Hide pagination loader on network loss
                    viewBinder.noInternet.root.visibility = View.VISIBLE
                    viewBinder.comingSoon.root.visibility = View.GONE
                    viewBinder.srlChatList.visibility = View.GONE
                    isNoResultsState = false
                } else {
                    // Network is now available
                    viewBinder.srlChatList.visibility = View.VISIBLE

                    if (isNoResultsState) {
                        viewBinder.comingSoon.root.visibility = View.VISIBLE
                        viewBinder.noInternet.root.visibility = View.GONE
                        viewBinder.llRecylerview.visibility = View.GONE
                    } else {
                        viewBinder.noInternet.root.visibility = View.GONE
                        viewBinder.comingSoon.root.visibility = View.GONE
                        if (isInitialized && (!::discoverListAdapter.isInitialized || discoverListAdapter.itemCount == 0)) {
                            viewBinder.llRecylerview.visibility = View.GONE
                            resetPagination()
                        } else if (::discoverListAdapter.isInitialized && discoverListAdapter.itemCount > 0) {
                            viewBinder.llRecylerview.visibility = View.VISIBLE
                        } else {
                            viewBinder.llRecylerview.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    /**
     * Show or hide the pagination loader with a minimum visibility duration.
     */
    private fun showPaginationLoader(show: Boolean) {
        // Check if view is available before accessing viewBinder
        if (_viewBinder != null) {
            if (show) {
                // If there's a pending task to hide the loader, cancel it
                hideLoaderRunnable?.let { handler.removeCallbacks(it) }
                viewBinder.paginationLoader.visibility = View.VISIBLE
            } else {
                // When we want to hide the loader, post a delayed task
                hideLoaderRunnable = Runnable {
                    if (_viewBinder != null) { // Re-check view availability
                        viewBinder.paginationLoader.visibility = View.GONE
                    }
                }
                handler.postDelayed(hideLoaderRunnable!!, 1500) // 500ms delay
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
        // Apply theme colors to pagination loader text only if view is available
//        if (_viewBinder != null) {
//            viewBinder.tvLoadingText.setTextColor(defaultTextColor)
//        }

        if (_viewBinder!=null){
            _viewBinder!!.comingSoon.txtComingSoon.setTextColor(primaryColor)
            _viewBinder!!.comingSoon.tvToolBarMain.setTextColor(defaultTextColor)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeViewModel = ViewModelProvider(
            requireActivity(),
            HomeViewModelFactory()
        ).get(HomeViewModel::class.java)

        networkConnectivityManager =
            (requireActivity().application as AppController).networkConnectivityManager
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _viewBinder = FragmentDiscoverBinding.inflate(inflater, container, false)
        return viewBinder.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Remove any pending callbacks to avoid memory leaks
        hideLoaderRunnable?.let { handler.removeCallbacks(it) }
        _viewBinder = null
    }


    private fun getDiscoverList(page: Int, searchQuery: String = "", filterStr: String = "") {
        isLoading = true
        arguments?.getString(AppConstants.HASHCODE_EXTRA)?.let {
            homeViewModel?.getEnterpriseDiscoverListResponse(
                false,
                it,
                page,
                searchQuery,
                filterStr
            )
        }
    }

    private fun searchDiscoverList(searchQuery: String, filterStr: String) {
        // Reset pagination for new search/filter
        currentPage = 1
        isLastPage = false
        isNoResultsState = false
        currentSearchQuery = searchQuery
        currentFilterStr = filterStr

        if (::discoverListAdapter.isInitialized) {
            discoverListAdapter.clearDiscoverList()
        }
        viewBinder.llRecylerview.visibility = View.GONE
        viewBinder.noInternet.root.visibility = View.GONE
        viewBinder.comingSoon.root.visibility = View.GONE
        showPaginationLoader(false) // Hide pagination loader for new search
        getDiscoverList(currentPage, searchQuery, filterStr)
    }

    private fun setRecyclerView() {
        discoverListAdapter = DiscoverListAdapter(requireContext()).apply {
            setOnDiscoverRvItemClickListener(object : OnDiscoverRvItemClickListener {
                override fun onChatClick(position: Int, model: ChatListModel) {}
                override fun onStartChatClick(position: Int, model: EnterPriseModel) {
                    ChatActivity.startIntent(
                        requireContext(),
                        "",
                        channel_name = model.resolvedDisplayName,
                        channel_image = model.resolvedLogoUrl,
                        model,
                        model.user_id,
                        arguments?.getString(AppConstants.HASHCODE_EXTRA)
                    )
                }
            })
        }
        viewBinder.rvDiscoverList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = discoverListAdapter
        }
    }

    private fun resetPagination() {
        currentPage = 1
        isLastPage = false
        isNoResultsState = false

        if (!::discoverListAdapter.isInitialized) {
            return
        }
        discoverListAdapter.clearDiscoverList()
        viewBinder.llRecylerview.visibility = View.GONE
        viewBinder.noInternet.root.visibility = View.GONE
        viewBinder.comingSoon.root.visibility = View.GONE
        showPaginationLoader(false) // Hide pagination loader on reset

        getDiscoverList(currentPage, currentSearchQuery, currentFilterStr)
    }

    fun receiveData(data: String) {
        // Only proceed if view is available
        if (_viewBinder != null) {
            searchDiscoverList(data, currentFilterStr)
        }
    }

    fun receiveIndustryData(filter: String) {
        // Only proceed if view is available
        if (_viewBinder != null) {
            currentPage = 1
            isLastPage = false
            currentFilterStr = filter

            if (::discoverListAdapter.isInitialized) {
                discoverListAdapter.clearDiscoverList()
            }
            viewBinder.comingSoon.root.visibility = View.GONE
            showPaginationLoader(false) // Hide pagination loader for new filter
            getDiscoverList(currentPage, currentSearchQuery, currentFilterStr)
        }else {
            // View not available, store for later
            pendingFilter = filter
        }
    }
    private fun processPendingData() {
        pendingFilter?.let { filter ->
            receiveIndustryData(filter)
            pendingFilter = null
        }
        pendingSearchData?.let { data ->
            receiveData(data)
            pendingSearchData = null
        }
    }
}