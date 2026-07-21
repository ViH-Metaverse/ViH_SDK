import UIKit
import Combine

/// Mirrors `ui/fragments/SettingFragment.kt`. Same surface area: profile header,
/// edit-settings entry point, and logout.
public final class SettingsViewController: BaseViewController {

    // Mirrors the URLs in `ui/fragments/SettingFragment.kt`.
    private static let termsURL = "https://vihmessenger.com/Vih-terms-and-conditions"
    private static let privacyURL = "https://vihmessenger.com/Vih-privacy-policy"

    private lazy var viewModel = ProfileViewModel(loaderHost: self)
    private var cancellables: [AnyCancellable] = []

    // Hashkey awaiting backend subscription before it's persisted + applied.
    private var pendingHashkey: String?

    private let nameLabel = UILabel()
    private let emailLabel = UILabel()
    private let avatarView = UIImageView()
    private let editButton = UIButton(type: .system)
    private let helpStack = UIStackView()
    private let logoutButton = UIButton(type: .system)

    public override func initView() {
        title = "Settings"
        view.backgroundColor = .systemBackground

        avatarView.contentMode = .scaleAspectFill
        avatarView.layer.cornerRadius = 40
        avatarView.layer.masksToBounds = true
        avatarView.backgroundColor = .systemGray5
        avatarView.translatesAutoresizingMaskIntoConstraints = false

        nameLabel.font = .systemFont(ofSize: 18, weight: .semibold)
        nameLabel.translatesAutoresizingMaskIntoConstraints = false
        emailLabel.textColor = .secondaryLabel
        emailLabel.translatesAutoresizingMaskIntoConstraints = false

        editButton.setTitle("Edit Profile", for: .normal)
        editButton.addTarget(self, action: #selector(openEdit), for: .touchUpInside)
        editButton.translatesAutoresizingMaskIntoConstraints = false

        logoutButton.setTitle("Log out", for: .normal)
        logoutButton.setTitleColor(.systemRed, for: .normal)
        logoutButton.addTarget(self, action: #selector(logoutTapped), for: .touchUpInside)
        logoutButton.translatesAutoresizingMaskIntoConstraints = false

        // Help & Support section — mirrors the "Help & Support" rows in SettingFragment.kt.
        let helpHeader = UILabel()
        helpHeader.text = "Help & Support"
        helpHeader.font = .systemFont(ofSize: 14, weight: .medium)
        helpHeader.textColor = .secondaryLabel

        helpStack.axis = .vertical
        helpStack.spacing = 0
        helpStack.translatesAutoresizingMaskIntoConstraints = false
        helpStack.addArrangedSubview(helpHeader)
        helpStack.setCustomSpacing(8, after: helpHeader)
        helpStack.addArrangedSubview(makeLinkRow(title: "Terms of Use", icon: "doc.text", action: #selector(openTerms)))
        let privacyRow = makeLinkRow(title: "Privacy Policy", icon: "lock.shield", action: #selector(openPrivacy))
        helpStack.addArrangedSubview(privacyRow)

        // Account section — change the channel hashkey the app talks to.
        let accountHeader = UILabel()
        accountHeader.text = "Account"
        accountHeader.font = .systemFont(ofSize: 14, weight: .medium)
        accountHeader.textColor = .secondaryLabel
        helpStack.setCustomSpacing(20, after: privacyRow)
        helpStack.addArrangedSubview(accountHeader)
        helpStack.setCustomSpacing(8, after: accountHeader)
        helpStack.addArrangedSubview(makeLinkRow(title: "Change Channel Hashkey", icon: "key", action: #selector(changeHashkeyTapped)))

        [avatarView, nameLabel, emailLabel, editButton, helpStack, logoutButton].forEach(view.addSubview)

        NSLayoutConstraint.activate([
            avatarView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 24),
            avatarView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            avatarView.widthAnchor.constraint(equalToConstant: 80),
            avatarView.heightAnchor.constraint(equalToConstant: 80),

            nameLabel.topAnchor.constraint(equalTo: avatarView.bottomAnchor, constant: 12),
            nameLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),

            emailLabel.topAnchor.constraint(equalTo: nameLabel.bottomAnchor, constant: 4),
            emailLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),

            editButton.topAnchor.constraint(equalTo: emailLabel.bottomAnchor, constant: 24),
            editButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),

            helpStack.topAnchor.constraint(equalTo: editButton.bottomAnchor, constant: 32),
            helpStack.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 20),
            helpStack.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -20),

            logoutButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -16),
            logoutButton.centerXAnchor.constraint(equalTo: view.centerXAnchor)
        ])

        hydrateFromCache()
    }

    /// Builds a tappable row (icon + title + chevron), matching the Help & Support
    /// rows from the Android `fragment_setting.xml`.
    private func makeLinkRow(title: String, icon: String, action: Selector) -> UIView {
        let row = UIView()
        row.translatesAutoresizingMaskIntoConstraints = false

        let iconView = UIImageView(image: UIImage(systemName: icon))
        iconView.tintColor = .label
        iconView.contentMode = .scaleAspectFit
        iconView.translatesAutoresizingMaskIntoConstraints = false

        let label = UILabel()
        label.text = title
        label.font = .systemFont(ofSize: 16)
        label.translatesAutoresizingMaskIntoConstraints = false

        let chevron = UIImageView(image: UIImage(systemName: "chevron.right"))
        chevron.tintColor = .tertiaryLabel
        chevron.contentMode = .scaleAspectFit
        chevron.translatesAutoresizingMaskIntoConstraints = false

        [iconView, label, chevron].forEach(row.addSubview)

        NSLayoutConstraint.activate([
            row.heightAnchor.constraint(equalToConstant: 48),

            iconView.leadingAnchor.constraint(equalTo: row.leadingAnchor),
            iconView.centerYAnchor.constraint(equalTo: row.centerYAnchor),
            iconView.widthAnchor.constraint(equalToConstant: 20),
            iconView.heightAnchor.constraint(equalToConstant: 20),

            label.leadingAnchor.constraint(equalTo: iconView.trailingAnchor, constant: 12),
            label.centerYAnchor.constraint(equalTo: row.centerYAnchor),

            chevron.trailingAnchor.constraint(equalTo: row.trailingAnchor),
            chevron.centerYAnchor.constraint(equalTo: row.centerYAnchor),
            chevron.widthAnchor.constraint(equalToConstant: 14),
            chevron.heightAnchor.constraint(equalToConstant: 14)
        ])

        row.addGestureRecognizer(UITapGestureRecognizer(target: self, action: action))
        return row
    }

    public override func setObservers() {
        cancellables.append(viewModel.logout.observe { [weak self] _ in
            self?.finishLogout()
        })
        // A blacklisted/expired refresh token surfaces here as an APIError. The
        // server-side session is already dead in that case, so treat it like a
        // successful logout and clear local state anyway — otherwise the user is
        // stuck on this screen with no way out.
        cancellables.append(viewModel.errorLiveData.observe { [weak self] _ in
            self?.finishLogout()
        })

        // Channel switch succeeded — persist the new hashkey and re-root so the dashboard
        // reloads against it.
        cancellables.append(viewModel.subscribeChannelResult.observe { [weak self] _ in
            guard let self = self, let newHash = self.pendingHashkey else { return }
            self.pendingHashkey = nil
            VihChatBotSDK.shared.prefs?.hashcode = newHash
            NotificationCenter.default.post(
                name: Notification.Name("com.vihmessenger.vihchatbot.CHANNEL_CHANGED"), object: nil
            )
            self.dismiss(animated: true)
        })
        cancellables.append(viewModel.subscribeChannelError.observe { [weak self] message in
            guard let self = self else { return }
            self.pendingHashkey = nil
            let alert = UIAlertController(
                title: "Couldn't switch channel", message: message, preferredStyle: .alert
            )
            alert.addAction(UIAlertAction(title: "OK", style: .default))
            self.present(alert, animated: true)
        })
    }

    private func finishLogout() {
        VihChatBotSDK.shared.prefs?.clearAllPreferences()
        NotificationCenter.default.post(
            name: Notification.Name("com.vihmessenger.vihchatbot.LOGGED_OUT"), object: nil
        )
        dismiss(animated: true)
    }

    private func hydrateFromCache() {
        guard let raw = VihChatBotSDK.shared.prefs?.userProfile,
              let data = raw.data(using: .utf8),
              let user = try? JSONDecoder().decode(UserProfileModel.self, from: data) else {
            return
        }
        nameLabel.text = user.full_name ?? user.username ?? user.user_name
        emailLabel.text = user.email
        ImageLoader.load(into: avatarView, url: user.user_profile_image ?? user.profile_image, placeholderName: "placeholder")
    }

    @objc private func openEdit() {
        navigationController?.pushViewController(EditSettingsViewController(), animated: true)
    }

    @objc private func openTerms() { openURL(Self.termsURL) }
    @objc private func openPrivacy() { openURL(Self.privacyURL) }

    /// Lets the user switch the channel hashkey. Saves to Prefs and posts CHANNEL_CHANGED
    /// so the host re-roots through splash → dashboard, reloading against the new channel
    /// (Discover/Chat read `prefs.hashcode`). Session is preserved.
    @objc private func changeHashkeyTapped() {
        let alert = UIAlertController(
            title: "Change Channel Hashkey",
            message: "Switch the channel this app talks to. The app will reload to apply.",
            preferredStyle: .alert
        )
        alert.addTextField { tf in
            tf.text = VihChatBotSDK.shared.prefs?.hashcode
            tf.placeholder = "Channel hashkey"
            tf.autocapitalizationType = .none
            tf.autocorrectionType = .no
        }
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: "Save", style: .default) { [weak self] _ in
            let newHash = (alert.textFields?.first?.text ?? "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
            guard !newHash.isEmpty, newHash != VihChatBotSDK.shared.prefs?.hashcode else { return }
            // Subscribe the account to the new channel first; only persist + re-root on
            // success so messages work on the new channel.
            self?.pendingHashkey = newHash
            self?.viewModel.subscribeChannel(showBlockingLoader: true, hashcode: newHash)
        })
        present(alert, animated: true)
    }

    private func openURL(_ string: String) {
        guard let url = URL(string: string), UIApplication.shared.canOpenURL(url) else {
            let alert = UIAlertController(
                title: nil,
                message: "Unable to open the link. Please install a web browser.",
                preferredStyle: .alert
            )
            alert.addAction(UIAlertAction(title: "OK", style: .default))
            present(alert, animated: true)
            return
        }
        UIApplication.shared.open(url)
    }

    @objc private func logoutTapped() {
        guard let refresh = VihChatBotSDK.shared.prefs?.refreshToken, !refresh.isEmpty else { return }
        viewModel.logoutUser(showBlockingLoader: true, refreshToken: refresh)
    }
}
