import UIKit

/// iOS counterpart to `ui/widget/VoicebotOrbView.kt`. Pulsing circle whose
/// amplitude is driven by the Ultravox session status. The Android view uses
/// `Canvas` + a custom `View`; we use a `CAShapeLayer` with a `CABasicAnimation`.
public final class VoicebotOrbView: UIView {

    private let orbLayer = CAShapeLayer()
    private var level: CGFloat = 0

    public override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        orbLayer.fillColor = UIColor.systemTeal.cgColor
        layer.addSublayer(orbLayer)
    }
    required init?(coder: NSCoder) { fatalError("not supported") }

    public override func layoutSubviews() {
        super.layoutSubviews()
        refreshPath()
    }

    /// `level` in [0, 1]; 0 = idle, 1 = peak speaking.
    public func setSpeakingLevel(_ level: CGFloat) {
        self.level = max(0, min(level, 1))
        refreshPath()
    }

    private func refreshPath() {
        let minRadius = min(bounds.width, bounds.height) * 0.30
        let maxRadius = min(bounds.width, bounds.height) * 0.45
        let radius = minRadius + (maxRadius - minRadius) * level
        let center = CGPoint(x: bounds.midX, y: bounds.midY)
        let path = UIBezierPath(
            arcCenter: center, radius: radius,
            startAngle: 0, endAngle: .pi * 2,
            clockwise: true
        )
        let anim = CABasicAnimation(keyPath: "path")
        anim.duration = 0.25
        anim.fromValue = orbLayer.path
        anim.toValue = path.cgPath
        orbLayer.add(anim, forKey: "orb")
        orbLayer.path = path.cgPath
    }
}
