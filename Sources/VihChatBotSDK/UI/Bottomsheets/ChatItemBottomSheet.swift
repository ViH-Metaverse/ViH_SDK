import UIKit

/// Mirrors `ui/bottomsheet/ChatItemBottomSheetFragment.kt`. Modal action sheet
/// shown on long-press of a chat row (copy, reply, delete, etc).
public final class ChatItemBottomSheet: UIViewController {

    public struct Action {
        public let title: String
        public let handler: () -> Void
        public init(title: String, handler: @escaping () -> Void) {
            self.title = title
            self.handler = handler
        }
    }

    public var actions: [Action] = []

    public override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        if let sheet = sheetPresentationController {
            sheet.detents = [.medium()]
        }
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 8
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16)
        ])
        for action in actions {
            let button = UIButton(type: .system)
            button.setTitle(action.title, for: .normal)
            button.contentHorizontalAlignment = .leading
            button.addAction(UIAction { [weak self] _ in
                action.handler()
                self?.dismiss(animated: true)
            }, for: .touchUpInside)
            stack.addArrangedSubview(button)
        }
    }
}
