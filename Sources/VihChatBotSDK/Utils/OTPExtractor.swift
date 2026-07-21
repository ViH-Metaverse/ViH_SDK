import Foundation

/// Mirrors the OTP extraction helper used by `MyFirebaseMessagingService`.
/// Pulls a contiguous 4–6 digit code out of a notification body.
public enum OTPExtractor {
    public static func extract(from message: String?) -> String? {
        guard let message = message else { return nil }
        let pattern = "(?<!\\d)(\\d{4,6})(?!\\d)"
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let range = NSRange(message.startIndex..<message.endIndex, in: message)
        guard let match = regex.firstMatch(in: message, range: range),
              let r = Range(match.range(at: 1), in: message) else { return nil }
        return String(message[r])
    }
}
