import UIKit

/// A Discover enterprise/channel, flattened to the fields a host list UI needs. Use
/// ``enterpriseId`` as the id to pass to `VihDiscover.openChat`. ``raw`` is the full underlying
/// model (escape hatch for fields not surfaced here).
public struct VihEnterprise {
    /// The chat/enterprise id — `EnterPriseModel.user_id`. Pass this to `VihDiscover.openChat`.
    public let enterpriseId: String
    public let name: String
    public let logoUrl: String?
    public let category: String
    public let industry: String
    public let description: String?
    public let raw: EnterPriseModel
    /// The channel owner has blacklisted this enterprise on the current channel. Such
    /// enterprises are *not* returned by `VihDiscover.listEnterprises` — this is only ever
    /// `true` on models fetched with `includeBlacklisted: true`.
    public let isBlacklistedByChannel: Bool
    /// This user has blocked the enterprise (Block from the chat / company profile screen).
    /// Blocked enterprises are hidden from `VihDiscover.listEnterprises` too — same escape hatch
    /// as ``isBlacklistedByChannel`` if you want to see them.
    public let isBlacklistedByUser: Bool

    init(_ m: EnterPriseModel) {
        enterpriseId = m.user_id
        // Prefer the resolved display name (raw_cpaas_json / enterprise_* fallbacks are folded
        // into displayNameModel at decode time), then comp_name.
        let display = m.displayNameModel?.display_name
        name = (display?.isEmpty == false ? display : nil) ?? m.comp_name
        logoUrl = [m.display_img, m.profile_picture, m.enterprise_logo, m.enterprise_display_img]
            .compactMap { $0 }
            .first { !$0.isEmpty }
        category = m.category
        industry = m.industry
        description = m.displayNameModel?.description ?? m.displayNameModel?.display_msg
        raw = m
        isBlacklistedByChannel = m.isBlacklistedByChannel == true
        isBlacklistedByUser = m.isBlacklistedByUser == true
    }
}

/// One page of the Discover list. ``enterprises`` holds only the enterprises **active on the
/// channel** — both channel-blacklisted and user-blocked ones are dropped — while ``hasMore``
/// reports whether the *backend* returned anything at all for this page. Drive pagination off
/// ``hasMore``, never off `enterprises.isEmpty`, because a page whose entries are all blacklisted
/// filters down to nothing while more pages still follow.
public struct EnterprisePage {
    public let enterprises: [VihEnterprise]
    public let hasMore: Bool
    /// Number of enterprises the backend returned for this page, before filtering.
    public let rawCount: Int
}

/// Public facade for **custom / headless Discover integration** — for hosts that render the
/// Discover enterprise list in their **own** native UI and deep-link straight into a specific
/// channel's chat, with the SDK's built-in Discover tab hidden. iOS mirror of Android's
/// `com.vihmessenger.vihchatbot.discover.VihDiscover`.
///
/// The three moving parts:
/// 1. ``prepareSession(phone:hashcode:)`` — establish an authenticated session (passwordless
///    phone sign-in) so the list + chat endpoints carry a Bearer token, before any SDK screen.
/// 2. ``listEnterprises(hashcode:page:search:industries:)`` — fetch the same enterprise list the
///    Discover tab shows, as a flat `VihEnterprise` list the host renders however it likes.
/// 3. ``openChat(from:hashcode:enterpriseId:name:logoUrl:enterprise:animated:)`` — push the SDK's
///    chat screen for one enterprise (the "button of their choice" on each row).
///
/// The SDK keeps its standard Discover · Chats · Settings tabs — this facade only adds the
/// host-side enterprise list + chat deep-link; it never changes the SDK's own UI.
///
/// Both `async` and completion-handler forms are provided. Nothing here requires a custom SDK
/// build — it wraps the already-shipped networking, session and chat surfaces.
public enum VihDiscover {

    // MARK: - Session bootstrap

    /// Establish an authenticated session for `phone` on channel `hashcode` via the SDK's
    /// passwordless phone sign-in (`account/signup-login/`), persisting the resulting tokens.
    /// Call once (e.g. when your Discover screen opens) so `listEnterprises` and `openChat` are
    /// authenticated even before any SDK UI has run. Switching channels resets the previous
    /// channel's stale session first. `phone` must be digits only, country code, no `+`.
    public static func prepareSession(phone: String, hashcode: String) async throws {
        let prefs = Prefs.shared
        // Reset stale session state if this is a switch to a different channel (mirror of
        // Android's Prefs.switchChannel), then mark SDK mode and remember the phone.
        if let previous = prefs.hashcode, !previous.isEmpty, previous != hashcode {
            prefs.clearAllPreferences()
        }
        prefs.hashcode = hashcode
        prefs.isSDK = true
        prefs.phoneNumber = phone

        // prepareSession performs a *fresh* sign-in, so the login request must go out
        // unauthenticated. On the same channel the old session is kept above; if that token is
        // expired, AuthInterceptor would attach it to signup-login and the server rejects it
        // with 401 token_not_valid. Drop it first.
        prefs.accessToken = nil
        prefs.refreshToken = nil

        let response = try await APIClient.shared.apiService.createUserProfile(
            body: UserProfileRequest(
                mobile_number: phone, hash_code: hashcode, fcm_token: prefs.fcmToken ?? ""
            )
        )
        guard response.status else {
            throw APIError(response.message.isEmpty ? "Sign-in failed" : response.message)
        }
        if let json = try? JSONEncoder().encode(response.data.user) {
            prefs.userProfile = String(data: json, encoding: .utf8)
        }
        prefs.accessToken = response.data.access_token
        prefs.refreshToken = response.data.refresh
    }

    /// Completion-handler variant of ``prepareSession(phone:hashcode:)``. Result is delivered on
    /// the main thread.
    public static func prepareSession(
        phone: String,
        hashcode: String,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        Task {
            do {
                try await prepareSession(phone: phone, hashcode: hashcode)
                await MainActor.run { completion(.success(())) }
            } catch {
                await MainActor.run { completion(.failure(error)) }
            }
        }
    }

    // MARK: - Enterprise list

    /// Fetch the Discover enterprise list for channel `hashcode` — the same data the built-in
    /// Discover tab shows, as a flat `VihEnterprise` list. The endpoint is paged: request page
    /// 1, 2, … and append until the backend runs out. `search` and `industries`
    /// (comma-separated) narrow the results server-side. Requires a session (see
    /// ``prepareSession(phone:hashcode:)``).
    ///
    /// Only enterprises **active on this channel** are returned. The backend still sends the
    /// blacklisted ones — `is_blacklisted_by_channel` (the channel owner blocked the enterprise
    /// on this channel; sends rejected with 2001) and `is_blacklisted_by_user` (this user blocked
    /// it; sends rejected with 2003) — so both are filtered out here. Because of that a page can
    /// come back empty while further pages still hold visible results — if you paginate yourself
    /// use ``listEnterprisesPage(hashcode:page:search:industries:includeBlacklisted:)`` and stop
    /// on `hasMore == false`.
    public static func listEnterprises(
        hashcode: String,
        page: Int = 1,
        search: String = "",
        industries: String = ""
    ) async throws -> [VihEnterprise] {
        try await listEnterprisesPage(
            hashcode: hashcode, page: page, search: search, industries: industries
        ).enterprises
    }

    /// Paging-aware variant of ``listEnterprises(hashcode:page:search:industries:)``: returns the
    /// channel-active enterprises for `page` together with a `hasMore` flag, so a fully
    /// blacklisted page doesn't read as the end of the list. Pass `includeBlacklisted: true` to
    /// skip the filter and get the raw backend page (each item then carries
    /// ``VihEnterprise/isBlacklistedByChannel`` and ``VihEnterprise/isBlacklistedByUser`` so you
    /// can render them however you like).
    public static func listEnterprisesPage(
        hashcode: String,
        page: Int = 1,
        search: String = "",
        industries: String = "",
        includeBlacklisted: Bool = false
    ) async throws -> EnterprisePage {
        let response = try await APIClient.shared.apiService.getEnterpriseDiscoverList(
            hashcode: hashcode, page: page, search: search, industries: industries
        )
        let raw = response.data
        // Hide anything the user can't actually talk to: blacklisted by the channel owner (send
        // rejected with 2001) or blocked by this user (2003). The flags are nullable server-side.
        let visible = includeBlacklisted ? raw : raw.filter {
            $0.isBlacklistedByChannel != true && $0.isBlacklistedByUser != true
        }
        return EnterprisePage(
            enterprises: visible.map { VihEnterprise($0) },
            // The backend paginates the UNFILTERED set, so "there may be more pages" is decided
            // by the raw page, not by what survived the filter.
            hasMore: !raw.isEmpty,
            rawCount: raw.count
        )
    }

    /// Completion-handler variant of ``listEnterprises(hashcode:page:search:industries:)``.
    /// Result is delivered on the main thread.
    public static func listEnterprises(
        hashcode: String,
        page: Int = 1,
        search: String = "",
        industries: String = "",
        completion: @escaping (Result<[VihEnterprise], Error>) -> Void
    ) {
        Task {
            do {
                let result = try await listEnterprises(
                    hashcode: hashcode, page: page, search: search, industries: industries
                )
                await MainActor.run { completion(.success(result)) }
            } catch {
                await MainActor.run { completion(.failure(error)) }
            }
        }
    }

    // MARK: - Deep-link into a channel's chat

    /// Open the SDK's chat screen for one enterprise on channel `hashcode`. `enterpriseId` is the
    /// `VihEnterprise.enterpriseId` (the enterprise's `user_id`); `name` / `logoUrl` pre-fill the
    /// header (optional — the screen self-hydrates from `enterpriseId` if omitted). Pass the full
    /// `enterprise` model to skip the extra details fetch. Requires a session (see
    /// ``prepareSession(phone:hashcode:)``).
    ///
    /// - With `landOnDashboard: false` (the iOS default): the chat is pushed onto `presenter`'s
    ///   navigation controller (or presented modally if it has none), so backing out returns to
    ///   your list. This is the safe, standalone behavior.
    /// - With `landOnDashboard: true`: the SDK's dashboard (the usual Discover · Chats · Settings
    ///   tabs) is presented full-screen and the chat is pushed on top of its first tab, so backing
    ///   out of the chat lands on the SDK's main page (matching the Android default). Your app is
    ///   then responsible for dismissing that presented dashboard to return to the host — as it is
    ///   whenever the SDK dashboard is the entry surface.
    ///
    /// > Note: the iOS default is `false` (the Android default is `true`). iOS navigation is a
    /// > `UITabBarController`, not an activity stack, so the land-on-dashboard path presents a
    /// > modal the host must dismiss; it is opt-in here and should be smoke-tested in your app.
    ///
    /// Call on the main thread.
    @discardableResult
    @MainActor
    public static func openChat(
        from presenter: UIViewController,
        hashcode: String,
        enterpriseId: String,
        name: String? = nil,
        logoUrl: String? = nil,
        enterprise: EnterPriseModel? = nil,
        landOnDashboard: Bool = false,
        animated: Bool = true
    ) -> ChatViewController {
        let prefs = Prefs.shared
        if (prefs.hashcode ?? "").isEmpty { prefs.hashcode = hashcode }

        let chat = ChatViewController(inputs: ChatViewController.Inputs(
            sessionId: "",
            channelName: name,
            channelImage: logoUrl,
            channel: enterprise,
            id: enterpriseId,
            hashcode: hashcode
        ))

        if landOnDashboard {
            // Present the standard SDK dashboard (Discover · Chats · Settings — the host's uiConfig
            // is honored if it set one) and push the chat onto the selected tab so back → dashboard.
            let dashboard = DashboardViewController()
            dashboard.modalPresentationStyle = .fullScreen
            presenter.present(dashboard, animated: animated) {
                if let nav = dashboard.selectedViewController as? UINavigationController {
                    nav.pushViewController(chat, animated: true)
                } else {
                    dashboard.present(
                        UINavigationController(rootViewController: chat), animated: true
                    )
                }
            }
            return chat
        }

        if let nav = presenter.navigationController {
            nav.pushViewController(chat, animated: animated)
        } else {
            let nav = UINavigationController(rootViewController: chat)
            nav.modalPresentationStyle = .fullScreen
            presenter.present(nav, animated: animated)
        }
        return chat
    }

    /// Convenience ``openChat`` taking a `VihEnterprise`.
    @discardableResult
    @MainActor
    public static func openChat(
        from presenter: UIViewController,
        hashcode: String,
        enterprise: VihEnterprise,
        landOnDashboard: Bool = false,
        animated: Bool = true
    ) -> ChatViewController {
        openChat(
            from: presenter, hashcode: hashcode, enterpriseId: enterprise.enterpriseId,
            name: enterprise.name, logoUrl: enterprise.logoUrl,
            enterprise: enterprise.raw, landOnDashboard: landOnDashboard, animated: animated
        )
    }
}
