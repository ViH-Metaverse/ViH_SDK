import UIKit

/// Every VihChatBot surface renders in one fixed appearance.
///
/// The UI was authored light — there is not a single dark variant in any first-party asset
/// catalogue — so "dark mode" was never a designed theme. It was iOS auto-adapting the
/// semantic colours (`.label`, `.systemBackground`, …) underneath a light design, which
/// produced illegible text and mismatched surfaces instead. Individual widgets were being
/// pinned back to `.light` one at a time to cope; this is that fix applied once, centrally.
///
/// Pinning affects *dynamic* colours only. Anything given an explicit colour (a black photo
/// viewer backdrop, the brand palette from `DynamicThemeManager`) is unchanged.
public enum VihInterfaceStyle {

    /// The appearance every SDK screen is pinned to.
    public static let fixed: UIUserInterfaceStyle = .light

    /// Pins a controller and everything it presents below it.
    ///
    /// The SDK ships inside host apps, so it cannot rely on the app-level
    /// `UIUserInterfaceStyle` Info.plist key the way `VihMessengerApp` does — a host may
    /// itself be running dark. Pinning per controller keeps SDK screens identical in any
    /// host without touching the host's own windows.
    public static func pin(_ viewController: UIViewController) {
        viewController.overrideUserInterfaceStyle = fixed
    }
}
