import UIKit
import AVKit

/// Mirrors `ui/activity/VideoActivity.kt`. Uses `AVPlayer` for both remote
/// streaming URLs and local `file://` paths — equivalent to the Android
/// ExoPlayer-based viewer.
public final class VideoViewController: AVPlayerViewController {

    public enum MediaType { case image, video }

    public let mediaURL: String
    public let mediaType: MediaType
    public let channel: EnterPriseModel?

    public init(mediaURL: String, mediaType: MediaType, channel: EnterPriseModel? = nil) {
        self.mediaURL = mediaURL
        self.mediaType = mediaType
        self.channel = channel
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError("not supported") }

    public override func viewDidLoad() {
        super.viewDidLoad()
        if mediaType == .video, let url = URL(string: mediaURL) {
            player = AVPlayer(url: url)
            player?.play()
        }
    }
}
