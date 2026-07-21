import Foundation
import UIKit

/// Mirrors `data/repository/BaseRepository.kt`. The "blocking loader" hooks are
/// expressed via an optional `LoaderHost` (the active view controller) instead
/// of `BaseActivity`.
public protocol LoaderHost: AnyObject {
    func showBlockingLoader()
    func hideBlockingLoader()
}

public class BaseRepository {

    public static let authFailedError = "Authentication failed"

    /// One-shot guard: when the JWT expires, multiple in-flight requests will
    /// 401 — only the first should redirect to login.
    private static var sessionExpiredHandled = false
    private static let sessionLock = NSLock()

    private weak var loaderHost: LoaderHost?

    public init(loaderHost: LoaderHost?) {
        self.loaderHost = loaderHost
    }

    /// Returns the decoded body or throws `NoConnectionError` after rendering
    /// the same error toast Android shows. 401 routes to `handleSessionExpired`.
    public func doSafeAPIRequest<T>(
        showBlockingLoader: Bool,
        _ call: () async throws -> T
    ) async throws -> T {
        if showBlockingLoader {
            await MainActor.run { self.loaderHost?.showBlockingLoader() }
        }

        do {
            let value = try await call()
            await MainActor.run { self.loaderHost?.hideBlockingLoader() }
            // A successful authenticated call means the session is healthy again,
            // so re-arm the one-shot guard. Without this, after one 401 the flag
            // stays set forever and a later genuine expiry won't redirect.
            Self.sessionLock.lock()
            Self.sessionExpiredHandled = false
            Self.sessionLock.unlock()
            return value
        } catch let error as APIError where error.message == Self.authFailedError {
            await MainActor.run { self.loaderHost?.hideBlockingLoader() }
            await handleSessionExpired()
            throw CancellationError()
        } catch let error as APIError {
            await MainActor.run {
                self.loaderHost?.hideBlockingLoader()
                self.showToast(error.message)
            }
            throw NoConnectionError(error.message)
        } catch {
            let mapped = Self.mapTransport(error)
            CorrelationLogger.error(message: "API Exception: \(type(of: error)) - \(mapped)")
            await MainActor.run {
                self.loaderHost?.hideBlockingLoader()
                self.showToast(mapped)
            }
            throw NoConnectionError(mapped)
        }
    }

    private static func mapTransport(_ error: Error) -> String {
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain {
            switch nsError.code {
            case NSURLErrorTimedOut: return "Connection timed out. Please try again."
            case NSURLErrorNotConnectedToInternet, NSURLErrorCannotFindHost:
                return "Cannot connect to server. Please check your internet connection."
            case NSURLErrorCannotConnectToHost, NSURLErrorNetworkConnectionLost:
                return "Failed to connect to server. Please check your internet connection."
            case NSURLErrorSecureConnectionFailed, NSURLErrorServerCertificateUntrusted:
                return "Secure connection failed. Please try again later."
            default: break
            }
        }
        return error.localizedDescription
    }

    @MainActor
    private func handleSessionExpired() {
        Self.sessionLock.lock()
        defer { Self.sessionLock.unlock() }
        if Self.sessionExpiredHandled { return }
        Self.sessionExpiredHandled = true

        VihChatBotSDK.shared.prefs?.accessToken = nil
        VihChatBotSDK.shared.prefs?.refreshToken = nil

        // Notify the host app — the demo app observes this and replays the
        // launch flow. Mirrors Android's launchIntent + FLAG_ACTIVITY_CLEAR_TASK.
        NotificationCenter.default.post(
            name: Notification.Name("com.vihmessenger.vihchatbot.SESSION_EXPIRED"),
            object: nil
        )
    }

    @MainActor
    private func showToast(_ message: String) {
        // Lightweight toast on the key window. Mirrors the Android `Toast.makeText`
        // calls inside repositories. Host apps that want a richer UI should
        // subscribe to a dedicated error stream and skip this fallback.
        guard let window = UIApplication.shared.connectedScenes
            .compactMap({ ($0 as? UIWindowScene)?.keyWindow })
            .first else { return }
        let label = UILabel()
        label.text = message
        label.textColor = .white
        label.backgroundColor = UIColor.black.withAlphaComponent(0.8)
        label.textAlignment = .center
        label.numberOfLines = 0
        label.layer.cornerRadius = 8
        label.layer.masksToBounds = true
        label.font = .systemFont(ofSize: 14)
        label.alpha = 0
        label.translatesAutoresizingMaskIntoConstraints = false
        window.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: window.centerXAnchor),
            label.bottomAnchor.constraint(equalTo: window.safeAreaLayoutGuide.bottomAnchor, constant: -32),
            label.widthAnchor.constraint(lessThanOrEqualTo: window.widthAnchor, multiplier: 0.9),
            label.heightAnchor.constraint(greaterThanOrEqualToConstant: 36)
        ])
        UIView.animate(withDuration: 0.2, animations: { label.alpha = 1 }) { _ in
            UIView.animate(
                withDuration: 0.2, delay: 2.0,
                options: [],
                animations: { label.alpha = 0 },
                completion: { _ in label.removeFromSuperview() }
            )
        }
    }
}
