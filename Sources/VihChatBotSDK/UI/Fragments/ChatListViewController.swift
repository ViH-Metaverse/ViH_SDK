import UIKit
import Combine

/// Mirrors `ui/fragments/ChatListFragment.kt` + the embedded RecyclerView.
public final class ChatListViewController: BaseViewController, UITableViewDataSource, UITableViewDelegate {

    private lazy var viewModel = HomeViewModel(loaderHost: self)
    private var cancellables: [AnyCancellable] = []
    private let tableView = UITableView()
    private var items: [ChatListModel] = []

    /// Optional category filter (`cpaas_json.templ_typ`: "1" OTP / "3" transactional). When set,
    /// the conversation list keeps only threads whose latest message is that category. nil = all.
    private let category: String?

    public init(category: String? = nil) {
        self.category = category
        super.init(nibName: nil, bundle: nil)
    }

    public required init?(coder: NSCoder) {
        self.category = nil
        super.init(coder: coder)
    }

    public override func initView() {
        title = "Chats"
        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(ChatListCell.self, forCellReuseIdentifier: "cell")
        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])
    }

    public override func setObservers() {
        cancellables.append(viewModel.chatListLiveData.observe { [weak self] response in
            guard let self = self else { return }
            if let category = self.category {
                self.items = response.data.filter { $0.last_message.cpaas_json?.templ_typ == category }
            } else {
                self.items = response.data
            }
            self.tableView.reloadData()
        })

        NotificationCenter.default.addObserver(
            self, selector: #selector(refresh),
            name: AppConstants.fcmMessageReceived, object: nil
        )
    }

    public override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        refresh()
    }

    @objc private func refresh() {
        let hashcode = VihChatBotSDK.shared.prefs?.hashcode ?? VihChatBotSDK.shared.config?.hashcode ?? ""
        viewModel.getChattingListResponse(showBlockingLoader: false, hashcode: hashcode)
    }

    // MARK: - UITableView

    public func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        items.count
    }

    public func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "cell", for: indexPath) as! ChatListCell
        cell.configure(with: items[indexPath.row])
        return cell
    }

    public func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        let model = items[indexPath.row]
        let inputs = ChatViewController.Inputs(
            sessionId: model.session_id,
            channelName: model.enterprise.displayNameModel?.display_name,
            channelImage: model.enterprise.display_img ?? model.enterprise.profile_picture,
            channel: model.enterprise,
            // chat-history / enterprise-details key on the enterprise's user_id (e.g. "10101"),
            // NOT its PK — passing the PK returns "Session not found" and an empty window. Mirrors
            // Android ChatListFragment (id = chat.enterprise.user_id).
            id: model.enterprise.user_id,
            hashcode: VihChatBotSDK.shared.prefs?.hashcode
        )
        navigationController?.pushViewController(ChatViewController(inputs: inputs), animated: true)
    }
}

public final class ChatListCell: UITableViewCell {

    private let avatar = UIImageView()
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let dateLabel = UILabel()
    private let badge = UILabel()

    private static let inFmt: DateFormatter = {
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "MMM, dd yyyy HH:mm:ss"; return f
    }()
    private static let outFmt: DateFormatter = {
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "MMM d"; return f
    }()

    public override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        selectionStyle = .none
        avatar.contentMode = .scaleAspectFill
        avatar.layer.cornerRadius = 26
        avatar.layer.masksToBounds = true
        avatar.backgroundColor = .secondarySystemBackground
        avatar.translatesAutoresizingMaskIntoConstraints = false

        titleLabel.font = .systemFont(ofSize: 16, weight: .semibold)
        titleLabel.textColor = .label
        subtitleLabel.font = .systemFont(ofSize: 13)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 1

        dateLabel.font = .systemFont(ofSize: 11)
        dateLabel.textColor = .secondaryLabel
        dateLabel.setContentHuggingPriority(.required, for: .horizontal)
        dateLabel.translatesAutoresizingMaskIntoConstraints = false

        let stack = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel])
        stack.axis = .vertical
        stack.spacing = 3
        stack.translatesAutoresizingMaskIntoConstraints = false

        badge.backgroundColor = DynamicThemeManager.shared.palette.primaryColor
        badge.textColor = .white
        badge.textAlignment = .center
        badge.font = .systemFont(ofSize: 11, weight: .bold)
        badge.layer.cornerRadius = 10
        badge.layer.masksToBounds = true
        badge.translatesAutoresizingMaskIntoConstraints = false

        contentView.addSubview(avatar)
        contentView.addSubview(stack)
        contentView.addSubview(dateLabel)
        contentView.addSubview(badge)

        NSLayoutConstraint.activate([
            avatar.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            avatar.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            avatar.widthAnchor.constraint(equalToConstant: 52),
            avatar.heightAnchor.constraint(equalToConstant: 52),

            stack.leadingAnchor.constraint(equalTo: avatar.trailingAnchor, constant: 12),
            stack.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            stack.trailingAnchor.constraint(lessThanOrEqualTo: dateLabel.leadingAnchor, constant: -8),

            dateLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            dateLabel.topAnchor.constraint(equalTo: stack.topAnchor, constant: 2),

            badge.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            badge.centerYAnchor.constraint(equalTo: subtitleLabel.centerYAnchor),
            badge.widthAnchor.constraint(greaterThanOrEqualToConstant: 20),
            badge.heightAnchor.constraint(equalToConstant: 20),

            contentView.heightAnchor.constraint(greaterThanOrEqualToConstant: 72)
        ])
    }
    public required init?(coder: NSCoder) { fatalError("not supported") }

    public func configure(with model: ChatListModel) {
        titleLabel.text = model.enterprise.displayNameModel?.display_name ?? model.enterprise.comp_name
        subtitleLabel.text = model.last_message.message
        if let d = Self.inFmt.date(from: model.last_message.created_at) {
            dateLabel.text = Self.outFmt.string(from: d)
        } else {
            dateLabel.text = ""
        }
        ImageLoader.load(
            into: avatar,
            url: model.enterprise.display_img ?? model.enterprise.profile_picture,
            placeholderName: "placeholder"
        )
        if model.unseen_count > 0 {
            badge.text = " \(model.unseen_count) "
            badge.isHidden = false
        } else {
            badge.isHidden = true
        }
    }
}
