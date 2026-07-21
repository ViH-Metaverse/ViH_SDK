import UIKit

/// Mirrors the `item_chat_input_box.xml` row Android binds inside `ChatActivity`: a pill text
/// field with a round accent-coloured send button.
public final class ChatInputBar: UIView, UITextViewDelegate {

    public var onSend: ((String) -> Void)?

    private let textView = UITextView()
    private let placeholder = UILabel()
    private let sendButton = UIButton(type: .system)

    private var accent: UIColor { DynamicThemeManager.shared.palette.primaryColor }

    public override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .systemBackground
        setup()
    }
    required init?(coder: NSCoder) { fatalError("not supported") }

    private func setup() {
        // Hairline separator along the top edge.
        let separator = UIView()
        separator.backgroundColor = .separator
        separator.translatesAutoresizingMaskIntoConstraints = false
        addSubview(separator)

        textView.font = .systemFont(ofSize: 15)
        textView.backgroundColor = UIColor(hex: "#F5F5F7") ?? .secondarySystemBackground
        textView.layer.cornerRadius = 20
        textView.textContainerInset = UIEdgeInsets(top: 9, left: 14, bottom: 9, right: 14)
        textView.translatesAutoresizingMaskIntoConstraints = false
        textView.delegate = self
        addSubview(textView)

        placeholder.text = "Message"
        placeholder.font = .systemFont(ofSize: 15)
        placeholder.textColor = UIColor(hex: "#828282") ?? .placeholderText
        placeholder.translatesAutoresizingMaskIntoConstraints = false
        textView.addSubview(placeholder)

        sendButton.backgroundColor = accent
        sendButton.tintColor = .white
        sendButton.setImage(UIImage(systemName: "paperplane.fill"), for: .normal)
        sendButton.layer.cornerRadius = 22
        sendButton.translatesAutoresizingMaskIntoConstraints = false
        sendButton.addTarget(self, action: #selector(sendTapped), for: .touchUpInside)
        addSubview(sendButton)

        NSLayoutConstraint.activate([
            separator.topAnchor.constraint(equalTo: topAnchor),
            separator.leadingAnchor.constraint(equalTo: leadingAnchor),
            separator.trailingAnchor.constraint(equalTo: trailingAnchor),
            separator.heightAnchor.constraint(equalToConstant: 0.5),

            textView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 12),
            textView.topAnchor.constraint(equalTo: topAnchor, constant: 8),
            textView.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -8),
            textView.heightAnchor.constraint(greaterThanOrEqualToConstant: 40),
            textView.heightAnchor.constraint(lessThanOrEqualToConstant: 120),

            placeholder.leadingAnchor.constraint(equalTo: textView.leadingAnchor, constant: 18),
            placeholder.centerYAnchor.constraint(equalTo: textView.centerYAnchor),

            sendButton.leadingAnchor.constraint(equalTo: textView.trailingAnchor, constant: 8),
            sendButton.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -12),
            sendButton.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -8),
            sendButton.widthAnchor.constraint(equalToConstant: 44),
            sendButton.heightAnchor.constraint(equalToConstant: 44)
        ])
    }

    public func textViewDidChange(_ textView: UITextView) {
        placeholder.isHidden = !textView.text.isEmpty
    }

    @objc private func sendTapped() {
        let text = textView.text ?? ""
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        onSend?(text)
        textView.text = ""
        placeholder.isHidden = false
    }
}
