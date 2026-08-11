import Foundation
import CryptoKit
import CommonCrypto

/// Enforces SHA-256 public-key pins for hosts listed in `VihSDKConfig.certificatePins`.
/// Hosts without pins fall back to the default system trust evaluation. Mirrors the
/// commented-out OkHttp `CertificatePinner` block in Android `ApiClient`.
final class CertificatePinningDelegate: NSObject, URLSessionDelegate, URLSessionTaskDelegate {

    /// iOS `URLSession` drops the `Authorization` header when it follows a
    /// redirect (e.g. Django's `APPEND_SLASH` 301 for a URL missing its trailing
    /// slash, like `main/chat-history/<hash>/<id>`). Without re-attaching it the
    /// redirected request is unauthenticated and 401s — which the SDK treats as a
    /// session expiry and bounces the user to login. Re-attach for SAME-HOST
    /// redirects only (never leak credentials cross-origin). OkHttp does this on
    /// Android, which is why Android never hit this.
    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        var redirected = request
        if let original = task.originalRequest,
           let auth = original.value(forHTTPHeaderField: "Authorization"),
           redirected.value(forHTTPHeaderField: "Authorization") == nil,
           original.url?.host == redirected.url?.host {
            redirected.setValue(auth, forHTTPHeaderField: "Authorization")
        }
        completionHandler(redirected)
    }

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let trust = challenge.protectionSpace.serverTrust else {
            completionHandler(.performDefaultHandling, nil)
            return
        }

        let host = challenge.protectionSpace.host
        let configuredPins = VihChatBotSDK.shared.config?.certificatePins[host] ?? []

        if configuredPins.isEmpty {
            // SECURITY (VAPT F-03): pinning silently fell through to system trust whenever
            // the pin map was empty — and it defaulted to empty, so pinning was effectively
            // off in every build. It now defaults to `VihSDKConfig.defaultCertificatePins`,
            // and an unpinned *API* host is treated as a misconfiguration rather than an
            // acceptable fallback: trap in debug so it is caught before shipping, and log
            // loudly in release rather than failing a partner's traffic outright.
            //
            // Non-API hosts (image CDNs, enterprise websites) legitimately have no pins and
            // fall through to normal platform validation without complaint.
            if Self.isAPIHost(host) {
                let message = "No certificate pins configured for API host \(host) — "
                    + "TLS pinning is NOT in effect for this connection."
                assertionFailure(message)
                CorrelationLogger.error(tag: "CertificatePinning", message: message)
            }
            completionHandler(.performDefaultHandling, nil)
            return
        }

        // Evaluate trust first; only proceed if the system would accept it.
        var error: CFError?
        guard SecTrustEvaluateWithError(trust, &error) else {
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }

        let count = SecTrustGetCertificateCount(trust)
        for i in 0..<count {
            guard let cert = SecTrustGetCertificateAtIndex(trust, i),
                  let pinHash = Self.spkiSHA256Base64(of: cert) else { continue }
            if configuredPins.contains(pinHash) {
                completionHandler(.useCredential, URLCredential(trust: trust))
                return
            }
        }
        completionHandler(.cancelAuthenticationChallenge, nil)
    }

    /// True when `host` is the API endpoint this SDK talks to, i.e. a host that must be
    /// pinned. Compared against the configured base URL rather than a hardcoded list so a
    /// redeployment to a new API domain still trips the check.
    private static func isAPIHost(_ host: String) -> Bool {
        guard let apiHost = VihChatBotSDK.shared.config?.apiBaseURL.host else { return false }
        return host.caseInsensitiveCompare(apiHost) == .orderedSame
    }

    /// SHA-256 of the certificate's SubjectPublicKeyInfo, Base64-encoded — the
    /// same format OkHttp accepts (`sha256/...`).
    private static func spkiSHA256Base64(of cert: SecCertificate) -> String? {
        guard let pubKey = SecCertificateCopyKey(cert),
              let pubData = SecKeyCopyExternalRepresentation(pubKey, nil) as Data? else {
            return nil
        }
        let digest = SHA256.hash(data: pubData)
        return Data(digest).base64EncodedString()
    }
}
