import Foundation
import os

/// Mirrors `utils/CorrelationLogger.kt`. Renders the same
/// `[shoot=… msg=… trace=…] message` line so OpenSearch queries continue to
/// span both platforms. iOS uses `os.Logger` instead of Logcat. Bugfender has a
/// Swift SDK; once integrated, mirror the Android dual-sink behaviour.
public enum CorrelationLogger {

    private static let logger = Logger(subsystem: "com.vihmessenger.vihchatbot", category: "VihSDK")

    public static func debug(
        tag: String = "VihSDK",
        message: String,
        shootId: String? = nil,
        messageId: String? = nil,
        traceId: String? = nil
    ) {
        let line = format(message: message, shootId: shootId, messageId: messageId, traceId: traceId)
        if VihChatBotSDK.shared.config?.isDebug == true {
            logger.debug("\(tag, privacy: .public): \(line, privacy: .public)")
        }
        // Bugfender.d(tag, line)
    }

    public static func info(
        tag: String = "VihSDK",
        message: String,
        shootId: String? = nil,
        messageId: String? = nil,
        traceId: String? = nil
    ) {
        let line = format(message: message, shootId: shootId, messageId: messageId, traceId: traceId)
        if VihChatBotSDK.shared.config?.isDebug == true {
            logger.info("\(tag, privacy: .public): \(line, privacy: .public)")
        }
        // Bugfender.i(tag, line)
    }

    public static func warn(
        tag: String = "VihSDK",
        message: String,
        shootId: String? = nil,
        messageId: String? = nil,
        traceId: String? = nil,
        error: Error? = nil
    ) {
        let line = format(message: message, shootId: shootId, messageId: messageId, traceId: traceId)
        if VihChatBotSDK.shared.config?.isDebug == true {
            if let error = error {
                logger.warning("\(tag, privacy: .public): \(line, privacy: .public) | \(String(describing: type(of: error)), privacy: .public): \(error.localizedDescription, privacy: .public)")
            } else {
                logger.warning("\(tag, privacy: .public): \(line, privacy: .public)")
            }
        }
    }

    public static func error(
        tag: String = "VihSDK",
        message: String,
        shootId: String? = nil,
        messageId: String? = nil,
        traceId: String? = nil,
        error: Error? = nil
    ) {
        let line = format(message: message, shootId: shootId, messageId: messageId, traceId: traceId)
        if VihChatBotSDK.shared.config?.isDebug == true {
            if let error = error {
                logger.error("\(tag, privacy: .public): \(line, privacy: .public) | \(String(describing: type(of: error)), privacy: .public): \(error.localizedDescription, privacy: .public)")
            } else {
                logger.error("\(tag, privacy: .public): \(line, privacy: .public)")
            }
        }
    }

    private static func format(
        message: String, shootId: String?, messageId: String?, traceId: String?
    ) -> String {
        let s = shootId ?? "-"
        let m = messageId ?? "-"
        let t = traceId ?? "-"
        return "[shoot=\(s) msg=\(m) trace=\(t)] \(message)"
    }
}
