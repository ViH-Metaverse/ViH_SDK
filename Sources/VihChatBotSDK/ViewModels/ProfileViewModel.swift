import Foundation

/// Mirrors `viewmodel/ProfileViewModel.kt`.
public final class ProfileViewModel: BaseViewModel {

    private let repository: HomeRepository

    public let updatedProfile = LiveData<UserProfileUpdateResponse>()
    public let logout = LiveData<LogoutDataModel>()
    public let errorLiveData = SingleLiveEvent<String>()
    // Dedicated events for channel-switch — kept separate from errorLiveData, which the
    // Settings screen treats as a logout signal.
    public let subscribeChannelResult = LiveData<SubscribeChannelResponse>()
    public let subscribeChannelError = SingleLiveEvent<String>()

    public init(loaderHost: LoaderHost?) {
        self.repository = HomeRepository(apiService: APIClient.shared.apiService, loaderHost: loaderHost)
        super.init()
    }

    /// Subscribes the current user to [hashcode] (Settings hashkey switch).
    public func subscribeChannel(showBlockingLoader: Bool, hashcode: String) {
        launch { [weak self] in
            guard let self = self else { return }
            do {
                let response = try await self.repository.subscribeChannel(
                    showBlockingLoader: showBlockingLoader,
                    body: SubscribeChannelRequest(channel_id: hashcode)
                )
                self.subscribeChannelResult.postValue(response)
            } catch {
                self.subscribeChannelError.postValue(error.localizedDescription)
            }
        }
    }

    public func updateUsername(showBlockingLoader: Bool, username: String, email: String) {
        launch { [weak self] in
            guard let self = self else { return }
            do {
                let response = try await self.repository.updateUserProfile(
                    showBlockingLoader: showBlockingLoader,
                    body: UpdateUserProfile(username: username, email: email)
                )
                self.updatedProfile.postValue(response)
            } catch { self.errorLiveData.postValue(error.localizedDescription) }
        }
    }

    public func updateProfileImage(
        showBlockingLoader: Bool, imageData: Data, mimeType: String, filename: String
    ) {
        launch { [weak self] in
            guard let self = self else { return }
            do {
                let response = try await self.repository.updateUserProfileImage(
                    showBlockingLoader: showBlockingLoader,
                    imageData: imageData, mimeType: mimeType, filename: filename
                )
                self.updatedProfile.postValue(response)
            } catch { self.errorLiveData.postValue(error.localizedDescription) }
        }
    }

    public func updateProfile(
        showBlockingLoader: Bool,
        fields: [String: String],
        imageData: Data?,
        imageMimeType: String?,
        imageFilename: String?
    ) {
        launch { [weak self] in
            guard let self = self else { return }
            do {
                let response = try await self.repository.updateProfileSelective(
                    showBlockingLoader: showBlockingLoader,
                    fields: fields,
                    imageData: imageData,
                    imageMimeType: imageMimeType,
                    imageFilename: imageFilename
                )
                self.updatedProfile.postValue(response)
            } catch { self.errorLiveData.postValue(error.localizedDescription) }
        }
    }

    public func logoutUser(showBlockingLoader: Bool, refreshToken: String) {
        launch { [weak self] in
            guard let self = self else { return }
            do {
                let response = try await self.repository.userLogout(
                    showBlockingLoader: showBlockingLoader, refreshToken: refreshToken
                )
                self.logout.postValue(response)
            } catch { self.errorLiveData.postValue(error.localizedDescription) }
        }
    }
}
