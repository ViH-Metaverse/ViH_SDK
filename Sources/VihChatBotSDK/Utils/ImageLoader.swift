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

    public static func load(
        into imageView: UIImageView,
        url: String?,
        placeholderName: String? = nil,
        onError: (() -> Void)? = nil
    ) {
        if let name = placeholderName {
            imageView.image = UIImage(named: name)
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
                imageView.image = image
            }
        }
        task.resume()
    }
}
