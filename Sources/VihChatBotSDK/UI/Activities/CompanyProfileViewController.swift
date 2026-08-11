import UIKit

/// Mirrors `ui/activity/CompanyProfileActivity.kt`. Enterprise details plus the
/// Block / Mute / Promotional controls (backed by the state-mutation endpoints).
public final class CompanyProfileViewController: BaseViewController {

    private var channel: EnterPriseModel
    private let avatar = UIImageView()
    private let nameLabel = UILabel()
    private let descriptionLabel = UILabel()
    private let phoneLabel = UILabel()
    private let websiteLabel = UILabel()
    private let blockButton = UIButton(type: .system)
    private let muteButton = UIButton(type: .system)
    private let promoButton = UIButton(type: .system)

    private lazy var homeRepository = HomeRepository(
        apiService: APIClient.shared.apiService, loaderHost: self
    )

    public init(channel: EnterPriseModel) {
        self.channel = channel
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError("not supported") }

    public override func initView() {
        title = "Company"
        view.backgroundColor = .systemBackground

        avatar.contentMode = .scaleAspectFill
        avatar.layer.cornerRadius = 48
        avatar.layer.masksToBounds = true
        avatar.translatesAutoresizingMaskIntoConstraints = false
        ImageLoader.load(into: avatar, url: channel.display_img ?? channel.profile_picture, placeholderName: "placeholder")

        nameLabel.font = .systemFont(ofSize: 20, weight: .semibold)
        nameLabel.text = channel.displayNameModel?.display_name ?? channel.comp_name
        nameLabel.textAlignment = .center

        descriptionLabel.font = .systemFont(ofSize: 14)
        descriptionLabel.textColor = .secondaryLabel
        descriptionLabel.numberOfLines = 0
        descriptionLabel.text = channel.displayNameModel?.description

        phoneLabel.text = "Phone: \(channel.customercare.isEmpty ? channel.phone : channel.customercare)"
        websiteLabel.text = "Website: \(channel.comp_website)"

        for button in [blockButton, muteButton, promoButton] {
            button.titleLabel?.font = .systemFont(ofSize: 15, weight: .medium)
            button.titleLabel?.numberOfLines = 0
            button.titleLabel?.textAlignment = .center
        }
        blockButton.addTarget(self, action: #selector(toggleBlock), for: .touchUpInside)
        muteButton.addTarget(self, action: #selector(toggleMute), for: .touchUpInside)
        promoButton.addTarget(self, action: #selector(togglePromotional), for: .touchUpInside)

        let stack = UIStackView(arrangedSubviews: [
            avatar, nameLabel, descriptionLabel, phoneLabel, websiteLabel,
            blockButton, muteButton, promoButton
        ])
        stack.axis = .vertical
        stack.alignment = .center
        stack.spacing = 12
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 24),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            avatar.widthAnchor.constraint(equalToConstant: 96),
            avatar.heightAnchor.constraint(equalToConstant: 96)
        ])

        refreshStateButtons()
    }

    // MARK: - State

    /// True when the user has opted OUT of promotional messages. Cascade per backend:
    /// prefer isPromotionalMessageBlocked; else derive from promotionalOptIn; else false.
    private func isPromotionalBlocked() -> Bool {
        channel.isPromotionalMessageBlocked ?? channel.promotionalOptIn.map { !$0 } ?? false
    }

    private func refreshStateButtons() {
        blockButton.setTitle(
            channel.isBlacklistedByUser == true ? "Unblock business" : "Block business", for: .normal)
        muteButton.setTitle(
            channel.isMutedByUser == true ? "Unmute notifications" : "Mute notifications", for: .normal)
        promoButton.setTitle(
            isPromotionalBlocked() ? "Opt-in to promotional messages" : "Opt-out from promotional messages",
            for: .normal)
    }

    // MARK: - Actions

    @objc private func toggleBlock() {
        let newState = channel.isBlacklistedByUser != true
        runMutation(
            { try await self.homeRepository.blacklistEnterprise(
                showBlockingLoader: true, enterprisePk: self.channel.id, blacklist: newState) },
            apply: { self.channel.isBlacklistedByUser = newState }
        )
    }

    @objc private func toggleMute() {
        let newState = channel.isMutedByUser != true
        runMutation(
            { try await self.homeRepository.muteEnterprise(
                showBlockingLoader: true, enterpriseId: self.channel.id, muteStatus: newState) },
            apply: { self.channel.isMutedByUser = newState }
        )
    }

    @objc private func togglePromotional() {
        // If currently opted OUT (blocked), this opts IN, and vice-versa.
        let optIn = isPromotionalBlocked()
        let channelId = VihChatBotSDK.shared.prefs?.hashcode ?? ""
        runMutation(
            { try await self.homeRepository.updateEnterprisePromotional(
                showBlockingLoader: true, optIn: optIn, enterpriseId: self.channel.id, channelId: channelId) },
            apply: {
                // optIn and isPromotionalMessageBlocked are inverse — set both locally.
                self.channel.promotionalOptIn = optIn
                self.channel.isPromotionalMessageBlocked = !optIn
            }
        )
    }

    /// Run a mutation, apply the optimistic local change on success, refresh titles, and
    /// alert on failure. status == false in the body is treated as a server-reported failure.
    private func runMutation(
        _ op: @escaping () async throws -> GenericStatusResponse,
        apply: @escaping () -> Void
    ) {
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            do {
                let res = try await op()
                if res.status == false {
                    self.showMutationError(res.message ?? "Couldn't update. Please try again.")
                } else {
                    apply()
                    self.refreshStateButtons()
                }
            } catch {
                self.showMutationError(error.localizedDescription)
            }
        }
    }

    private func showMutationError(_ message: String) {
        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}
