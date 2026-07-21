import UIKit

/// Mirrors `base/BaseActivity.kt`. Provides:
///   - the `LoaderHost` blocking loader contract used by repositories
///   - default `ThemeAware` registration
///   - lifecycle hooks (`initViewModels`, `initView`, `setObservers`, `setListeners`)
///     that subclasses override — same naming as Android.
open class BaseViewController: UIViewController, LoaderHost, ThemeAware {

    private lazy var blockingLoader: UIView = {
        let overlay = UIView()
        overlay.backgroundColor = UIColor.black.withAlphaComponent(0.35)
        overlay.translatesAutoresizingMaskIntoConstraints = false
        let spinner = UIActivityIndicatorView(style: .large)
        spinner.color = .white
        spinner.startAnimating()
        spinner.translatesAutoresizingMaskIntoConstraints = false
        overlay.addSubview(spinner)
        NSLayoutConstraint.activate([
            spinner.centerXAnchor.constraint(equalTo: overlay.centerXAnchor),
            spinner.centerYAnchor.constraint(equalTo: overlay.centerYAnchor)
        ])
        return overlay
    }()

    open override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        initViewModels()
        initView()
        setObservers()
        setListeners()
        DynamicThemeManager.shared.register(self)
    }

    // MARK: - Hooks (override)

    open func initViewModels() {}
    open func initView() {}
    open func setObservers() {}
    open func setListeners() {}
    open func onViewClick(_ view: UIView?) {}

    open func onThemeChanged(
        primaryColor: UIColor,
        secondaryColor: UIColor,
        primaryTextColor: UIColor,
        secondaryTextColor: UIColor,
        headerColor: UIColor,
        defaultTextColor: UIColor
    ) {}

    // MARK: - LoaderHost

    public func showBlockingLoader() {
        guard blockingLoader.superview == nil else { return }
        view.addSubview(blockingLoader)
        NSLayoutConstraint.activate([
            blockingLoader.topAnchor.constraint(equalTo: view.topAnchor),
            blockingLoader.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            blockingLoader.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            blockingLoader.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])
    }

    public func hideBlockingLoader() {
        blockingLoader.removeFromSuperview()
    }

    /// Mirrors Android `updateStatusBarColor(...)`. iOS doesn't expose a tint
    /// directly — apply the colour to the nav bar or to a top safe-area view.
    public func updateStatusBarBackground(_ color: UIColor) {
        guard let window = view.window else { return }
        let topInset = window.safeAreaInsets.top
        let tag = 0xC0FFEE
        window.viewWithTag(tag)?.removeFromSuperview()
        guard topInset > 0 else { return }
        let strip = UIView(frame: CGRect(x: 0, y: 0, width: window.bounds.width, height: topInset))
        strip.tag = tag
        strip.backgroundColor = color
        window.addSubview(strip)
    }
}
