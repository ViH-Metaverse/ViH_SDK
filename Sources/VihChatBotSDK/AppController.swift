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

    private var foregroundActivityCount = 0
    private var lifecycleObservers: [NSObjectProtocol] = []

    private init() {}

    /// Configure the SDK once during app launch.
    public func configure(_ config: VihSDKConfig) {
        self.config = config
        self.prefs = Prefs.shared
        self.networkMonitor.start()
        registerForegroundLifecycle()
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
