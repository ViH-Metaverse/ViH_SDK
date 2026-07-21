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
        let chatList = UINavigationController(rootViewController: ChatListViewController())
        chatList.tabBarItem = UITabBarItem(title: "Chats", image: UIImage(systemName: "bubble.left.and.bubble.right"), tag: 0)

        let discover = UINavigationController(rootViewController: DiscoverViewController())
        discover.tabBarItem = UITabBarItem(title: "Discover", image: UIImage(systemName: "globe"), tag: 1)

        let settings = UINavigationController(rootViewController: SettingsViewController())
        settings.tabBarItem = UITabBarItem(title: "Settings", image: UIImage(systemName: "gearshape"), tag: 2)

        viewControllers = [chatList, discover, settings]

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
}
