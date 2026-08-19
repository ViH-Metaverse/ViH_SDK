package com.vihmessenger.vihchatbot.discover

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.vihmessenger.vihchatbot.AppController
import com.vihmessenger.vihchatbot.api.services.ApiClient
import com.vihmessenger.vihchatbot.config.VihConfig
import com.vihmessenger.vihchatbot.config.VihConfigStore
import com.vihmessenger.vihchatbot.config.VihNavigation
import com.vihmessenger.vihchatbot.config.VihTab
import com.vihmessenger.vihchatbot.config.VihTabId
import com.vihmessenger.vihchatbot.constants.AppConstants
import com.vihmessenger.vihchatbot.data.model.EnterPriseModel
import com.vihmessenger.vihchatbot.data.model.UserProfileRequest
import com.vihmessenger.vihchatbot.services.DeviceTokenRegistrar
import com.vihmessenger.vihchatbot.ui.activity.home.ChatActivity
import com.vihmessenger.vihchatbot.utils.FloatingButtonView
import com.vihmessenger.vihchatbot.utils.sharedPreference.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable

/**
 * Public facade for **custom / headless Discover integration** — for hosts that render the
 * Discover enterprise list in their **own** native UI and deep-link straight into a specific
 * channel's chat, with the SDK's built-in Discover tab hidden.
 *
 * The three moving parts:
 *  1. [prepareSession] — establish an authenticated session (passwordless phone sign-in) so the
 *     list + chat endpoints carry a Bearer token, *before* any SDK screen is shown.
 *  2. [listEnterprises] — fetch the same enterprise list the Discover tab shows, as a flat
 *     [VihEnterprise] list the host can render however it likes (paged/searchable/filterable).
 *  3. [openChat] — launch the SDK's chat screen for one enterprise (the "button of their choice"
 *     on each row).
 *
 * The SDK keeps its standard **Discover · Chats · Settings** tabs — this facade only adds the
 * host-side enterprise list + chat deep-link; it never changes the SDK's own UI.
 *
 * All async work is delivered back on the **main thread**. Nothing here requires a custom SDK
 * build — it wraps the already-shipped networking, session and chat surfaces.
 */
object VihDiscover {

    /** Async result callback. Both methods are invoked on the main thread. */
    interface Callback<T> {
        fun onSuccess(result: T)
        fun onError(error: Throwable)
    }

    /**
     * A Discover enterprise/channel, flattened to the fields a host list UI needs. Use
     * [enterpriseId] as the id to pass to [openChat]. [raw] is the full underlying model
     * (escape hatch for fields not surfaced here).
     */
    data class VihEnterprise(
        /** The chat/enterprise id — `EnterPriseModel.user_id`. Pass this to [openChat]. */
        val enterpriseId: String,
        val name: String,
        val logoUrl: String?,
        val category: String,
        val industry: String,
        val description: String?,
        val raw: EnterPriseModel,
        /**
         * The channel owner has blacklisted this enterprise on the current channel. Such
         * enterprises are *not* returned by [listEnterprises] — this flag is only ever `true`
         * on models obtained through [listEnterprisesPage] with `includeBlacklisted = true`.
         */
        val isBlacklistedByChannel: Boolean = false,
        /**
         * This user has blocked the enterprise (Block from the chat / company profile screen).
         * Blocked enterprises are hidden from [listEnterprises] too — same escape hatch as
         * [isBlacklistedByChannel] if you want to see them.
         */
        val isBlacklistedByUser: Boolean = false,
    ) : Serializable {
        companion object {
            @JvmStatic
            fun from(model: EnterPriseModel): VihEnterprise = VihEnterprise(
                enterpriseId = model.user_id,
                name = model.resolvedDisplayName,
                logoUrl = model.resolvedLogoUrl,
                category = model.category,
                industry = model.industry,
                description = model.displayNameModel?.description
                    ?: model.displayNameModel?.display_msg,
                raw = model,
                isBlacklistedByChannel = model.is_blacklisted_by_channel == true,
                isBlacklistedByUser = model.is_blacklisted_by_user == true,
            )
        }
    }

    /**
     * One page of the Discover list. [enterprises] holds only the enterprises that are **active
     * on the channel** — both channel-blacklisted and user-blocked ones are dropped — while
     * [hasMore] reports whether the *backend* returned anything at all for this page. Drive
     * pagination off [hasMore], never off `enterprises.isEmpty()`, because a page whose entries
     * are all blacklisted filters down to nothing while more pages still follow.
     */
    data class EnterprisePage(
        val enterprises: List<VihEnterprise>,
        val hasMore: Boolean,
        /** Number of enterprises the backend returned for this page, before filtering. */
        val rawCount: Int,
    ) : Serializable

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val gson = Gson()

    /**
     * Models handed out by [listEnterprises], keyed by `user_id` — the same value the host gets
     * as [VihEnterprise.enterpriseId] and passes back to [openChat].
     *
     * [ChatActivity] needs the full model. When it is not given one it falls back to
     * `main/enterprise-details/`, which is keyed by `EnterPriseModel.id` — a *different* number
     * (BSNL is `id=29`, `user_id=10132`). Passing the `user_id` there returns
     * `{"data":"Enterprise not found","status":false}`, which surfaced to users as a
     * `java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING` toast. Reusing the
     * model the host already fetched avoids the mismatched lookup entirely.
     */
    private val knownEnterprises = java.util.concurrent.ConcurrentHashMap<String, EnterPriseModel>()

    /**
     * Establish an authenticated session for [phone] on channel [hashcode] via the SDK's
     * passwordless phone sign-in (`account/signup-login/`), persisting the resulting tokens.
     * Call this once (e.g. when your Discover screen opens) so [listEnterprises] and [openChat]
     * are authenticated even before any SDK UI has run.
     *
     * Switching to a different channel resets the previous channel's stale session first.
     * [phone] must be digits only, with country code and no `+` (e.g. `"919876543210"`).
     */
    @JvmStatic
    @JvmOverloads
    fun prepareSession(
        context: Context,
        phone: String,
        hashcode: String,
        callback: Callback<Unit>? = null,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                // Prefs is backed by EncryptedSharedPreferences, whose first construction goes
                // through the Android Keystore. That is slow enough to stutter — and on some
                // emulator images slow enough to stall — so it must not run on the main
                // thread, which is where this coroutine's scope dispatches.
                val response = withContext(Dispatchers.IO) {
                    // The host owns the Application here, so AppController.onCreate never ran.
                    // Everything downstream (Retrofit, the 401 authenticator, the chat screens)
                    // reads AppController's companion state, so bring it up first — off the main
                    // thread, since it constructs Prefs.
                    AppController.ensureInitialized(appContext)
                    val prefs = Prefs.getInstance(appContext)
                    // Reset stale session state if this is a switch to a different channel, then
                    // mark SDK mode and remember the phone — mirrors FloatingButtonView.startSdk.
                    prefs.switchChannel(hashcode)
                    prefs.isSDK = true
                    // The host renders its own Discover UI and owns navigation, so an
                    // unrecoverable expiry must not relaunch it. See BaseRepository.
                    prefs.isHostDriven = true
                    prefs.phoneNumber = phone

                    // prepareSession performs a *fresh* sign-in, so the login request must go out
                    // unauthenticated. On the same channel switchChannel keeps the old session; if
                    // that token is expired, AuthInterceptor would attach it to signup-login and the
                    // server rejects it with 401 token_not_valid. Drop it first.
                    prefs.accessToken = null
                    prefs.refreshToken = null

                    ApiClient.apiService.createUserProfile(
                        UserProfileRequest(phone, hashcode, prefs.fcmToken ?: "")
                    )
                }
                val body = response.body()
                if (response.isSuccessful && body != null && body.status) {
                    withContext(Dispatchers.IO) {
                        val prefs = Prefs.getInstance(appContext)
                        prefs.userProfile = gson.toJson(body.data.user)
                        prefs.accessToken = body.data.access_token
                        prefs.refreshToken = body.data.refresh
                        // Register for push now that there is a session. Without this the
                        // backend has no token for this device and nothing that is delivered
                        // only by push — a Flow Builder response, most visibly — ever arrives.
                        DeviceTokenRegistrar.registerCachedTokenIfNeeded(appContext)
                    }
                    callback?.onSuccess(Unit)
                } else {
                    val msg = body?.message?.takeIf { it.isNotBlank() }
                        ?: "Sign-in failed (HTTP ${response.code()})"
                    callback?.onError(IllegalStateException(msg))
                }
            } catch (t: Throwable) {
                callback?.onError(t)
            }
        }
    }

    /**
     * Fetch the Discover enterprise list for channel [hashcode] — the same data the built-in
     * Discover tab shows, as a flat [VihEnterprise] list for the host to render. The endpoint is
     * paged: request [page] 1, 2, … and append until the backend runs out. [search] and
     * [industries] (comma-separated) narrow the results server-side.
     *
     * Only enterprises **active on this channel** are returned. The backend still sends the
     * blacklisted ones — `is_blacklisted_by_channel` (the channel owner blocked the enterprise
     * on this channel; sends rejected with 2001) and `is_blacklisted_by_user` (this user blocked
     * it; sends rejected with 2003) — so both are filtered out here. Because of that a page can
     * come back empty while further pages still hold visible results — use [listEnterprisesPage]
     * if you paginate yourself, and stop on `hasMore == false`.
     *
     * Requires a session — call [prepareSession] (or launch the SDK once) first.
     */
    @JvmStatic
    @JvmOverloads
    fun listEnterprises(
        hashcode: String,
        page: Int = 1,
        search: String = "",
        industries: String = "",
        callback: Callback<List<VihEnterprise>>,
    ) = listEnterprisesPage(
        hashcode, page, search, industries,
        callback = object : Callback<EnterprisePage> {
            override fun onSuccess(result: EnterprisePage) = callback.onSuccess(result.enterprises)
            override fun onError(error: Throwable) = callback.onError(error)
        }
    )

    /**
     * Paging-aware variant of [listEnterprises]: delivers the channel-active enterprises for
     * [page] together with a [EnterprisePage.hasMore] flag so a fully-blacklisted page doesn't
     * read as the end of the list. Pass [includeBlacklisted] `true` to skip the filter and get
     * the raw backend page (each item then carries [VihEnterprise.isBlacklistedByChannel] and
     * [VihEnterprise.isBlacklistedByUser] so you can render them however you like).
     *
     * Requires a session — call [prepareSession] (or launch the SDK once) first.
     */
    @JvmStatic
    @JvmOverloads
    fun listEnterprisesPage(
        hashcode: String,
        page: Int = 1,
        search: String = "",
        industries: String = "",
        includeBlacklisted: Boolean = false,
        callback: Callback<EnterprisePage>,
    ) {
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.getEnterpriseDiscoverListResponse(
                        hashcode, page, search, industries
                    )
                }
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    val raw = body.data
                    raw.forEach { knownEnterprises[it.user_id] = it }
                    // Hide anything the user can't actually talk to: blacklisted by the channel
                    // owner (send rejected with 2001) or blocked by this user (2003). The flags
                    // are nullable server-side, so compare with `== true`.
                    val visible = if (includeBlacklisted) raw else raw.filterNot {
                        it.is_blacklisted_by_channel == true || it.is_blacklisted_by_user == true
                    }
                    callback.onSuccess(
                        EnterprisePage(
                            enterprises = visible.map { VihEnterprise.from(it) },
                            // The backend paginates the UNFILTERED set, so "there may be more
                            // pages" is decided by the raw page, not by what survived the filter.
                            hasMore = raw.isNotEmpty(),
                            rawCount = raw.size,
                        )
                    )
                } else {
                    callback.onError(
                        IllegalStateException("Failed to load enterprises (HTTP ${response.code()})")
                    )
                }
            } catch (t: Throwable) {
                callback.onError(t)
            }
        }
    }

    private const val DASHBOARD_ACTIVITY =
        "com.vihmessenger.vihchatbot.ui.activity.home.DashBoardActivity"

    /**
     * Open the SDK's chat screen for one enterprise on channel [hashcode]. [enterpriseId] is the
     * [VihEnterprise.enterpriseId] (the enterprise's `user_id`); [name] / [logoUrl] pre-fill the
     * header (optional — the screen self-hydrates from [enterpriseId] if omitted). Pass the full
     * [enterprise] model to skip the extra details fetch.
     *
     * With [landOnDashboard] `true` (default), the SDK's dashboard (the usual **Discover · Chats ·
     * Settings** tabs) is placed underneath the chat, so backing out of the chat lands on the
     * SDK's main page — then backing out again returns to your app. This needs the phone from
     * [prepareSession]; if it's missing, the chat opens standalone (back returns to your list).
     * Set [landOnDashboard] `false` to always open the chat standalone.
     *
     * Requires a session — call [prepareSession] first (otherwise history/sending will fail auth).
     */
    @JvmStatic
    @JvmOverloads
    fun openChat(
        context: Context,
        hashcode: String,
        enterpriseId: String,
        name: String? = null,
        logoUrl: String? = null,
        enterprise: EnterPriseModel? = null,
        landOnDashboard: Boolean = true,
    ) {
        AppController.ensureInitialized(context)
        val prefs = Prefs.getInstance(context.applicationContext)
        if (prefs.hashcode.isNullOrBlank()) prefs.hashcode = hashcode

        val phone = prefs.phoneNumber
        if (landOnDashboard && !phone.isNullOrBlank()) {
            // Land on the standard SDK dashboard (Discover · Chats · Settings) unless the host has
            // already configured its own tab set. `copy` rather than a fresh VihConfig: building
            // one from scratch silently reset every field it did not name, so a host that had set
            // `security` (FLAG_SECURE) or `diagnostics` lost it the moment it called openChat.
            val existing = VihConfigStore.config
            if (existing?.navigation == null) {
                val navigation = VihNavigation(
                    tabs = listOf(
                        VihTab(VihTabId.DISCOVER),
                        VihTab(VihTabId.CHATS),
                        VihTab(VihTabId.SETTINGS)
                    )
                )
                VihConfigStore.set(
                    existing?.copy(navigation = navigation) ?: VihConfig(navigation = navigation)
                )
            }
            // Push the dashboard first so it sits under the chat in the back stack.
            context.startActivity(
                Intent().apply {
                    setClassName(context, DASHBOARD_ACTIVITY)
                    putExtra(AppConstants.HASHCODE_EXTRA, hashcode)
                    putExtra(AppConstants.PHONENUMBER, phone)
                    // See ChatActivity.startIntent — a non-Activity context needs NEW_TASK.
                    if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
        // Prefer whatever the caller supplied; otherwise reuse the model from listEnterprises so
        // ChatActivity does not have to look it up by the wrong key. See [knownEnterprises].
        val model = enterprise ?: knownEnterprises[enterpriseId]
        ChatActivity.startIntent(context, "", name, logoUrl, model, enterpriseId, hashcode)
    }

    /** Convenience [openChat] overload taking a [VihEnterprise]. */
    @JvmStatic
    @JvmOverloads
    fun openChat(
        context: Context,
        hashcode: String,
        enterprise: VihEnterprise,
        landOnDashboard: Boolean = true,
    ) {
        openChat(
            context, hashcode, enterprise.enterpriseId,
            enterprise.name, enterprise.logoUrl, enterprise.raw, landOnDashboard
        )
    }
}
