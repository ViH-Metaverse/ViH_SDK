import UIKit

/// Mirrors `ui/activity/splash/SplashActivity.kt`. Decides whether to launch
/// into the dashboard (when an access token is present) or hand back to the
/// demo app's onboarding flow. The actual hand-off is delegated via the
/// `delegate` callback to keep the SDK independent of the demo app's nav.
public protocol SplashViewControllerDelegate: AnyObject {
    func splashShouldLaunchAuthenticatedFlow()
    func splashShouldLaunchOnboarding()
}

public final class SplashViewController: BaseViewController {

    public weak var delegate: SplashViewControllerDelegate?

    private let logoView = SplashLogoView()
    private let spinner = UIActivityIndicatorView(style: .medium)

    public override func initView() {
        // Black background + centered brand mark, matching Android's fragment_splash
        // (android:background="@color/black" + ic_logo_splah centered with a slight upward bias).
        view.backgroundColor = .black

        logoView.translatesAutoresizingMaskIntoConstraints = false
        spinner.color = .white
        spinner.translatesAutoresizingMaskIntoConstraints = false
        spinner.startAnimating()

        view.addSubview(logoView)
        view.addSubview(spinner)

        NSLayoutConstraint.activate([
            logoView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            // Vertical bias ~0.467 (slightly above center), mirroring the Android layout.
            logoView.centerYAnchor.constraint(equalTo: view.centerYAnchor, constant: -12),
            logoView.widthAnchor.constraint(equalToConstant: 151),
            logoView.heightAnchor.constraint(equalToConstant: 141),

            spinner.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            spinner.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -48)
        ])
    }

    public override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        Task { [weak self] in await self?.attemptSilentLoginThenRoute() }
    }

    /// Keeps the user signed in across launches by silently restoring the Cognito session and
    /// re-minting app tokens through the existing `email-login` exchange — no OTP required.
    /// Mirrors Android's `SplashFragment`.
    private func attemptSilentLoginThenRoute() async {
        // A short branding beat so the splash never flashes past.
        try? await Task.sleep(nanoseconds: 800_000_000)

        let prefs = VihChatBotSDK.shared.prefs

        // Never signed in on this device → onboarding.
        guard let phone = prefs?.phoneNumber, !phone.isEmpty else {
            await route(authenticated: false)
            return
        }

        switch await EmailOtpAuth.restoreSession() {
        case .token(let idToken):
            let exchanged = await performSilentExchange(idToken: idToken, mobile: phone)
            // On success we have fresh tokens; otherwise stay in only if a token still exists.
            let hasToken = !(prefs?.accessToken?.isEmpty ?? true)
            await route(authenticated: exchanged || hasToken)

        case .signedOut:
            // Cognito session is genuinely gone — the app token is stale too.
            prefs?.accessToken = nil
            prefs?.refreshToken = nil
            await route(authenticated: false)

        case .unavailable:
            // Offline / Amplify not ready — keep the user in if we still hold an app token.
            let hasToken = !(prefs?.accessToken?.isEmpty ?? true)
            await route(authenticated: hasToken)
        }
    }

    /// Re-runs the backend exchange with a fresh Cognito ID token; persists new app tokens.
    private func performSilentExchange(idToken: String, mobile: String) async -> Bool {
        let prefs = VihChatBotSDK.shared.prefs
        let hashcode = (prefs?.hashcode.flatMap { $0.isEmpty ? nil : $0 })
            ?? VihChatBotSDK.shared.config?.hashcode ?? ""
        let request = EmailLoginRequest(
            cognito_id_token: idToken,
            mobile: mobile,
            channel_id: hashcode,
            fcm_token: prefs?.fcmToken ?? ""
        )
        let repo = HomeRepository(apiService: APIClient.shared.apiService, loaderHost: nil)
        do {
            let response = try await repo.emailLogin(showBlockingLoader: false, body: request)
            guard let data = response.data else { return false }
            prefs?.accessToken = data.access_token
            prefs?.refreshToken = data.refresh
            if let encoded = try? JSONEncoder().encode(data.user),
               let json = String(data: encoded, encoding: .utf8) {
                prefs?.userProfile = json
            }
            prefs?.phoneNumber = data.user.mobile
            return true
        } catch {
            CorrelationLogger.warn(message: "silent email-login exchange failed: \(error.localizedDescription)")
            return false
        }
    }

    @MainActor
    private func route(authenticated: Bool) {
        if authenticated {
            delegate?.splashShouldLaunchAuthenticatedFlow()
        } else {
            delegate?.splashShouldLaunchOnboarding()
        }
    }
}
