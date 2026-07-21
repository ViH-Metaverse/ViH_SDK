import UIKit
import Combine

/// Mirrors `ui/activity/home/ChatActivity.kt`. The Android version inflates
/// `activity_chat.xml`; on iOS we build the same hierarchy programmatically:
///   - app bar (with title + avatar + voicebot button)
///   - background image / colour
///   - message list (UITableView replacing RecyclerView)
///   - input bar with emoji + send
///
/// FCM-driven refresh is delivered via `NotificationCenter` instead of
/// `LocalBroadcastManager`.
public final class ChatViewController: BaseViewController, UITableViewDataSource, UITableViewDelegate {

    public struct Inputs {
        public var sessionId: String
        public var channelName: String?
        public var channelImage: String?
        public var channel: EnterPriseModel?
        public var id: String?
        public var hashcode: String?
        public init(
            sessionId: String, channelName: String? = nil, channelImage: String? = nil,
            channel: EnterPriseModel? = nil, id: String? = nil, hashcode: String? = nil
        ) {
            self.sessionId = sessionId
            self.channelName = channelName
            self.channelImage = channelImage
            self.channel = channel
            self.id = id
            self.hashcode = hashcode
        }
    }

    public var inputs: Inputs

    /// The backend returns a non-reply placeholder when a message is routed to a chatbot flow
    /// (which may be unconfigured) — e.g. "Message handled by flow" / "Message handled by the
    /// flow", sometimes with is_flow == 0. Match on both distinctive tokens so minor wording
    /// differences ("the") are still suppressed, and we never render it as a bot bubble.
    static func isFlowAcknowledgement(_ message: String) -> Bool {
        let m = message.trimmingCharacters(in: .whitespacesAndNewlines)
        return m.range(of: "handled by", options: .caseInsensitive) != nil
            && m.range(of: "flow", options: .caseInsensitive) != nil
    }

    private lazy var viewModel = ChatViewModel(loaderHost: self)
    private var cancellables: [AnyCancellable] = []

    private let tableView = UITableView()
    private let backgroundImageView = UIImageView()
    private let titleLabel = UILabel()
    private let avatarView = UIImageView()
    private let voicebotButton = UIButton(type: .system)
    private let inputBar = ChatInputBar()
    private let emptyLabel = UILabel()
    private let loadingIndicator = UIActivityIndicatorView(style: .medium)

    /// Bottom pin of the input bar; its constant is raised while the keyboard is on screen so
    /// the text field + send button stay visible above it.
    private var inputBarBottomConstraint: NSLayoutConstraint!

    private var messages: [MessageModel] = []
    private var hasLoadedChats = false

    public init(inputs: Inputs) {
        self.inputs = inputs
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    public override func initView() {
        view.backgroundColor = .systemBackground
        setupBackground()
        setupNavBar()
        setupTableView()
        setupInputBar()
        applyVihSettings()
        registerKeyboardObservers()
    }

    public override func setObservers() {
        cancellables.append(viewModel.chatMessageLiveData.observe { [weak self] response in
            guard let self = self else { return }
            self.removeProgressMessage()
            guard response.status, let data = response.data else { return }
            // Preserve the session id so the conversation continues even when the reply itself
            // is suppressed below.
            self.inputs.sessionId = data.session_id ?? self.inputs.sessionId
            // Suppress flow acknowledgements: is_flow == 1 and/or a "…handled by flow" placeholder.
            if data.is_flow == 1 || Self.isFlowAcknowledgement(data.message) { return }
            self.append(MessageModel(
                session_id: data.session_id,
                message: data.message,
                suggested_questions: data.suggested_questions ?? [],
                sent_by: data.sent_by,
                created_at: data.created_at,
                updated_at: data.updated_at,
                session: self.inputs.sessionId,
                cpaas_json: nil,
                interactive: data.interactive
            ))
        })

        cancellables.append(viewModel.chatHistoryLiveData.observe { [weak self] response in
            guard let self = self else { return }
            self.loadingIndicator.stopAnimating()
            if let data = response.data {
                // Drop flow acknowledgements so a persisted "…handled by flow" placeholder
                // doesn't reappear as a bot bubble when history reloads.
                let display = data.filter {
                    $0.is_flow != 1 && !Self.isFlowAcknowledgement($0.message)
                }
                if !display.isEmpty {
                    self.messages = display
                    self.tableView.reloadData()
                    self.scrollToBottom()
                }
            }
            self.updateEmptyVisibility()
        })

        cancellables.append(viewModel.enterpriseDetails.observe { [weak self] response in
            guard let self = self, response.status else { return }
            self.inputs.channel = response.data
            self.updateEmptyVisibility()
        })

        cancellables.append(viewModel.errorLiveData.observe { [weak self] _ in
            self?.removeProgressMessage()
        })

        NotificationCenter.default.addObserver(
            self, selector: #selector(handleFcmMessage),
            name: AppConstants.fcmMessageReceived, object: nil
        )
    }

    public override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        if !hasLoadedChats {
            loadAllChats()
            hasLoadedChats = true
        }
    }

    deinit { NotificationCenter.default.removeObserver(self) }

    @objc private func handleFcmMessage() { loadAllChats() }

    private func setupBackground() {
        backgroundImageView.contentMode = .scaleAspectFill
        backgroundImageView.translatesAutoresizingMaskIntoConstraints = false
        view.insertSubview(backgroundImageView, at: 0)
        NSLayoutConstraint.activate([
            backgroundImageView.topAnchor.constraint(equalTo: view.topAnchor),
            backgroundImageView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            backgroundImageView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            backgroundImageView.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])
    }

    private func setupNavBar() {
        let stack = UIStackView()
        stack.axis = .horizontal
        stack.alignment = .center
        stack.spacing = 8

        avatarView.contentMode = .scaleAspectFill
        avatarView.layer.cornerRadius = 16
        avatarView.layer.masksToBounds = true
        avatarView.widthAnchor.constraint(equalToConstant: 32).isActive = true
        avatarView.heightAnchor.constraint(equalToConstant: 32).isActive = true

        titleLabel.text = (inputs.channelName?.isEmpty == false) ? inputs.channelName : "Chat"
        titleLabel.font = .systemFont(ofSize: 16, weight: .semibold)
        ImageLoader.load(into: avatarView, url: inputs.channelImage, placeholderName: "placeholder")

        stack.addArrangedSubview(avatarView)
        stack.addArrangedSubview(titleLabel)
        navigationItem.titleView = stack

        voicebotButton.setImage(UIImage(systemName: "phone.circle.fill"), for: .normal)
        voicebotButton.addTarget(self, action: #selector(launchVoicebot), for: .touchUpInside)
        navigationItem.rightBarButtonItem = UIBarButtonItem(customView: voicebotButton)

        let avatarTap = UITapGestureRecognizer(target: self, action: #selector(openCompanyProfile))
        stack.addGestureRecognizer(avatarTap)
        stack.isUserInteractionEnabled = true
    }

    private func setupTableView() {
        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.dataSource = self
        tableView.delegate = self
        tableView.separatorStyle = .none
        tableView.backgroundColor = .clear
        // Let the user swipe down over the messages to dismiss the keyboard.
        tableView.keyboardDismissMode = .interactive
        tableView.register(LeftChatCell.self, forCellReuseIdentifier: "left")
        tableView.register(RightChatCell.self, forCellReuseIdentifier: "right")
        tableView.register(LeftProgressCell.self, forCellReuseIdentifier: "progress")
        tableView.register(TemplateChatCell.self, forCellReuseIdentifier: "template")
        tableView.register(OtpChatCell.self, forCellReuseIdentifier: "otp")
        view.addSubview(tableView)

        emptyLabel.numberOfLines = 0
        emptyLabel.textAlignment = .center
        emptyLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(emptyLabel)

        loadingIndicator.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(loadingIndicator)

        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),

            emptyLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            emptyLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            emptyLabel.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 32),
            emptyLabel.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -32),

            loadingIndicator.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            loadingIndicator.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }

    private func setupInputBar() {
        inputBar.translatesAutoresizingMaskIntoConstraints = false
        inputBar.onSend = { [weak self] text in self?.send(text: text) }
        view.addSubview(inputBar)
        inputBarBottomConstraint = inputBar.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor)
        NSLayoutConstraint.activate([
            inputBar.topAnchor.constraint(equalTo: tableView.bottomAnchor),
            inputBar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            inputBar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            inputBarBottomConstraint
        ])
    }

    // MARK: - Keyboard avoidance

    private func registerKeyboardObservers() {
        NotificationCenter.default.addObserver(
            self, selector: #selector(handleKeyboardFrameChange(_:)),
            name: UIResponder.keyboardWillChangeFrameNotification, object: nil
        )
        NotificationCenter.default.addObserver(
            self, selector: #selector(handleKeyboardHide(_:)),
            name: UIResponder.keyboardWillHideNotification, object: nil
        )
    }

    @objc private func handleKeyboardFrameChange(_ note: Notification) {
        guard let info = note.userInfo,
              let endFrame = (info[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue)?.cgRectValue else { return }
        // How much the keyboard overlaps this view's bottom, minus the safe-area inset the
        // input bar is already pinned above (so we don't double-count the home-indicator gap).
        let overlap = max(0, view.bounds.maxY - view.convert(endFrame, from: nil).minY)
        let raise = overlap > 0 ? overlap - view.safeAreaInsets.bottom : 0
        animateInputBar(to: -max(0, raise), info: info)
    }

    @objc private func handleKeyboardHide(_ note: Notification) {
        animateInputBar(to: 0, info: note.userInfo)
    }

    private func animateInputBar(to constant: CGFloat, info: [AnyHashable: Any]?) {
        inputBarBottomConstraint.constant = constant
        let duration = (info?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double) ?? 0.25
        UIView.animate(withDuration: duration) {
            self.view.layoutIfNeeded()
            self.scrollToBottom()
        }
    }

    private func applyVihSettings() {
        guard let raw = VihChatBotSDK.shared.prefs?.vihSettings,
              let data = raw.data(using: .utf8),
              let settings = try? JSONDecoder().decode(SdkFeatureModel.self, from: data) else {
            return
        }
        if settings.background_style == "image", !settings.choose_other_image.isEmpty {
            ImageLoader.load(into: backgroundImageView, url: settings.choose_other_image, placeholderName: "placeholder")
        }
        if settings.background_style == "color", let color = UIColor(hex: settings.solid_color) {
            backgroundImageView.backgroundColor = color
        }
    }

    private func loadAllChats() {
        loadingIndicator.startAnimating()
        updateEmptyVisibility()
        let hashcode = inputs.hashcode?.nonBlank ?? VihChatBotSDK.shared.prefs?.hashcode
        let enterpriseId = inputs.id ?? ""

        if let id = inputs.id, !id.isEmpty, inputs.channel == nil {
            viewModel.getEnterpriseModel(showBlockingLoader: false, enterpriseId: id)
        }

        if let hashcode = hashcode {
            viewModel.getChatHistoryResponse(
                showBlockingLoader: false, channelId: hashcode, enterpriseId: enterpriseId
            )
        } else {
            loadingIndicator.stopAnimating()
            updateEmptyVisibility()
        }
    }

    private func send(text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        append(MessageModel(
            session_id: inputs.sessionId,
            message: trimmed,
            suggested_questions: [],
            sent_by: AppConstants.receiverId,
            created_at: DateTimeUtils.currentDateTime(),
            updated_at: "",
            session: inputs.sessionId,
            cpaas_json: nil
        ))
        showProgressPlaceholder()
        viewModel.getChatResponse(
            showBlockingLoader: false,
            question: trimmed,
            sessionId: inputs.sessionId,
            hashcode: inputs.hashcode ?? VihChatBotSDK.shared.prefs?.hashcode ?? "",
            enterpriseId: inputs.id ?? ""
        )
    }

    private func showProgressPlaceholder() {
        append(MessageModel(
            session_id: inputs.sessionId,
            message: "",
            suggested_questions: [],
            sent_by: AppConstants.leftChatProgress,
            created_at: DateTimeUtils.currentDateTime(),
            updated_at: DateTimeUtils.currentDateTime(),
            session: inputs.sessionId,
            cpaas_json: nil
        ))
    }

    private func removeProgressMessage() {
        guard let idx = messages.lastIndex(where: { $0.sent_by == AppConstants.leftChatProgress }) else {
            return
        }
        messages.remove(at: idx)
        tableView.deleteRows(at: [IndexPath(row: idx, section: 0)], with: .none)
    }

    private func append(_ message: MessageModel) {
        messages.append(message)
        tableView.insertRows(at: [IndexPath(row: messages.count - 1, section: 0)], with: .none)
        scrollToBottom()
        updateEmptyVisibility()
    }

    private func scrollToBottom() {
        guard !messages.isEmpty else { return }
        let idx = IndexPath(row: messages.count - 1, section: 0)
        tableView.scrollToRow(at: idx, at: .bottom, animated: false)
    }

    private func updateEmptyVisibility() {
        let empty = messages.isEmpty && !loadingIndicator.isAnimating
        emptyLabel.text = inputs.channel?.displayNameModel?.display_msg
            ?? "Hello! Need support, info, or have a question? Just send a message to get started."
        emptyLabel.isHidden = !empty
    }

    @objc private func openCompanyProfile() {
        guard let channel = inputs.channel else { return }
        let vc = CompanyProfileViewController(channel: channel)
        navigationController?.pushViewController(vc, animated: true)
    }

    @objc private func launchVoicebot() {
        guard !inputs.sessionId.isEmpty else { return }
        let vc = VoicebotViewController(sessionId: inputs.sessionId)
        vc.modalPresentationStyle = .fullScreen
        present(vc, animated: true)
    }

    // MARK: - UITableViewDataSource

    public func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        messages.count
    }

    public func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let message = messages[indexPath.row]
        switch message.sent_by {
        case AppConstants.leftChatProgress:
            return tableView.dequeueReusableCell(withIdentifier: "progress", for: indexPath)
        case AppConstants.receiverId:
            let cell = tableView.dequeueReusableCell(withIdentifier: "right", for: indexPath) as! RightChatCell
            cell.configure(with: message)
            return cell
        default:
            // OTP message (template_type == 1): dedicated OTP card with copy-code (OTP_MOBILE_SPEC §3).
            // Checked before the generic template branch since an OTP also carries cpaas_json.
            if message.isOtp {
                let cell = tableView.dequeueReusableCell(withIdentifier: "otp", for: indexPath) as! OtpChatCell
                cell.configure(with: message)
                return cell
            }
            // CPaaS/campaign template message (image/video/doc, product carousel, buttons).
            if message.cpaas_json != nil {
                let cell = tableView.dequeueReusableCell(withIdentifier: "template", for: indexPath) as! TemplateChatCell
                cell.configure(with: message)
                cell.onTemplateButton = { [weak self] b in self?.handleTemplateButton(b) }
                cell.onProductLink = { [weak self] url in self?.openInteractiveURL(url) }
                cell.onMediaTap = { [weak self] url in self?.openInteractiveURL(url) }
                return cell
            }
            let cell = tableView.dequeueReusableCell(withIdentifier: "left", for: indexPath) as! LeftChatCell
            cell.configure(with: message)
            cell.onButtonTap = { [weak self] button in self?.handleInteractiveButton(button) }
            cell.onChipTap = { [weak self] text in self?.send(text: text) }
            return cell
        }
    }

    /// Handles a CPaaS template button (web / call / email / quick-reply). Mirrors Android's
    /// `onChatButtonClick`.
    private func handleTemplateButton(_ model: ButtonModel) {
        let value = (model.btn_value ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        switch (model.btn_typ ?? "").lowercased() {
        case "call":
            if let url = URL(string: "tel://\(value.filter { $0.isNumber || $0 == "+" })") { UIApplication.shared.open(url) }
        case "email":
            if let url = URL(string: "mailto:\(value)") { UIApplication.shared.open(url) }
        case "web", "url":
            openInteractiveURL(value)
        default:
            if !value.isEmpty { send(text: value) } // quick-reply style
        }
    }

    /// Routes a GLM interactive button tap. Mirrors Android's `onInteractiveButtonClick`.
    private func handleInteractiveButton(_ button: InteractiveButton) {
        let value = (button.value ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        switch button.type?.lowercased() {
        case "quick_reply":
            // Same as sending a normal message.
            if !value.isEmpty { send(text: value) }
        case "url":
            openInteractiveURL(value)
        case "action":
            handleInteractiveAction(value)
        default:
            // Unknown type (server drops these) — ignore rather than risk sending a link.
            CorrelationLogger.warn(message: "Ignoring interactive button of unknown type: \(button.type ?? "nil")")
        }
    }

    private func openInteractiveURL(_ rawURL: String) {
        guard !rawURL.isEmpty else { return }
        var str = rawURL
        if !str.hasPrefix("http://") && !str.hasPrefix("https://") { str = "https://\(str)" }
        guard let url = URL(string: str) else { return }
        UIApplication.shared.open(url)
    }

    // Named in-app capability. Handle call-support natively; anything else degrades gracefully
    // per the GLM contract (MESSAGE_FLOW_GLM.md §4).
    private func handleInteractiveAction(_ action: String) {
        switch action.lowercased() {
        case "call_support", "call", "call_agent", "contact_support":
            let number = (inputs.channel?.customercare.isEmpty == false ? inputs.channel?.customercare : nil)
                ?? (inputs.channel?.phone.isEmpty == false ? inputs.channel?.phone : nil)
            if let number = number,
               let url = URL(string: "tel://\(number.filter { $0.isNumber || $0 == "+" })") {
                UIApplication.shared.open(url)
            }
        default:
            CorrelationLogger.warn(message: "Unhandled interactive action: \(action)")
        }
    }
}

private extension String {
    var nonBlank: String? { trimmingCharacters(in: .whitespaces).isEmpty ? nil : self }
}
