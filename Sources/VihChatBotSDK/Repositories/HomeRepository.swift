import Foundation

/// Mirrors `data/repository/HomeRepository.kt`.
public final class HomeRepository: BaseRepository {

    private let apiService: ApiService

    public init(apiService: ApiService, loaderHost: LoaderHost?) {
        self.apiService = apiService
        super.init(loaderHost: loaderHost)
    }

    public func getCustomerHome(showBlockingLoader: Bool, hashCode: String) async throws -> SdkFeatureResponse {
        try await doSafeAPIRequest(showBlockingLoader: showBlockingLoader) {
            try await self.apiService.getSdkFeatures(hashCode: hashCode)
        }
    }

    public func createUserProfile(showBlockingLoader: Bool, body: UserProfileRequest) async throws -> UserProfileResponse {
        try await doSafeAPIRequest(showBlockingLoader: showBlockingLoader) {
            try await self.apiService.createUserProfile(body: body)
        }
    }

    /// Exchanges a verified Cognito ID token (+ mobile for delivery) for app-session tokens.
    public func emailLogin(showBlockingLoader: Bool, body: EmailLoginRequest) async throws -> EmailLoginResponse {
        try await doSafeAPIRequest(showBlockingLoader: showBlockingLoader) {
            try await self.apiService.emailLogin(body: body)
        }
    }

    /// Subscribes the authenticated user to a channel (Settings hashkey switch).
    public func subscribeChannel(showBlockingLoader: Bool, body: SubscribeChannelRequest) async throws -> SubscribeChannelResponse {
        try await doSafeAPIRequest(showBlockingLoader: showBlockingLoader) {
            try await self.apiService.subscribeChannel(body: body)
        }
    }

    public func updateUserProfile(showBlockingLoader: Bool, body: UpdateUserProfile) async throws -> UserProfileUpdateResponse {
        try await doSafeAPIRequest(showBlockingLoader: showBlockingLoader) {
            try await self.apiService.updateUserProfile(body: body)
        }
    }

    public func updateUserProfileImage(
        showBlockingLoader: Bool,
        imageData: Data, mimeType: String, filename: String
    ) async throws -> UserProfileUpdateResponse {
        try await doSafeAPIRequest(showBlockingLoader: showBlockingLoader) {
            try await self.apiService.updateUserProfileImage(
                imageData: imageData, mimeType: mimeType, filename: filename
            )
        }
    }

    public func createProfile(
        showBlockingLoader: Bool,
        fullName: String,
        email: String?,
        imageData: Data,
        imageMimeType: String,
        imageFilename: String
    ) async throws -> UserProfileUpdateResponse {
        var fields = ["full_name": fullName]
        if let email = email, !email.isEmpty { fields["email"] = email }
        return try await doSafeAPIRequest(showBlockingLoader: showBlockingLoader) {
            try await self.apiService.createProfile(
                fields: fields,
                imageData: imageData,
                imageMimeType: imageMimeType,
                imageFilename: imageFilename
            )
        }
    }

    public func updateProfileSelective(
        showBlockingLoader: Bool,
        fields: [String: String],
        imageData: Data?,
        imageMimeType: String?,
        imageFilename: String?
    ) async throws -> UserProfileUpdateResponse {
        try await doSafeAPIRequest(showBlockingLoader: showBlockingLoader) {
            try await self.apiService.updateProfileSelective(
                fields: fields,
                imageData: imageData,
                imageMimeType: imageMimeType,
                imageFilename: imageFilename
            )
        }
    }

    public func userLogout(showBlockingLoader: Bool, refreshToken: String) async throws -> LogoutDataModel {
        try await doSafeAPIRequest(showBlockingLoader: showBlockingLoader) {
            try await self.apiService.userLogout(refreshToken: refreshToken)
        }
    }
}
