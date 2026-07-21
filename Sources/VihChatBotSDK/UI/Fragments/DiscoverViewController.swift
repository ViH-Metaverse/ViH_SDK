import UIKit
import Combine

/// Mirrors `ui/fragments/DiscoverFragment.kt`. Shows the directory of
/// enterprises that the user can start a chat with. Implements the same
/// search bar + industry filter + paginated list flow as Android.
public final class DiscoverViewController: BaseViewController, UITableViewDataSource, UITableViewDelegate, UISearchBarDelegate {

    private lazy var viewModel = HomeViewModel(loaderHost: self)
    private var cancellables: [AnyCancellable] = []
    private let searchBar = UISearchBar()
    private let tableView = UITableView()

    private var items: [EnterPriseModel] = []
    private var page = 1
    private var search = ""
    private var industries: [String] = []
    private var selectedIndustries: Set<String> = []

    public override func initView() {
        title = "Discover"
        searchBar.placeholder = "Search"
        searchBar.delegate = self
        searchBar.translatesAutoresizingMaskIntoConstraints = false

        // Filter + search circle icons in the top bar (mirrors the Android Discover header).
        let accent = DynamicThemeManager.shared.palette.primaryColor
        navigationItem.rightBarButtonItems = [
            UIBarButtonItem(customView: makeCircleButton("magnifyingglass", accent: accent, action: #selector(searchTapped))),
            UIBarButtonItem(customView: makeCircleButton("line.3.horizontal.decrease", accent: accent, action: #selector(filterTapped)))
        ]

        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(DiscoverChannelCell.self, forCellReuseIdentifier: "cell")
        tableView.rowHeight = 72
        tableView.separatorInset = UIEdgeInsets(top: 0, left: 76, bottom: 0, right: 0)

        view.addSubview(searchBar)
        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            searchBar.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            searchBar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            searchBar.trailingAnchor.constraint(equalTo: view.trailingAnchor),

            tableView.topAnchor.constraint(equalTo: searchBar.bottomAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])
    }

    public override func setObservers() {
        cancellables.append(viewModel.enterprisesDiscoverListLiveData.observe { [weak self] response in
            guard let self = self else { return }
            if self.page == 1 {
                self.items = response.data
            } else {
                self.items.append(contentsOf: response.data)
            }
            self.tableView.reloadData()
        })
        cancellables.append(viewModel.industryListLiveData.observe { [weak self] response in
            self?.industries = response.data
        })
        viewModel.getIndustriesListResponse(showBlockingLoader: false)
    }

    private func makeCircleButton(_ systemName: String, accent: UIColor, action: Selector) -> UIButton {
        let b = UIButton(type: .system)
        b.setImage(UIImage(systemName: systemName), for: .normal)
        b.tintColor = accent
        b.backgroundColor = accent.withAlphaComponent(0.12)
        b.layer.cornerRadius = 17
        b.widthAnchor.constraint(equalToConstant: 34).isActive = true
        b.heightAnchor.constraint(equalToConstant: 34).isActive = true
        b.addTarget(self, action: action, for: .touchUpInside)
        return b
    }

    @objc private func searchTapped() {
        searchBar.becomeFirstResponder()
    }

    @objc private func filterTapped() {
        let sheet = IndustryFiltersBottomSheet()
        sheet.industries = industries
        sheet.selected = selectedIndustries
        sheet.onApply = { [weak self] selected in
            guard let self = self else { return }
            self.selectedIndustries = selected
            self.viewModel.setSelectedIndustries(selected.joined(separator: ","))
            self.page = 1
            self.load()
        }
        present(sheet, animated: true)
    }

    public override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        load()
    }

    private func load() {
        let hashcode = VihChatBotSDK.shared.prefs?.hashcode ?? VihChatBotSDK.shared.config?.hashcode ?? ""
        viewModel.getEnterpriseDiscoverListResponse(
            showBlockingLoader: false,
            hashCode: hashcode,
            page: page, search: search,
            industries: viewModel.selectedIndustries()
        )
    }

    public func searchBar(_ searchBar: UISearchBar, textDidChange searchText: String) {
        search = searchText
        page = 1
        load()
    }

    public func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        items.count
    }

    public func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "cell", for: indexPath) as! DiscoverChannelCell
        let model = items[indexPath.row]
        cell.configure(with: model)
        cell.onChat = { [weak self] in self?.openChat(model) }
        return cell
    }

    public func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        openChat(items[indexPath.row])
    }

    private func openChat(_ model: EnterPriseModel) {
        let inputs = ChatViewController.Inputs(
            sessionId: "",
            channelName: model.displayNameModel?.display_name ?? model.comp_name,
            channelImage: model.display_img ?? model.profile_picture ?? model.enterprise_logo ?? model.enterprise_display_img,
            channel: model,
            // Use the enterprise user_id (not the PK) so chat-history/enterprise-details resolve
            // the session — mirrors Android DiscoverFragment (model.user_id).
            id: model.user_id,
            hashcode: VihChatBotSDK.shared.prefs?.hashcode
        )
        navigationController?.pushViewController(ChatViewController(inputs: inputs), animated: true)
    }
}

/// Discover row with channel logo + name + industry. Mirrors the Android
/// discover list item (the previous plain `UITableViewCell` had no image view,
/// so logos never rendered).
public final class DiscoverChannelCell: UITableViewCell {

    private let logo = UIImageView()
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let chatButton = UIButton(type: .system)

    /// Tapped the "Chat" button (mirrors the Android discover row's chat CTA).
    public var onChat: (() -> Void)?

    public override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        selectionStyle = .none

        logo.contentMode = .scaleAspectFill
        logo.layer.cornerRadius = 24
        logo.layer.masksToBounds = true
        logo.backgroundColor = .secondarySystemBackground
        logo.translatesAutoresizingMaskIntoConstraints = false

        titleLabel.font = .systemFont(ofSize: 16, weight: .semibold)
        titleLabel.textColor = .label
        titleLabel.numberOfLines = 2
        subtitleLabel.font = .systemFont(ofSize: 13)
        subtitleLabel.textColor = .secondaryLabel

        let stack = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel])
        stack.axis = .vertical
        stack.spacing = 2
        stack.translatesAutoresizingMaskIntoConstraints = false

        let accent = DynamicThemeManager.shared.palette.primaryColor
        chatButton.setTitle("Chat", for: .normal)
        chatButton.setImage(UIImage(systemName: "bubble.left"), for: .normal)
        chatButton.tintColor = accent
        chatButton.setTitleColor(accent, for: .normal)
        chatButton.titleLabel?.font = .systemFont(ofSize: 13, weight: .semibold)
        chatButton.backgroundColor = accent.withAlphaComponent(0.10)
        chatButton.layer.cornerRadius = 16
        chatButton.contentEdgeInsets = UIEdgeInsets(top: 6, left: 12, bottom: 6, right: 12)
        chatButton.imageEdgeInsets = UIEdgeInsets(top: 0, left: -4, bottom: 0, right: 4)
        chatButton.setContentHuggingPriority(.required, for: .horizontal)
        chatButton.translatesAutoresizingMaskIntoConstraints = false
        chatButton.addTarget(self, action: #selector(chatTapped), for: .touchUpInside)

        contentView.addSubview(logo)
        contentView.addSubview(stack)
        contentView.addSubview(chatButton)

        NSLayoutConstraint.activate([
            logo.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            logo.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            logo.widthAnchor.constraint(equalToConstant: 48),
            logo.heightAnchor.constraint(equalToConstant: 48),

            stack.leadingAnchor.constraint(equalTo: logo.trailingAnchor, constant: 12),
            stack.trailingAnchor.constraint(lessThanOrEqualTo: chatButton.leadingAnchor, constant: -8),
            stack.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),

            chatButton.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            chatButton.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            chatButton.heightAnchor.constraint(equalToConstant: 32)
        ])
    }

    public required init?(coder: NSCoder) { fatalError("not supported") }

    public override func prepareForReuse() {
        super.prepareForReuse()
        logo.image = UIImage(named: "placeholder")
        onChat = nil
    }

    @objc private func chatTapped() { onChat?() }

    public func configure(with model: EnterPriseModel) {
        titleLabel.text = model.displayNameModel?.display_name ?? model.comp_name
        subtitleLabel.text = model.industry
        subtitleLabel.isHidden = (model.industry).isEmpty
        ImageLoader.load(
            into: logo,
            url: model.display_img ?? model.profile_picture ?? model.enterprise_logo ?? model.enterprise_display_img,
            placeholderName: "placeholder"
        )
    }
}
