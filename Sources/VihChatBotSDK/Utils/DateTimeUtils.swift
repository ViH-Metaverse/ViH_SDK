import Foundation

/// Mirrors small date helpers in `utils/utils.kt` and `adapters/DateTimeUtils.kt`.
public enum DateTimeUtils {

    public static let isoFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    /// Returns the current date in ISO-8601, matching `currentDateTime()` on Android.
    public static func currentDateTime() -> String {
        isoFormatter.string(from: Date())
    }

    /// Convenience header label ("Today" / "Yesterday" / "dd MMM yyyy") used by
    /// the chat list adapter. Mirrors Android `DateTimeUtils` formatting.
    public static func headerLabel(from isoString: String) -> String {
        guard let date = isoFormatter.date(from: isoString) else { return "" }
        let calendar = Calendar.current
        if calendar.isDateInToday(date) { return "Today" }
        if calendar.isDateInYesterday(date) { return "Yesterday" }
        let df = DateFormatter()
        df.dateFormat = "dd MMM yyyy"
        return df.string(from: date)
    }
}
