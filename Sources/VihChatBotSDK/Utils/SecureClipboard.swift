import UIKit

/// Clipboard writes for one-time codes (VAPT F-08, CWE-200).
///
/// `UIPasteboard.general.string = code` has two properties that are wrong for an OTP:
/// the value syncs through **Universal Clipboard** to every device signed into the same
/// Apple account, and it persists until something else overwrites it. Any app the user
/// foregrounds afterwards can read it (iOS 16+ prompts for programmatic reads, but the
/// system paste affordance still surfaces the value).
///
/// This helper writes the code as a `localOnly` item with a short expiry, so it never
/// leaves the device and the system drops it on its own.
enum SecureClipboard {

    /// How long a one-time code may sit on the pasteboard before iOS expires it.
    static let retention: TimeInterval = 60

    /// Copies `value` as a device-local, self-expiring pasteboard item.
    static func copySensitive(_ value: String?) {
        guard let value, !value.isEmpty else { return }
        UIPasteboard.general.setItems(
            [[UTType.plainText: value]],
            options: [
                // Never hand a one-time code to Universal Clipboard.
                .localOnly: true,
                .expirationDate: Date().addingTimeInterval(retention)
            ]
        )
    }

    /// `public.utf8-plain-text` — the pasteboard type key for plain text. Spelled out rather
    /// than pulled from UniformTypeIdentifiers so this file compiles on the iOS 15 minimum
    /// the package targets.
    private enum UTType {
        static let plainText = "public.utf8-plain-text"
    }
}
