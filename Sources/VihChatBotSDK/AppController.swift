import Foundation
import UIKit

/// iOS equivalent of `com.vihmessenger.vihchatbot.AppController`. Android extended
/// `Application`; on iOS we expose a singleton that the host app initialises in
/// `application(_:didFinishLaunchingWithOptions:)` via `configure(_:)`.
public final class VihChatBotSDK {

    public static let shared = VihChatBotSDK()

    public private(set) var config: VihSDKConfig?
    public private(set) var prefs: Prefs?
    public let networkMonitor = NetworkConnectivityMonitor()

    /// White-label UI customization (tabs + brand theme). Read by `DashboardViewController`.
    public private(set) var uiConfig: VihUIConfig?

    private var foregroundActivityCount = 0
    private var lifecycleObservers: [NSObjectProtocol] = []

    private init() {}

    /// Configure the SDK once during app launch. Optionally pass a `VihUIConfig` to
    /// customize the UI (split tabs, rename/re-icon, brand colors) — see `setUIConfig(_:)`.
    public func configure(_ config: VihSDKConfig, ui: VihUIConfig? = nil) {
        self.config = config
        self.prefs = Prefs.shared
        self.networkMonitor.start()
        registerForegroundLifecycle()
        if let ui = ui { setUIConfig(ui) }
        // Initialise Amplify Auth (Cognito) for email-OTP — mirrors Android's
        // AppController.initAmplify(). No-ops when the pool isn't configured, leaving
        // the phone/SDK login path intact.
        do {
            try EmailOtpAuth.configure(with: config)
        } catch {
            CorrelationLogger.error(message: "Amplify init failed: \(error.localizedDescription)")
        }
        CorrelationLogger.info(message: "VihChatBotSDK configured sdkVersion=\(config.sdkVersion)")
    }

    /// Apply (or update) the white-label UI customization. Stores the config for
    /// `DashboardViewController` to compose its tabs, and seeds the brand theme immediately so
    /// the SDK opens on-brand (host colors are re-applied after server features load, so they
    /// always win). Safe to call before or after `configure(_:)`.
    public func setUIConfig(_ ui: VihUIConfig) {
        self.uiConfig = ui
        if let theme = ui.theme {
            DynamicThemeManager.shared.applyHostOverride(
                primary: theme.primary, onPrimary: theme.onPrimary,
                secondary: theme.secondary, accent: theme.accent
            )
        }
    }

    public var isAppInForeground: Bool {
        return foregroundActivityCount > 0
    }

    private func registerForegroundLifecycle() {
        let center = NotificationCenter.default
        lifecycleObservers.append(center.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil, queue: .main
        ) { [weak self] _ in self?.foregroundActivityCount += 1 })

        lifecycleObservers.append(center.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil, queue: .main
        ) { [weak self] _ in
            guard let self = self else { return }
            self.foregroundActivityCount = max(0, self.foregroundActivityCount - 1)
        })
    }
}
