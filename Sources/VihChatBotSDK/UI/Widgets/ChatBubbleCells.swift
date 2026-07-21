import UIKit

/// Shared chat visual tokens, matched to the Android layouts (item_left_chat / item_right_chat /
/// item_chat_rv). Received bubbles are a fixed light grey (they don't flip for dark mode on
/// Android), sent bubbles use the tenant accent.
private enum ChatStyle {
    static let receivedBubble = UIColor(hex: "#F7F7F7") ?? .secondarySystemBackground
    static let receivedText = UIColor(hex: "#1C2020") ?? .label
    static let timeText = UIColor(hex: "#828282") ?? .secondaryLabel
    static var accent: UIColor { DynamicThemeManager.shared.palette.primaryColor }
    static let radius: CGFloat = 18

    /// Backend timestamps look like "Jul, 13 2026 12:59:08" → render as "12:59 pm".
    static func time(_ raw: String) -> String {
        guard !raw.isEmpty else { return "" }
        let parser = DateFormatter()
        parser.locale = Locale(identifier: "en_US_POSIX")
        for fmt in ["MMM, dd yyyy HH:mm:ss", "MMM, d yyyy HH:mm:ss"] {
            parser.dateFormat = fmt
            if let d = parser.date(from: raw) {
                let out = DateFormatter()
                out.locale = Locale(identifier: "en_US_POSIX")
                out.dateFormat = "h:mm a"
                return out.string(from: d).lowercased()
            }
        }
        return ""
    }
}

// MARK: - Received (bot / enterprise)

public final class LeftChatCell: UITableViewCell {

    private let bubble = UIView()
    private let label = UILabel()
    private let timeLabel = UILabel()
    private let chipsStack = UIStackView()     // suggested_questions
    private let buttonsStack = UIStackView()   // GLM interactive buttons
    private let contentStack = UIStackView()

    /// Fired when a suggestion chip is tapped (send its text as the next message).
    public var onChipTap: ((String) -> Void)?
    /// Fired when a GLM interactive button is tapped.
    public var onButtonTap: ((InteractiveButton) -> Void)?

    private var chips: [String] = []
    private var buttons: [InteractiveButton] = []

    public override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        selectionStyle = .none
        backgroundColor = .clear
        contentView.backgroundColor = .clear

        bubble.backgroundColor = ChatStyle.receivedBubble
        bubble.layer.cornerRadius = ChatStyle.radius
        // Rounded except the bottom-left corner (mirrors chat_bubble_receiver).
        bubble.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner, .layerMaxXMaxYCorner]
        bubble.translatesAutoresizingMaskIntoConstraints = false

        label.numberOfLines = 0
        label.font = .systemFont(ofSize: 15)
        label.textColor = ChatStyle.receivedText

        chipsStack.axis = .vertical
        chipsStack.spacing = 6
        chipsStack.isHidden = true

        buttonsStack.axis = .vertical
        buttonsStack.spacing = 6
        buttonsStack.isHidden = true

        timeLabel.font = .systemFont(ofSize: 10)
        timeLabel.textColor = ChatStyle.timeText

        contentStack.axis = .vertical
        contentStack.spacing = 8
        contentStack.alignment = .fill
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        [label, chipsStack, buttonsStack, timeLabel].forEach { contentStack.addArrangedSubview($0) }

        contentView.addSubview(bubble)
        bubble.addSubview(contentStack)

        NSLayoutConstraint.activate([
            bubble.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 4),
            bubble.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -4),
            bubble.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 12),
            bubble.trailingAnchor.constraint(lessThanOrEqualTo: contentView.trailingAnchor, constant: -56),

            contentStack.topAnchor.constraint(equalTo: bubble.topAnchor, constant: 10),
            contentStack.bottomAnchor.constraint(equalTo: bubble.bottomAnchor, constant: -8),
            contentStack.leadingAnchor.constraint(equalTo: bubble.leadingAnchor, constant: 12),
            contentStack.trailingAnchor.constraint(equalTo: bubble.trailingAnchor, constant: -12)
        ])
    }
    required init?(coder: NSCoder) { fatalError("not supported") }

    public override func prepareForReuse() {
        super.prepareForReuse()
        onChipTap = nil
        onButtonTap = nil
        chips = []
        buttons = []
        chipsStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        buttonsStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        chipsStack.isHidden = true
        buttonsStack.isHidden = true
    }

    public func configure(with message: MessageModel) {
        label.text = message.message
        timeLabel.text = ChatStyle.time(message.created_at)

        buttons = (message.interactive?.buttons ?? []).filter { !($0.value ?? "").isEmpty }
        // Interactive buttons supersede the mirrored suggestion chips (avoid duplicate rows).
        chips = buttons.isEmpty ? (message.suggested_questions ?? []).filter { !$0.isEmpty } : []

        buttonsStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        for (i, b) in buttons.enumerated() { buttonsStack.addArrangedSubview(makeButton(b, index: i)) }
        buttonsStack.isHidden = buttons.isEmpty

        chipsStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        for (i, c) in chips.enumerated() { chipsStack.addArrangedSubview(makeChip(c, index: i)) }
        chipsStack.isHidden = chips.isEmpty
    }

    private func makeChip(_ text: String, index: Int) -> UIView {
        let row = UIControl()
        row.backgroundColor = ChatStyle.accent.withAlphaComponent(0.08)
        row.layer.cornerRadius = 10
        row.layer.borderWidth = 1
        row.layer.borderColor = ChatStyle.accent.withAlphaComponent(0.35).cgColor
        row.tag = index
        row.addTarget(self, action: #selector(chipTapped(_:)), for: .touchUpInside)

        let lbl = UILabel()
        lbl.text = text
        lbl.font = .systemFont(ofSize: 13)
        lbl.textColor = ChatStyle.receivedText
        lbl.numberOfLines = 0
        lbl.translatesAutoresizingMaskIntoConstraints = false

        let arrow = UIImageView(image: UIImage(systemName: "chevron.right"))
        arrow.tintColor = ChatStyle.accent
        arrow.setContentHuggingPriority(.required, for: .horizontal)
        arrow.translatesAutoresizingMaskIntoConstraints = false

        row.addSubview(lbl); row.addSubview(arrow)
        NSLayoutConstraint.activate([
            lbl.leadingAnchor.constraint(equalTo: row.leadingAnchor, constant: 10),
            lbl.topAnchor.constraint(equalTo: row.topAnchor, constant: 8),
            lbl.bottomAnchor.constraint(equalTo: row.bottomAnchor, constant: -8),
            arrow.leadingAnchor.constraint(equalTo: lbl.trailingAnchor, constant: 8),
            arrow.trailingAnchor.constraint(equalTo: row.trailingAnchor, constant: -10),
            arrow.centerYAnchor.constraint(equalTo: row.centerYAnchor),
            arrow.widthAnchor.constraint(equalToConstant: 12)
        ])
        return row
    }

    private func makeButton(_ button: InteractiveButton, index: Int) -> UIButton {
        let accent = ChatStyle.accent
        let btn = UIButton(type: .system)
        btn.setTitle((button.label?.isEmpty == false ? button.label : button.value) ?? "", for: .normal)
        btn.titleLabel?.font = .systemFont(ofSize: 13, weight: .medium)
        btn.titleLabel?.numberOfLines = 0
        btn.setTitleColor(accent, for: .normal)
        btn.layer.borderColor = accent.cgColor
        btn.layer.borderWidth = 1
        btn.layer.cornerRadius = 14
        btn.contentEdgeInsets = UIEdgeInsets(top: 7, left: 12, bottom: 7, right: 12)
        btn.tag = index
        btn.addTarget(self, action: #selector(buttonTapped(_:)), for: .touchUpInside)
        return btn
    }

    @objc private func chipTapped(_ sender: UIControl) {
        guard sender.tag >= 0, sender.tag < chips.count else { return }
        onChipTap?(chips[sender.tag])
    }

    @objc private func buttonTapped(_ sender: UIButton) {
        guard sender.tag >= 0, sender.tag < buttons.count else { return }
        onButtonTap?(buttons[sender.tag])
    }
}

// MARK: - Sent (user)

public final class RightChatCell: UITableViewCell {

    private let bubble = UIView()
    private let label = UILabel()
    private let timeLabel = UILabel()

    public override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        selectionStyle = .none
        backgroundColor = .clear
        contentView.backgroundColor = .clear

        bubble.backgroundColor = ChatStyle.accent
        bubble.layer.cornerRadius = ChatStyle.radius
        // Rounded except the top-right corner (mirrors chat_bubble_sender).
        bubble.layer.maskedCorners = [.layerMinXMinYCorner, .layerMinXMaxYCorner, .layerMaxXMaxYCorner]
        bubble.translatesAutoresizingMaskIntoConstraints = false

        label.numberOfLines = 0
        label.font = .systemFont(ofSize: 15)
        label.textColor = .white

        timeLabel.font = .systemFont(ofSize: 10)
        timeLabel.textColor = UIColor.white.withAlphaComponent(0.8)
        timeLabel.textAlignment = .right

        let stack = UIStackView(arrangedSubviews: [label, timeLabel])
        stack.axis = .vertical
        stack.spacing = 4
        stack.alignment = .fill
        stack.translatesAutoresizingMaskIntoConstraints = false

        contentView.addSubview(bubble)
        bubble.addSubview(stack)

        NSLayoutConstraint.activate([
            bubble.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 4),
            bubble.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -4),
            bubble.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -12),
            bubble.leadingAnchor.constraint(greaterThanOrEqualTo: contentView.leadingAnchor, constant: 56),

            stack.topAnchor.constraint(equalTo: bubble.topAnchor, constant: 8),
            stack.bottomAnchor.constraint(equalTo: bubble.bottomAnchor, constant: -8),
            stack.leadingAnchor.constraint(equalTo: bubble.leadingAnchor, constant: 12),
            stack.trailingAnchor.constraint(equalTo: bubble.trailingAnchor, constant: -12)
        ])
    }
    required init?(coder: NSCoder) { fatalError("not supported") }

    public func configure(with message: MessageModel) {
        label.text = message.message
        timeLabel.text = ChatStyle.time(message.created_at)
    }
}

// MARK: - Typing indicator

public final class LeftProgressCell: UITableViewCell {

    private let bubble = UIView()
    private let dot1 = UIView()
    private let dot2 = UIView()
    private let dot3 = UIView()

    public override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        selectionStyle = .none
        backgroundColor = .clear
        contentView.backgroundColor = .clear

        bubble.backgroundColor = ChatStyle.receivedBubble
        bubble.layer.cornerRadius = 14
        bubble.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner, .layerMaxXMaxYCorner]
        bubble.translatesAutoresizingMaskIntoConstraints = false

        for dot in [dot1, dot2, dot3] {
            dot.backgroundColor = UIColor(hex: "#828282") ?? .systemGray
            dot.layer.cornerRadius = 4
            dot.translatesAutoresizingMaskIntoConstraints = false
            bubble.addSubview(dot)
        }

        contentView.addSubview(bubble)

        NSLayoutConstraint.activate([
            bubble.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 4),
            bubble.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -4),
            bubble.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 12),
            bubble.heightAnchor.constraint(equalToConstant: 30),
            bubble.widthAnchor.constraint(equalToConstant: 58),

            dot1.leadingAnchor.constraint(equalTo: bubble.leadingAnchor, constant: 11),
            dot1.centerYAnchor.constraint(equalTo: bubble.centerYAnchor),
            dot1.widthAnchor.constraint(equalToConstant: 8),
            dot1.heightAnchor.constraint(equalToConstant: 8),

            dot2.leadingAnchor.constraint(equalTo: dot1.trailingAnchor, constant: 5),
            dot2.centerYAnchor.constraint(equalTo: bubble.centerYAnchor),
            dot2.widthAnchor.constraint(equalToConstant: 8),
            dot2.heightAnchor.constraint(equalToConstant: 8),

            dot3.leadingAnchor.constraint(equalTo: dot2.trailingAnchor, constant: 5),
            dot3.centerYAnchor.constraint(equalTo: bubble.centerYAnchor),
            dot3.widthAnchor.constraint(equalToConstant: 8),
            dot3.heightAnchor.constraint(equalToConstant: 8)
        ])

        startAnimating()
    }
    required init?(coder: NSCoder) { fatalError("not supported") }

    private func startAnimating() {
        for (index, dot) in [dot1, dot2, dot3].enumerated() {
            UIView.animate(
                withDuration: 0.6,
                delay: TimeInterval(index) * 0.2,
                options: [.repeat, .autoreverse, .curveEaseInOut],
                animations: { dot.alpha = 0.3 }
            )
        }
    }
}
