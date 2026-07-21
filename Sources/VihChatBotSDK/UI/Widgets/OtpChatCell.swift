import UIKit

/// Renders an OTP message (`template_type == 1`) as a dedicated, visually-distinct OTP card —
/// the iOS counterpart of Android's OTP message viewholder (OTP_MOBILE_SPEC §3). Unlike a normal
/// received bubble or a promotional/transactional template, it shows a lock badge, a
/// "One-time password" label, the extracted code in monospace with a Copy affordance, and an
/// optional origin badge driven by `source` ("api" / "portal").
///
/// OTP text is rendered verbatim — it deliberately does NOT run through the link-preview /
/// rich-media template path (OTP_MOBILE_SPEC §3).
public final class OtpChatCell: UITableViewCell {

    private let bubble = UIView()
    private let headerRow = UIStackView()
    private let lockIcon = UIImageView()
    private let titleLabel = UILabel()
    private let sourceBadge = PaddedLabel()
    private let bodyLabel = UILabel()
    private let codeRow = UIStackView()
    private let codeLabel = UILabel()
    private let copyButton = UIButton(type: .system)
    private let timeLabel = UILabel()

    private var codeToCopy: String?

    public override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        selectionStyle = .none
        backgroundColor = .clear
        contentView.backgroundColor = .clear

        let accent = DynamicThemeManager.shared.palette.primaryColor

        // Fixed light card (matches Android's #F3F2FE bg_otp_card). Must NOT be a translucent
        // accent — over the dark chat background in dark mode that renders dark, and the
        // intentionally fixed-dark body/code text (#1C2020 below) becomes invisible. Keeping the
        // card fixed-light means the OTP stays a distinct, readable card in both themes.
        bubble.backgroundColor = UIColor(hex: "#F3F2FE") ?? accent.withAlphaComponent(0.06)
        bubble.layer.cornerRadius = 16
        bubble.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner, .layerMaxXMaxYCorner]
        bubble.layer.borderWidth = 1
        bubble.layer.borderColor = accent.withAlphaComponent(0.35).cgColor
        bubble.translatesAutoresizingMaskIntoConstraints = false

        lockIcon.image = UIImage(systemName: "lock.shield.fill")?
            .withConfiguration(UIImage.SymbolConfiguration(pointSize: 15, weight: .semibold))
        lockIcon.tintColor = accent
        lockIcon.setContentHuggingPriority(.required, for: .horizontal)

        titleLabel.text = "One-time password"
        titleLabel.font = .systemFont(ofSize: 12, weight: .semibold)
        titleLabel.textColor = accent

        sourceBadge.font = .systemFont(ofSize: 10, weight: .semibold)
        sourceBadge.textColor = accent
        sourceBadge.backgroundColor = accent.withAlphaComponent(0.14)
        sourceBadge.layer.cornerRadius = 8
        sourceBadge.layer.masksToBounds = true
        sourceBadge.insets = UIEdgeInsets(top: 2, left: 8, bottom: 2, right: 8)
        sourceBadge.setContentHuggingPriority(.required, for: .horizontal)

        headerRow.axis = .horizontal
        headerRow.spacing = 6
        headerRow.alignment = .center
        [lockIcon, titleLabel, UIView(), sourceBadge].forEach { headerRow.addArrangedSubview($0) }

        bodyLabel.numberOfLines = 0
        bodyLabel.font = .systemFont(ofSize: 15)
        bodyLabel.textColor = UIColor(hex: "#1C2020") ?? .label

        // Prominent code chip + copy button (only shown when a numeric code is detected).
        codeLabel.font = .monospacedDigitSystemFont(ofSize: 22, weight: .bold)
        codeLabel.textColor = UIColor(hex: "#1C2020") ?? .label
        codeLabel.setContentHuggingPriority(.defaultLow, for: .horizontal)

        copyButton.setTitle("Copy", for: .normal)
        copyButton.setImage(UIImage(systemName: "doc.on.doc"), for: .normal)
        copyButton.tintColor = accent
        copyButton.setTitleColor(accent, for: .normal)
        copyButton.titleLabel?.font = .systemFont(ofSize: 13, weight: .semibold)
        copyButton.layer.borderColor = accent.cgColor
        copyButton.layer.borderWidth = 1
        copyButton.layer.cornerRadius = 14
        copyButton.contentEdgeInsets = UIEdgeInsets(top: 5, left: 12, bottom: 5, right: 12)
        copyButton.imageEdgeInsets = UIEdgeInsets(top: 0, left: -4, bottom: 0, right: 4)
        copyButton.setContentHuggingPriority(.required, for: .horizontal)
        copyButton.addTarget(self, action: #selector(copyTapped), for: .touchUpInside)

        let codeCard = UIView()
        codeCard.backgroundColor = .white
        codeCard.layer.cornerRadius = 12
        codeCard.layer.borderWidth = 1
        codeCard.layer.borderColor = accent.withAlphaComponent(0.25).cgColor
        codeRow.axis = .horizontal
        codeRow.spacing = 10
        codeRow.alignment = .center
        codeRow.translatesAutoresizingMaskIntoConstraints = false
        [codeLabel, copyButton].forEach { codeRow.addArrangedSubview($0) }
        codeCard.addSubview(codeRow)
        NSLayoutConstraint.activate([
            codeRow.topAnchor.constraint(equalTo: codeCard.topAnchor, constant: 10),
            codeRow.bottomAnchor.constraint(equalTo: codeCard.bottomAnchor, constant: -10),
            codeRow.leadingAnchor.constraint(equalTo: codeCard.leadingAnchor, constant: 12),
            codeRow.trailingAnchor.constraint(equalTo: codeCard.trailingAnchor, constant: -12)
        ])

        timeLabel.font = .systemFont(ofSize: 10)
        timeLabel.textColor = UIColor(hex: "#828282") ?? .secondaryLabel

        let stack = UIStackView(arrangedSubviews: [headerRow, bodyLabel, codeCard, timeLabel])
        stack.axis = .vertical
        stack.spacing = 8
        stack.setCustomSpacing(4, after: codeCard)
        stack.translatesAutoresizingMaskIntoConstraints = false
        bubble.addSubview(stack)
        contentView.addSubview(bubble)

        NSLayoutConstraint.activate([
            bubble.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 4),
            bubble.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -4),
            bubble.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 12),
            bubble.trailingAnchor.constraint(lessThanOrEqualTo: contentView.trailingAnchor, constant: -40),
            bubble.widthAnchor.constraint(equalToConstant: 280),

            stack.topAnchor.constraint(equalTo: bubble.topAnchor, constant: 10),
            stack.bottomAnchor.constraint(equalTo: bubble.bottomAnchor, constant: -10),
            stack.leadingAnchor.constraint(equalTo: bubble.leadingAnchor, constant: 12),
            stack.trailingAnchor.constraint(equalTo: bubble.trailingAnchor, constant: -12)
        ])
    }
    public required init?(coder: NSCoder) { fatalError("not supported") }

    public override func prepareForReuse() {
        super.prepareForReuse()
        codeToCopy = nil
        copyButton.setTitle("Copy", for: .normal)
    }

    public func configure(with message: MessageModel) {
        // OTP body text: cpaas_json.msg (API/CPaaS OTPs) or the plain message field.
        let raw = message.cpaas_json?.msg?.isEmpty == false ? message.cpaas_json?.msg : message.message
        let body = (raw ?? "")
            .replacingOccurrences(of: "<br />", with: "\n")
            .replacingOccurrences(of: "<br>", with: "\n")
        bodyLabel.text = body
        bodyLabel.isHidden = body.isEmpty

        // Pull the first 4–8 digit run out of the body as the copyable code.
        let code = OtpChatCell.extractCode(from: body)
        codeToCopy = code ?? body
        if let code = code {
            codeLabel.text = code.map(String.init).joined(separator: " ")
            codeRow.superview?.isHidden = false
        } else {
            // No numeric code found — hide the code chip; Copy still copies the message text.
            codeRow.superview?.isHidden = true
        }

        // Origin badge (optional): "VIA API" / "VIA PORTAL".
        if let s = message.source, !s.isEmpty {
            sourceBadge.text = "VIA \(s.uppercased())"
            sourceBadge.isHidden = false
        } else {
            sourceBadge.isHidden = true
        }

        timeLabel.text = TemplateChatCell.time(message.created_at)
    }

    @objc private func copyTapped() {
        UIPasteboard.general.string = codeToCopy
        copyButton.setTitle("Copied", for: .normal)
        // Revert the label without a timer dependency — reset happens on next reuse/config,
        // but give immediate feedback here.
        UIView.performWithoutAnimation { copyButton.layoutIfNeeded() }
    }

    /// First run of 4–8 digits in the text (the OTP), ignoring surrounding words. Returns nil
    /// when the body has no such code (e.g. a non-numeric one-time link).
    static func extractCode(from text: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: "\\b\\d{4,8}\\b") else { return nil }
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        guard let match = regex.firstMatch(in: text, range: range),
              let r = Range(match.range, in: text) else { return nil }
        return String(text[r])
    }
}

/// UILabel with content insets, used for the pill-shaped source badge.
final class PaddedLabel: UILabel {
    var insets: UIEdgeInsets = .zero
    override func drawText(in rect: CGRect) { super.drawText(in: rect.inset(by: insets)) }
    override var intrinsicContentSize: CGSize {
        let s = super.intrinsicContentSize
        return CGSize(width: s.width + insets.left + insets.right, height: s.height + insets.top + insets.bottom)
    }
}
