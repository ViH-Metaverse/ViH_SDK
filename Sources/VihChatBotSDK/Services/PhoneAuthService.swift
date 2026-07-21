import Foundation

/// Orchestrates silent phone verification: carrier Silent Network Auth (SNA)
/// first, SMS OTP as fallback. See docs/ios/phone-auth-backend-contract.md.
///
/// Usage (from the host login screen):
/// ```
/// switch await PhoneAuthService().startVerification(phoneE164: phone, hashcode: hc) {
/// case .verified:            // SNA succeeded silently — proceed to signup-login
/// case .needsOTP(let id):    // present an OTP screen, then call verifyOTP(...)
/// case .failed(let reason):  // show an error
/// }
/// ```
public final class PhoneAuthService {

    public enum StartResult {
        /// SNA verified the number silently. Proceed straight to signup-login.
        case verified
        /// SNA unavailable/failed — an SMS code was sent. Present an OTP screen.
        case needsOTP(requestId: String)
        case failed(String)
    }

    public enum OTPVerifyResult {
        case verified
        case failed(String)
    }

    private let api = APIClient.shared
    private let cellular = CellularHTTPClient()

    public init() {}

    /// Begin verification for an E.164 phone (e.g. "+919023409998").
    public func startVerification(phoneE164: String, hashcode: String) async -> StartResult {
        do {
            let start = try await postJSON(
                BaseAPIConstants.authStart,
                AuthStartRequest(phone: phoneE164, channel_id: hashcode),
                as: AuthStartResponse.self
            )

            // Primary path: Silent Network Auth over cellular.
            if start.method == "sna", let urlStr = start.check_url, let url = URL(string: urlStr) {
                do {
                    _ = try await cellular.get(url)
                    let finish = try await postJSON(
                        BaseAPIConstants.authFinish,
                        AuthFinishRequest(request_id: start.request_id),
                        as: AuthVerifyResponse.self
                    )
                    if finish.verified {
                        CorrelationLogger.info(message: "phone verified via SNA")
                        return .verified
                    }
                    CorrelationLogger.info(message: "SNA not verified, falling back to OTP")
                } catch {
                    // No cellular (Wi-Fi/simulator), carrier unsupported, etc.
                    CorrelationLogger.warn(message: "SNA failed, falling back to OTP", error: error)
                }
            }

            // Fallback: send an SMS OTP.
            return await sendOTP(requestId: start.request_id)
        } catch {
            return .failed(error.localizedDescription)
        }
    }

    /// Verify the SMS code the user entered.
    public func verifyOTP(requestId: String, code: String) async -> OTPVerifyResult {
        do {
            let resp = try await postJSON(
                BaseAPIConstants.authOtpVerify,
                OtpVerifyRequest(request_id: requestId, code: code),
                as: AuthVerifyResponse.self
            )
            return resp.verified ? .verified : .failed(resp.message ?? "Invalid code")
        } catch {
            return .failed(error.localizedDescription)
        }
    }

    /// Re-send the OTP for an in-flight request.
    public func resendOTP(requestId: String) async -> Bool {
        if case .needsOTP = await sendOTP(requestId: requestId) { return true }
        return false
    }

    // MARK: - Helpers

    private func sendOTP(requestId: String) async -> StartResult {
        do {
            let resp = try await postJSON(
                BaseAPIConstants.authOtpSend,
                OtpSendRequest(request_id: requestId),
                as: OtpSendResponse.self
            )
            return resp.sent ? .needsOTP(requestId: resp.request_id ?? requestId)
                             : .failed(resp.message ?? "Could not send the verification code")
        } catch {
            return .failed(error.localizedDescription)
        }
    }

    private func postJSON<Body: Encodable, T: Decodable>(
        _ path: String, _ body: Body, as type: T.Type
    ) async throws -> T {
        let request = api.request(
            path: path,
            method: "POST",
            body: try JSONEncoder().encode(body),
            contentType: "application/json"
        )
        return try await api.send(request, as: T.self)
    }
}
