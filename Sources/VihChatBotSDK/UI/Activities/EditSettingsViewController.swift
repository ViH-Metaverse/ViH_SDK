import UIKit
import Combine

/// Mirrors `ui/activity/EditSettingsActivity.kt`. Lets the user update their
/// `full_name`, `email`, and `user_profile_image`.
public final class EditSettingsViewController: BaseViewController, UIImagePickerControllerDelegate, UINavigationControllerDelegate {

    private lazy var viewModel = ProfileViewModel(loaderHost: self)
    private var cancellables: [AnyCancellable] = []

    private let nameField = UITextField()
    private let emailField = UITextField()
    private let avatarButton = UIButton(type: .system)
    private let saveButton = UIButton(type: .system)
    private var pickedImageData: Data?
    private var pickedImageMime: String?
    private var pickedImageName: String?

    public override func initView() {
        title = "Edit Profile"
        view.backgroundColor = .systemBackground

        nameField.placeholder = "Full name"
        nameField.borderStyle = .roundedRect
        nameField.translatesAutoresizingMaskIntoConstraints = false

        emailField.placeholder = "Email"
        emailField.keyboardType = .emailAddress
        emailField.autocapitalizationType = .none
        emailField.borderStyle = .roundedRect
        emailField.translatesAutoresizingMaskIntoConstraints = false

        avatarButton.setTitle("Choose profile photo", for: .normal)
        avatarButton.addTarget(self, action: #selector(pickImage), for: .touchUpInside)
        avatarButton.translatesAutoresizingMaskIntoConstraints = false

        saveButton.setTitle("Save", for: .normal)
        saveButton.addTarget(self, action: #selector(save), for: .touchUpInside)
        saveButton.translatesAutoresizingMaskIntoConstraints = false

        let stack = UIStackView(arrangedSubviews: [avatarButton, nameField, emailField, saveButton])
        stack.axis = .vertical
        stack.spacing = 16
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 24),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16)
        ])

        prefillFromCache()
    }

    public override func setObservers() {
        cancellables.append(viewModel.updatedProfile.observe { [weak self] response in
            VihChatBotSDK.shared.prefs?.userProfile = (try? String(
                data: JSONEncoder().encode(response.data), encoding: .utf8
            )) ?? VihChatBotSDK.shared.prefs?.userProfile
            self?.navigationController?.popViewController(animated: true)
        })
    }

    private func prefillFromCache() {
        guard let raw = VihChatBotSDK.shared.prefs?.userProfile,
              let data = raw.data(using: .utf8),
              let user = try? JSONDecoder().decode(UserProfileModel.self, from: data) else { return }
        nameField.text = user.full_name
        emailField.text = user.email
    }

    @objc private func pickImage() {
        let picker = UIImagePickerController()
        picker.delegate = self
        picker.sourceType = .photoLibrary
        present(picker, animated: true)
    }

    @objc private func save() {
        var fields: [String: String] = [:]
        if let name = nameField.text, !name.isEmpty { fields["full_name"] = name }
        if let email = emailField.text, !email.isEmpty { fields["email"] = email }
        viewModel.updateProfile(
            showBlockingLoader: true, fields: fields,
            imageData: pickedImageData,
            imageMimeType: pickedImageMime,
            imageFilename: pickedImageName
        )
    }

    public func imagePickerController(
        _ picker: UIImagePickerController,
        didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
    ) {
        picker.dismiss(animated: true)
        guard let image = info[.originalImage] as? UIImage,
              let data = image.jpegData(compressionQuality: 0.8) else { return }
        pickedImageData = data
        pickedImageMime = "image/jpeg"
        pickedImageName = "profile.jpg"
        avatarButton.setTitle("Photo selected", for: .normal)
    }

    public func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
    }
}
