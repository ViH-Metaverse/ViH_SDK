import Foundation

/// Minimal `{ status, message }` envelope returned by the enterprise state-mutation
/// endpoints (block/unblock, mute/unmute, promotional opt-in/out). Both fields are
/// optional so a terse backend response still decodes. Mirrors `GenericStatusResponse.kt`.
public struct GenericStatusResponse: Codable {
    public var status: Bool?
    public var message: String?
}
