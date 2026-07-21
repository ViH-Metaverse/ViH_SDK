import Foundation
import Network

/// Performs an HTTPS GET forced over the **cellular** interface, following
/// redirects. This is the transport for carrier Silent Network Auth (SNA): the
/// carrier can only confirm the device's MSISDN when the request egresses over
/// mobile data, not Wi-Fi. `URLSession` cannot *require* the cellular interface,
/// so we use `Network.framework` (`NWConnection` with
/// `requiredInterfaceType = .cellular`) and speak minimal HTTP/1.1 ourselves.
///
/// IMPORTANT: cellular-only requests cannot run on the Simulator, on Wi-Fi-only,
/// or in airplane mode. Test on a real device with mobile data enabled. If the
/// cellular path is unavailable the caller should fall back to SMS OTP.
///
/// For production you may instead adopt your SNA provider's vetted iOS SDK
/// (Twilio, tru.ID, Vonage) — this class is a provider-agnostic equivalent.
public final class CellularHTTPClient {

    public struct Response {
        public let statusCode: Int
        public let location: String?
        public let body: Data
    }

    public enum CellularError: Error {
        case cellularUnavailable
        case timedOut
        case malformedResponse
        case tooManyRedirects
        case connectionFailed(String)
    }

    public init() {}

    /// GET `url` over cellular, following up to `maxRedirects` redirects.
    public func get(_ url: URL, maxRedirects: Int = 5, timeout: TimeInterval = 20) async throws -> Response {
        var current = url
        for _ in 0...maxRedirects {
            let response = try await performGet(current, timeout: timeout)
            if (300...399).contains(response.statusCode),
               let location = response.location,
               let next = URL(string: location, relativeTo: current)?.absoluteURL {
                current = next
                continue
            }
            return response
        }
        throw CellularError.tooManyRedirects
    }

    // MARK: - Single request over a cellular-pinned connection

    private func performGet(_ url: URL, timeout: TimeInterval) async throws -> Response {
        guard let host = url.host else { throw CellularError.malformedResponse }
        let isTLS = (url.scheme?.lowercased() ?? "https") == "https"
        let port = NWEndpoint.Port(rawValue: UInt16(url.port ?? (isTLS ? 443 : 80))) ?? (isTLS ? 443 : 80)

        let params: NWParameters = isTLS ? .tls : .tcp
        // Force cellular; refuse Wi-Fi / wired so SNA actually exercises the SIM.
        params.requiredInterfaceType = .cellular
        params.prohibitedInterfaceTypes = [.wifi, .wiredEthernet, .loopback]

        let connection = NWConnection(host: .init(host), port: port, using: params)

        var path = url.path.isEmpty ? "/" : url.path
        if let q = url.query, !q.isEmpty { path += "?\(q)" }
        let request =
            "GET \(path) HTTP/1.1\r\n" +
            "Host: \(host)\r\n" +
            "User-Agent: VihChatBotSDK-SNA\r\n" +
            "Accept: */*\r\n" +
            "Connection: close\r\n\r\n"

        return try await withCheckedThrowingContinuation { continuation in
            let state = ResumeGuard(continuation: continuation)

            // Overall timeout.
            let timer = DispatchSource.makeTimerSource(queue: .global())
            timer.schedule(deadline: .now() + timeout)
            timer.setEventHandler { state.fail(.timedOut); connection.cancel() }
            timer.resume()
            state.onDone = { timer.cancel() }

            var buffer = Data()

            func receiveLoop() {
                connection.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1024) { data, _, isComplete, error in
                    if let data = data, !data.isEmpty { buffer.append(data) }
                    if let error = error {
                        state.fail(.connectionFailed(String(describing: error)))
                        connection.cancel()
                        return
                    }
                    if isComplete {
                        connection.cancel()
                        if let parsed = Self.parse(buffer) {
                            state.succeed(parsed)
                        } else {
                            state.fail(.malformedResponse)
                        }
                        return
                    }
                    receiveLoop()
                }
            }

            connection.stateUpdateHandler = { newState in
                switch newState {
                case .ready:
                    let payload = Data(request.utf8)
                    connection.send(content: payload, completion: .contentProcessed { sendError in
                        if let sendError = sendError {
                            state.fail(.connectionFailed(String(describing: sendError)))
                            connection.cancel()
                            return
                        }
                        receiveLoop()
                    })
                case .failed(let error):
                    state.fail(.connectionFailed(String(describing: error)))
                    connection.cancel()
                case .waiting(let error):
                    // Typically "no cellular interface available".
                    state.fail(.cellularUnavailable)
                    _ = error
                    connection.cancel()
                default:
                    break
                }
            }

            connection.start(queue: .global())
        }
    }

    // MARK: - Minimal HTTP/1.1 response parsing

    private static func parse(_ data: Data) -> Response? {
        guard let separatorRange = data.range(of: Data("\r\n\r\n".utf8)) else { return nil }
        let headerData = data.subdata(in: data.startIndex..<separatorRange.lowerBound)
        let body = data.subdata(in: separatorRange.upperBound..<data.endIndex)
        guard let headerString = String(data: headerData, encoding: .utf8) else { return nil }

        let lines = headerString.components(separatedBy: "\r\n")
        guard let statusLine = lines.first else { return nil }
        // "HTTP/1.1 302 Found"
        let statusParts = statusLine.split(separator: " ")
        guard statusParts.count >= 2, let code = Int(statusParts[1]) else { return nil }

        var location: String?
        for line in lines.dropFirst() {
            let lower = line.lowercased()
            if lower.hasPrefix("location:") {
                location = line.dropFirst("location:".count).trimmingCharacters(in: .whitespaces)
                break
            }
        }
        return Response(statusCode: code, location: location, body: body)
    }

    /// Serializes continuation resumption so we never resume twice.
    private final class ResumeGuard {
        private let continuation: CheckedContinuation<Response, Error>
        private let lock = NSLock()
        private var finished = false
        var onDone: (() -> Void)?

        init(continuation: CheckedContinuation<Response, Error>) {
            self.continuation = continuation
        }
        func succeed(_ response: Response) {
            lock.lock(); defer { lock.unlock() }
            guard !finished else { return }
            finished = true
            onDone?()
            continuation.resume(returning: response)
        }
        func fail(_ error: CellularError) {
            lock.lock(); defer { lock.unlock() }
            guard !finished else { return }
            finished = true
            onDone?()
            continuation.resume(throwing: error)
        }
    }
}
