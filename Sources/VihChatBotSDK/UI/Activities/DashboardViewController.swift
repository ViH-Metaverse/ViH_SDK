import UIKit
import Combine

/// Mirrors `ui/activity/home/DashBoardActivity.kt`. The Android version hosts
/// a bottom-tab structure backed by fragments (Chat list, Discover, Settings).
/// On iOS we use `UITabBarController` directly.
public final class DashboardViewController: UITabBarController {

    // Retained so its async network call survives past viewDidLoad (the VM captures self weakly).
    private let profileViewModel = ProfileViewModel(loaderHost: nil)

    public override func viewDidLoad() {
        super.viewDidLoad()
        if let tabs = VihChatBotSDK.shared.uiConfig?.navigation?.tabs, !tabs.isEmpty {
            // White-label: compose the tab bar from the host's VihTab list (order, label, icon).
            // UITabBar shows ~5 comfortably; take the first 5.
            let shown = Array(tabs.prefix(5))
            viewControllers = shown.map { makeTab($0) }
            let defaultId = VihChatBotSDK.shared.uiConfig?.navigation?.defaultTab
                ?? (shown.contains { $0.id == .chats } ? .chats : shown.first?.id)
            if let defaultId = defaultId, let idx = shown.firstIndex(where: { $0.id == defaultId }) {
                selectedIndex = idx
            }
        } else {
            // Legacy static tabs: Chats, Discover, Settings.
            let chatList = UINavigationController(rootViewController: ChatListViewController())
            chatList.tabBarItem = UITabBarItem(title: "Chats", image: UIImage(systemName: "bubble.left.and.bubble.right"), tag: 0)

            let discover = UINavigationController(rootViewController: DiscoverViewController())
            discover.tabBarItem = UITabBarItem(title: "Discover", image: UIImage(systemName: "globe"), tag: 1)

            let settings = UINavigationController(rootViewController: SettingsViewController())
            settings.tabBarItem = UITabBarItem(title: "Settings", image: UIImage(systemName: "gearshape"), tag: 2)

            viewControllers = [chatList, discover, settings]
        }

        // Brand the tab bar (selected = accent purple, matching Android's bottom nav).
        tabBar.tintColor = DynamicThemeManager.shared.palette.primaryColor
        tabBar.unselectedItemTintColor = UIColor(hex: "#828282")
        let appearance = UITabBarAppearance()
        appearance.configureWithDefaultBackground()
        tabBar.standardAppearance = appearance
        tabBar.scrollEdgeAppearance = appearance

        // SDK feature/theme hydration as the Android DashboardActivity does it.
        if let hashcode = VihChatBotSDK.shared.prefs?.hashcode ?? VihChatBotSDK.shared.config?.hashcode {
            let vm = HomeViewModel(loaderHost: nil)
            vm.getSdkFeatures(showBlockingLoader: false, hashCode: hashcode)

            // Defensive: make sure this account is subscribed to the active channel. Login is
            // supposed to subscribe, but an account that first authenticated on a different
            // channel isn't — the backend then rejects messaging with 2001 "user not registered
            // on channel". subscribe-channel is idempotent, so this is safe on every entry.
            profileViewModel.subscribeChannel(showBlockingLoader: false, hashcode: hashcode)
        }
        // Best-effort re-registration of the cached push token (post-auth retry).
        DeviceTokenRegistrar.shared.registerCachedTokenIfNeeded()
    }

    /// Builds a nav-wrapped tab from a `VihTab` — mapping the id to its surface (category tabs
    /// are `ChatListViewController` filtered by `template_type`) and applying label + icon.
    private func makeTab(_ tab: VihTab) -> UINavigationController {
        let root: UIViewController
        switch tab.id {
        case .discover: root = DiscoverViewController()
        case .settings: root = SettingsViewController()
        case .chats, .promo, .transactional, .otp:
            // chats/promo → nil (all active chats); otp/transactional → their template_type.
            root = ChatListViewController(category: tab.id.templateType)
        }
        let nav = UINavigationController(rootViewController: root)
        nav.tabBarItem = UITabBarItem(
            title: tab.label ?? defaultTabLabel(tab.id),
            image: tab.icon ?? defaultTabIcon(tab.id),
            tag: 0
        )
        return nav
    }

    private func defaultTabLabel(_ id: VihTabId) -> String {
        switch id {
        case .discover: return "Discover"
        case .chats: return "Chats"
        case .promo: return "Promotions"
        case .transactional: return "Transactions"
        case .otp: return "OTP"
        case .settings: return "Settings"
        }
    }

    private func defaultTabIcon(_ id: VihTabId) -> UIImage? {
        let name: String
        switch id {
        case .discover: name = "globe"
        case .chats: name = "bubble.left.and.bubble.right"
        case .promo: name = "megaphone"
        case .transactional: name = "arrow.left.arrow.right"
        case .otp: name = "lock.shield"
        case .settings: name = "gearshape"
        }
        return UIImage(systemName: name)
    }
}
