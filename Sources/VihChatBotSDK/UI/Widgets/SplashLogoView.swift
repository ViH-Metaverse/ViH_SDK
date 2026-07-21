import UIKit

/// The VIH Messenger brand mark, recreated from Android's `ic_logo_splah.xml` vector so the iOS
/// splash matches the Android splash exactly: a rounded "messenger" blob filled with the
/// blue→purple→pink brand gradient, with a white check/swoosh and a grey fold on top.
/// Resolution-independent (drawn from the original 151×141 viewport paths), so no asset export.
public final class SplashLogoView: UIView {

    // Original Android drawable viewport.
    private let viewportWidth: CGFloat = 151
    private let viewportHeight: CGFloat = 141

    // Blob outline (the vector's clip-path) — used as the gradient mask.
    private let blobPathData =
        "M130.18,25.96L98.15,117.29C94.04,129.01 78.91,131.84 70.85,122.4C49.89,97.88 28.92,73.35 7.96,48.83C-0.1,39.39 5,24.84 17.17,22.56L112.1,4.8C124.27,2.52 134.29,14.25 130.18,25.96Z"
    private let whitePathData =
        "M24.64,41.66L40.6,58.71L41.74,87.75L56.83,74.84L76.95,93.65L113.75,24.87L24.64,41.66Z"
    private let greyPathData =
        "M41.74,87.75L56.83,74.84L49.06,66.15L98.64,31.09L44.66,58.32L41.74,87.75Z"

    private let gradientLayer = CAGradientLayer()
    private let blobMask = CAShapeLayer()
    private let whiteLayer = CAShapeLayer()
    private let greyLayer = CAShapeLayer()

    public override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear

        gradientLayer.colors = [
            (UIColor(hex: "#0049E6") ?? .systemBlue).cgColor,
            (UIColor(hex: "#9C15F7") ?? .systemPurple).cgColor,
            (UIColor(hex: "#FF4CF8") ?? .systemPink).cgColor
        ]
        gradientLayer.locations = [0, 0.49, 1]
        // Direction from the vector's linearGradient (viewport coords → unit space).
        gradientLayer.startPoint = CGPoint(x: 61.88 / viewportWidth, y: 159.15 / viewportHeight)
        gradientLayer.endPoint = CGPoint(x: 92.07 / viewportWidth, y: -30.65 / viewportHeight)
        gradientLayer.mask = blobMask
        layer.addSublayer(gradientLayer)

        whiteLayer.fillColor = UIColor.white.cgColor
        whiteLayer.fillRule = .evenOdd
        greyLayer.fillColor = (UIColor(hex: "#ACACAC") ?? .lightGray).cgColor
        greyLayer.fillRule = .evenOdd
        layer.addSublayer(whiteLayer)
        layer.addSublayer(greyLayer)
    }
    public required init?(coder: NSCoder) { fatalError("not supported") }

    public override func layoutSubviews() {
        super.layoutSubviews()
        gradientLayer.frame = bounds

        // Aspect-fit the 151×141 artwork into bounds, centered.
        let scale = min(bounds.width / viewportWidth, bounds.height / viewportHeight)
        let dx = (bounds.width - viewportWidth * scale) / 2
        let dy = (bounds.height - viewportHeight * scale) / 2
        var transform = CGAffineTransform(translationX: dx, y: dy).scaledBy(x: scale, y: scale)

        blobMask.path = SplashLogoView.path(from: blobPathData)?.copy(using: &transform)
        whiteLayer.path = SplashLogoView.path(from: whitePathData)?.copy(using: &transform)
        greyLayer.path = SplashLogoView.path(from: greyPathData)?.copy(using: &transform)
    }

    // MARK: - Minimal SVG path parser (absolute M / L / C / Z — all this artwork uses)

    private static func path(from data: String) -> CGPath? {
        let tokens = tokenize(data)
        let path = CGMutablePath()
        var i = 0
        var command: Character = " "
        func num(_ k: Int) -> CGFloat { CGFloat(Double(tokens[k]) ?? 0) }

        while i < tokens.count {
            let token = tokens[i]
            if let c = token.first, c.isLetter {
                command = c
                i += 1
                if c == "Z" || c == "z" { path.closeSubpath() }
                continue
            }
            switch command {
            case "M":
                path.move(to: CGPoint(x: num(i), y: num(i + 1))); i += 2
                command = "L" // extra coordinate pairs after an M are implicit line-tos
            case "L":
                path.addLine(to: CGPoint(x: num(i), y: num(i + 1))); i += 2
            case "C":
                path.addCurve(
                    to: CGPoint(x: num(i + 4), y: num(i + 5)),
                    control1: CGPoint(x: num(i), y: num(i + 1)),
                    control2: CGPoint(x: num(i + 2), y: num(i + 3))
                ); i += 6
            default:
                i += 1 // unexpected token — skip defensively
            }
        }
        return path
    }

    /// Splits path data into command letters and numeric tokens (handles negatives / decimals).
    private static func tokenize(_ data: String) -> [String] {
        var tokens: [String] = []
        var current = ""
        func flush() { if !current.isEmpty { tokens.append(current); current = "" } }
        for ch in data {
            if ch.isLetter {
                flush(); tokens.append(String(ch))
            } else if ch == "," || ch == " " {
                flush()
            } else if ch == "-" {
                // Start of a new negative number (unless it's an exponent sign).
                if !current.isEmpty && !current.hasSuffix("e") && !current.hasSuffix("E") { flush() }
                current.append(ch)
            } else {
                current.append(ch)
            }
        }
        flush()
        return tokens
    }
}
