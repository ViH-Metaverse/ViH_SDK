import Foundation
import Network

/// iOS counterpart to `utils/NetworkConnectivityManager.kt`. Uses
/// `NWPathMonitor` to observe link state. Subscribers can call `addListener`
/// or observe `Notification.Name.connectivityChanged`.
public final class NetworkConnectivityMonitor {

    public static let connectivityChanged = Notification.Name("com.vihmessenger.vihchatbot.connectivityChanged")

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "VihSDK.NetworkConnectivityMonitor")

    public private(set) var isConnected: Bool = false

    public func start() {
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self = self else { return }
            self.isConnected = path.status == .satisfied
            NotificationCenter.default.post(
                name: NetworkConnectivityMonitor.connectivityChanged,
                object: nil,
                userInfo: ["isConnected": self.isConnected]
            )
        }
        monitor.start(queue: queue)
    }

    public func stop() {
        monitor.cancel()
    }

    deinit { monitor.cancel() }
}
