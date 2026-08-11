import UIKit

/// iOS counterpart to `utils/ScreenCapturePolicy.kt` (VAPT F-09, MASVS-RESILIENCE-3).
///
/// iOS has no `FLAG_SECURE`. The two exposures that matter for a screen rendering OTPs are:
///
///  1. **The app-switcher snapshot.** iOS photographs the window when the app backgrounds
///     and stores it unencrypted in the app container. Anyone with the unlocked device — or
///     file access on a jailbroken one — can read the last screen the user saw.
///  2. **Live screen capture / mirroring.** `UIScreen.isCaptured` reports when the screen is
///     being recorded or mirrored, which is the closest iOS gets to detecting the capture
///     malware `FLAG_SECURE` blocks on Android.
///
/// Attach once per protected view controller via `install(on:)`. Honours the same
/// `blockScreenCapture` switch as Android so both platforms are configured together.
///
/// Deliberately *not* annotated `@MainActor`: the package targets iOS 15 and the isolation
/// bridges available there (`MainActor.assumeIsolated`) require iOS 17. Every observer is
/// registered with `queue: .main`, so the callbacks are already on the main thread.
final class ScreenCapturePolicy {

    private weak var host: UIViewController?
    private var shield: UIView?
    private var observers: [NSObjectProtocol] = []

    private static var isEnabled: Bool {
        VihChatBotSDK.shared.config?.blockScreenCapture ?? true
    }

    /// Installs snapshot masking and capture monitoring on `controller`. Returns nil (and
    /// does nothing) when the host app has opted out.
    @discardableResult
    static func install(on controller: UIViewController) -> ScreenCapturePolicy? {
        guard isEnabled else { return nil }
        let policy = ScreenCapturePolicy(host: controller)
        policy.start()
        return policy
    }

    private init(host: UIViewController) {
        self.host = host
    }

    private func start() {
        let center = NotificationCenter.default

        // Cover the window *before* iOS takes the snapshot. willResignActive fires ahead of
        // the snapshot; didBecomeActive is the earliest safe point to uncover.
        observers.append(center.addObserver(
            forName: UIApplication.willResignActiveNotification,
            object: nil, queue: .main
        ) { [weak self] _ in
            self?.cover()
        })

        observers.append(center.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil, queue: .main
        ) { [weak self] _ in
            self?.uncover()
        })

        // Recording/mirroring can start while the app is foregrounded, so watch it live.
        observers.append(center.addObserver(
            forName: UIScreen.capturedDidChangeNotification,
            object: nil, queue: .main
        ) { [weak self] _ in
            self?.applyCaptureState()
        })

        applyCaptureState()
    }

    private func applyCaptureState() {
        if UIScreen.main.isCaptured { cover() } else { uncover() }
    }

    private func cover() {
        guard let view = host?.view, shield == nil else { return }
        let blur = UIVisualEffectView(effect: UIBlurEffect(style: .systemMaterial))
        blur.frame = view.bounds
        blur.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        // Above everything the controller drew, including any presented loader overlay.
        view.addSubview(blur)
        shield = blur
    }

    private func uncover() {
        // Never lift the shield while capture is still active — didBecomeActive fires when
        // returning from the switcher even if recording is running.
        guard !UIScreen.main.isCaptured else { return }
        shield?.removeFromSuperview()
        shield = nil
    }

    deinit {
        observers.forEach(NotificationCenter.default.removeObserver)
    }
}
