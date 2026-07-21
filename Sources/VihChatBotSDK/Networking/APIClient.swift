import Foundation

/// Mirrors `api/services/RetrofitClient.kt` (`ApiClient` singleton). Builds a
/// `URLSession` configured with the same timeout profile as the Android OkHttp
/// client (60s connect/read/write). Auth header injection is delegated to
/// `AuthInterceptor`. TLS pinning lives on `CertificatePinningDelegate`.
public final class APIClient {

    public static let shared = APIClient()

    private(set) lazy var session: URLSession = {
        let cfg = URLSessionConfiguration.default
        cfg.timeoutIntervalForRequest = 60
        cfg.timeoutIntervalForResource = 60
        cfg.httpAdditionalHeaders = ["Accept": "application/json"]
        return URLSession(
            configuration: cfg,
            delegate: CertificatePinningDelegate(),
            delegateQueue: nil
        )
    }()

    public lazy var apiService: ApiService = APIServiceImpl(client: self)

    public var baseURL: URL {
        guard let cfg = VihChatBotSDK.shared.config else {
            fatalError("VihChatBotSDK.configure(_:) must be called before any network call")
        }
        return cfg.apiBaseURL
    }

    private init() {}

    // MARK: - Request builder

    func request(
        path: String,
        method: String = "GET",
        query: [URLQueryItem] = [],
        headers: [String: String] = [:],
        body: Data? = nil,
        contentType: String? = nil,
        explicitURL: URL? = nil
    ) -> URLRequest {
        let url: URL = {
            if let explicit = explicitURL { return explicit }
            var components = URLComponents(
                url: baseURL.appendingPathComponent(path),
                resolvingAgainstBaseURL: false
            )!
            if !query.isEmpty { components.queryItems = query }
            return components.url!
        }()

        var req = URLRequest(url: url)
        req.httpMethod = method
        if let body = body { req.httpBody = body }
        if let contentType = contentType {
            req.setValue(contentType, forHTTPHeaderField: "Content-Type")
        }
        for (k, v) in headers { req.setValue(v, forHTTPHeaderField: k) }
        AuthInterceptor.inject(into: &req)
        return req
    }

    // MARK: - Decoded GET/POST/PATCH helpers

    func send<T: Decodable>(_ request: URLRequest, as type: T.Type) async throws -> T {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw APIError("Invalid response object")
        }

        // TEMP DEBUG (remove before release): logs every request's status + body
        // so empty lists can be told apart from decode failures. Gated on isDebug.
        if VihChatBotSDK.shared.config?.isDebug == true {
            let method = request.httpMethod ?? "GET"
            let urlString = request.url?.absoluteString ?? "?"
            let hasAuth = request.value(forHTTPHeaderField: "Authorization") != nil
            let bodySnippet = String(data: data.prefix(1000), encoding: .utf8) ?? "<non-utf8 \(data.count) bytes>"
            CorrelationLogger.info(
                message: "HTTP \(http.statusCode) \(method) \(urlString) auth=\(hasAuth) bytes=\(data.count)\n   body: \(bodySnippet)"
            )
        }

        try Self.throwForHTTP(http, data: data)
        do {
            return try JSONDecoder().decode(T.self, from: data)
        } catch {
            // Surface the underlying DecodingError (missing key / type mismatch),
            // not just localizedDescription, so the failing field is visible.
            throw APIError("Decode \(T.self) failed: \(error)")
        }
    }

    /// Throws a normalised error for non-2xx responses. Matches Android
    /// `BaseRepository.returnSafeAPIResponse` status-code mapping.
    static func throwForHTTP(_ http: HTTPURLResponse, data: Data) throws {
        guard !(200...299).contains(http.statusCode) else { return }
        let body = String(data: data, encoding: .utf8) ?? ""
        let msg: String
        switch http.statusCode {
        case 401: msg = BaseRepository.authFailedError
        case 403: msg = "Access denied"
        case 404: msg = "Resource not found"
        case 413: msg = "Payload size too large"
        case 500, 501, 502, 503: msg = "Server error, please try again later"
        default: msg = body.isEmpty ? "Unknown error occurred" : body
        }
        throw APIError(msg)
    }
}
