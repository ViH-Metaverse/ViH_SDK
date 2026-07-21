import UIKit

/// Mirrors `ui/activity/CompanyProfileActivity.kt`. Read-only details for the
/// enterprise the user is chatting with.
public final class CompanyProfileViewController: BaseViewController {

    private let channel: EnterPriseModel
    private let avatar = UIImageView()
    private let nameLabel = UILabel()
    private let descriptionLabel = UILabel()
    private let phoneLabel = UILabel()
    private let websiteLabel = UILabel()

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

        let stack = UIStackView(arrangedSubviews: [avatar, nameLabel, descriptionLabel, phoneLabel, websiteLabel])
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
    }
}
