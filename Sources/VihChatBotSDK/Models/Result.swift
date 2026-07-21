import Foundation

/// Mirrors `utils/result/results.kt` (`Results.Success` / `Results.Error`).
/// Renamed to `APIResult` to avoid colliding with the Swift stdlib `Result` type.
public enum APIResult<T> {
    case success(T)
    case failure(String)
}

public struct APIError: Error, LocalizedError {
    public let message: String
    public init(_ message: String) { self.message = message }
    public var errorDescription: String? { message }
}

/// Mirrors `NoConnectionException` thrown by repositories.
public struct NoConnectionError: Error, LocalizedError {
    public let message: String
    public init(_ message: String) { self.message = message }
    public var errorDescription: String? { message }
}
