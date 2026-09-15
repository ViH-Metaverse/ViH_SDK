import UIKit

/// Mirrors `utils/imageloader/CustomImageLoader.kt` — lightweight in-memory cache
/// + URLSession download. Replaces the Glide-style API. Use the iOS host's
/// own image-loading library (SDWebImage, Kingfisher, Nuke) for full feature parity.
public enum ImageLoader {

    private static let cache: NSCache<NSString, UIImage> = {
        let c = NSCache<NSString, UIImage>()
        c.totalCostLimit = 50 * 1024 * 1024 // 50MB ceiling
        return c
    }()

    /// The URL each image view is currently waiting on. Table/collection cells are recycled while
    /// their download is still in flight, so without this a row shows whichever request finishes
    /// last — another enterprise's logo, or a stale one that then never gets replaced.
    /// Weak keys: an image view that goes away drops out on its own.
    private static let inFlight = NSMapTable<UIImageView, NSString>.weakToStrongObjects()

    /// `UIImage(named:)` on its own searches `Bundle.main`, so an asset shipped with the package
    /// is never found from a host app. Look in the package bundle first, then the host's.
    /// Returns nil when neither has the asset — the package currently ships none.
    public static func placeholder(named name: String) -> UIImage? {
        UIImage(named: name, in: Bundle.module, compatibleWith: nil) ?? UIImage(named: name)
    }

    /// Stand-in for a channel/enterprise avatar, used where a small circular logo is expected and
    /// no bitmap placeholder resolved — those views used to render as nothing at all. A template
    /// image, so the call site's `tintColor` decides the colour; left untinted it would inherit
    /// the window's blue. Deliberately NOT a default: a hero/media slot reads better as an empty
    /// grey box than as one giant glyph.
    public static let avatarPlaceholder: UIImage? = UIImage(
        systemName: "building.2.crop.circle.fill",
        withConfiguration: UIImage.SymbolConfiguration(pointSize: 48)
    )

    public static func load(
        into imageView: UIImageView,
        url: String?,
        placeholderName: String? = nil,
        fallback: UIImage? = nil,
        onError: (() -> Void)? = nil
    ) {
        inFlight.setObject((url ?? "") as NSString, forKey: imageView)
        if let name = placeholderName {
            imageView.image = placeholder(named: name) ?? fallback
        }
        // Staging fixtures sometimes return placeholder strings like "profile_image"
        // in URL fields. `URL(string:)` happily accepts those as relative URLs,
        // then URLSession fails with NSURLErrorUnsupportedURL (-1002). Require an
        // http(s) scheme so junk values fall through to the placeholder instead.
        guard let urlStr = url, !urlStr.isEmpty,
              let url = URL(string: urlStr),
              let scheme = url.scheme?.lowercased(),
              scheme == "http" || scheme == "https" else {
            onError?()
            return
        }
        if let cached = cache.object(forKey: urlStr as NSString) {
            imageView.image = cached
            return
        }
        let task = URLSession.shared.dataTask(with: url) { data, _, error in
            guard error == nil, let data = data, let image = UIImage(data: data) else {
                DispatchQueue.main.async { onError?() }
                return
            }
            cache.setObject(image, forKey: urlStr as NSString)
            DispatchQueue.main.async {
                // Drop the result if the view was recycled onto another URL meanwhile.
                guard inFlight.object(forKey: imageView) == (urlStr as NSString) else { return }
                imageView.image = image
            }
        }
        task.resume()
    }
}
