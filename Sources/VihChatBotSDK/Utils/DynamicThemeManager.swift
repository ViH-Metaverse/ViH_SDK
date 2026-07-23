import UIKit

/// Mirrors `utils/DynamicThemeManager.kt`. The Android version persists the
/// theme palette pulled from the SDK features endpoint and broadcasts theme
/// changes via a listener interface. Here we use a singleton + delegate.
public protocol ThemeAware: AnyObject {
    func onThemeChanged(
        primaryColor: UIColor,
        secondaryColor: UIColor,
        primaryTextColor: UIColor,
        secondaryTextColor: UIColor,
        headerColor: UIColor,
        defaultTextColor: UIColor
    )
}

public final class DynamicThemeManager {

    public static let shared = DynamicThemeManager()

    public private(set) var palette: ThemePalette = .default

    private var listeners = NSHashTable<AnyObject>.weakObjects()

    /// Host brand-color override (from `VihUIConfig.theme`). Stored so it can be re-applied
    /// on top of every server-features update — "host config wins" precedence.
    private var hostOverride: (primary: String?, onPrimary: String?, secondary: String?, accent: String?)?

    private init() {}

    public func register(_ listener: ThemeAware) {
        listeners.add(listener as AnyObject)
        deliver(to: listener)
    }

    public func loadSavedTheme() {
        guard let raw = Prefs.shared.vihSettings,
              let data = raw.data(using: .utf8),
              let model = try? JSONDecoder().decode(SdkFeatureModel.self, from: data) else {
            return
        }
        apply(from: model)
    }

    public func apply(from model: SdkFeatureModel) {
        palette = ThemePalette(
            primaryColor: UIColor(hex: model.style_primary_color) ?? palette.primaryColor,
            secondaryColor: UIColor(hex: model.style_secondary_color) ?? palette.secondaryColor,
            primaryTextColor: UIColor(hex: model.style_font_color) ?? palette.primaryTextColor,
            secondaryTextColor: UIColor(hex: model.style_accent_color) ?? palette.secondaryTextColor,
            headerColor: UIColor(hex: model.solid_color) ?? palette.headerColor,
            defaultTextColor: palette.defaultTextColor
        )
        applyStoredHostOverride()   // host config wins over server colors
        broadcast()
    }

    /// White-label override: applies the host app's brand colors on top of whatever colors
    /// are currently set (server features or defaults), overriding ONLY the fields supplied.
    /// The override is remembered and re-applied after each server-features update, so
    /// `VihUIConfig.theme` always wins. Malformed hex is ignored per-field.
    public func applyHostOverride(primary: String?, onPrimary: String?, secondary: String?, accent: String?) {
        hostOverride = (primary, onPrimary, secondary, accent)
        applyStoredHostOverride()
        broadcast()
    }

    private func applyStoredHostOverride() {
        guard let o = hostOverride else { return }
        var p = palette
        if let c = UIColor(hex: o.primary) { p.primaryColor = c }
        if let c = UIColor(hex: o.onPrimary) { p.primaryTextColor = c }
        if let c = UIColor(hex: o.secondary) ?? UIColor(hex: o.accent) { p.secondaryColor = c }
        palette = p
    }

    private func broadcast() {
        for case let listener as ThemeAware in listeners.allObjects {
            deliver(to: listener)
        }
    }

    private func deliver(to listener: ThemeAware) {
        listener.onThemeChanged(
            primaryColor: palette.primaryColor,
            secondaryColor: palette.secondaryColor,
            primaryTextColor: palette.primaryTextColor,
            secondaryTextColor: palette.secondaryTextColor,
            headerColor: palette.headerColor,
            defaultTextColor: palette.defaultTextColor
        )
    }
}

public struct ThemePalette {
    public var primaryColor: UIColor
    public var secondaryColor: UIColor
    public var primaryTextColor: UIColor
    public var secondaryTextColor: UIColor
    public var headerColor: UIColor
    public var defaultTextColor: UIColor

    // Brand defaults (ViH purple #5955F1), matching Android's `primarycolor`. Used until the
    // tenant theme from the SDK-features endpoint overrides them (see `apply(from:)`).
    public static let `default` = ThemePalette(
        primaryColor: UIColor(hex: "#5955F1") ?? .systemIndigo,
        secondaryColor: UIColor(hex: "#5955F1") ?? .systemIndigo,
        primaryTextColor: .label,
        secondaryTextColor: .secondaryLabel,
        headerColor: .systemBackground,
        defaultTextColor: .label
    )
}

public extension UIColor {
    /// Lenient hex parser. Accepts `#RRGGBB` / `#RRGGBBAA` / `RRGGBB`.
    convenience init?(hex: String?) {
        guard let hex = hex?.trimmingCharacters(in: .whitespacesAndNewlines),
              !hex.isEmpty else { return nil }
        var trimmed = hex
        if trimmed.hasPrefix("#") { trimmed.removeFirst() }
        guard let value = UInt64(trimmed, radix: 16) else { return nil }
        switch trimmed.count {
        case 6:
            self.init(
                red: CGFloat((value >> 16) & 0xff) / 255.0,
                green: CGFloat((value >> 8) & 0xff) / 255.0,
                blue: CGFloat(value & 0xff) / 255.0,
                alpha: 1.0
            )
        case 8:
            self.init(
                red: CGFloat((value >> 24) & 0xff) / 255.0,
                green: CGFloat((value >> 16) & 0xff) / 255.0,
                blue: CGFloat((value >> 8) & 0xff) / 255.0,
                alpha: CGFloat(value & 0xff) / 255.0
            )
        default:
            return nil
        }
    }
}
