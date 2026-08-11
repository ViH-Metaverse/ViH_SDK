import Foundation

/// Swift equivalent of Retrofit's `ApiService` interface. Each `suspend fun`
/// becomes an `async throws` method. Path/query/body assembly that Retrofit
/// handled via annotations is done inline below.
///
/// All Authorization header injection runs through `AuthInterceptor` exactly
/// like Android — never set it inline (matches the security note in `ApiService.kt`).
public protocol ApiService {
    func getSdkFeatures(hashCode: String) async throws -> SdkFeatureResponse

    func createUserProfile(body: UserProfileRequest) async throws -> UserProfileResponse

    func emailLogin(body: EmailLoginRequest) async throws -> EmailLoginResponse

    func subscribeChannel(body: SubscribeChannelRequest) async throws -> SubscribeChannelResponse

    func updateUserProfile(body: UpdateUserProfile) async throws -> UserProfileUpdateResponse

    func updateUserProfileUsername(body: UserProfilePatchUsername) async throws -> UserProfileUpdateResponse

    func updateUserProfileImage(imageData: Data, mimeType: String, filename: String) async throws -> UserProfileUpdateResponse

    func updateProfileSelective(
        fields: [String: String],
        imageData: Data?,
        imageMimeType: String?,
        imageFilename: String?
    ) async throws -> UserProfileUpdateResponse

    func getChatResponse(
        hashcode: String,
        enterpriseId: String,
        question: String,
        sessionId: String
    ) async throws -> ChatMessageModel

    func getChatHistory(channelId: String, enterpriseId: String) async throws -> ChatHistoryModel

    func getChatListResponse(hashcode: String) async throws -> ChatListModelResponse

    func getEnterpriseDiscoverList(
        hashcode: String,
        page: Int,
        search: String,
        industries: String
    ) async throws -> EnterPriseDiscoverModel

    func getIndustries() async throws -> IndustryResponse

    func getEnterprises(enterpriseId: String) async throws -> EnterpriseApiResponse

    func createProfile(
        fields: [String: String],
        imageData: Data,
        imageMimeType: String,
        imageFilename: String
    ) async throws -> UserProfileUpdateResponse

    func userLogout(refreshToken: String) async throws -> LogoutDataModel

    func registerDeviceToken(body: DeviceTokenRequest) async throws -> DeviceTokenResponse

    // Per-enterprise state mutations (see EnterPriseModel flags).
    func blacklistEnterprise(enterprisePk: Int, blacklist: Bool) async throws -> GenericStatusResponse
    func muteEnterprise(enterpriseId: Int, muteStatus: Bool) async throws -> GenericStatusResponse
    func updateEnterprisePromotional(optIn: Bool, enterpriseId: Int, channelId: String) async throws -> GenericStatusResponse
}

final class APIServiceImpl: ApiService {

    private let client: APIClient
    init(client: APIClient) { self.client = client }

    // MARK: - SDK features / chat / enterprises

    func getSdkFeatures(hashCode: String) async throws -> SdkFeatureResponse {
        // Trailing slash required by the DRF SimpleRouter — without it the server 301-redirects.
        let req = client.request(path: BaseAPIConstants.sdkFeatures + hashCode + "/")
        return try await client.send(req, as: SdkFeatureResponse.self)
    }

    func createUserProfile(body: UserProfileRequest) async throws -> UserProfileResponse {
        let req = client.request(
            path: BaseAPIConstants.userSignupLogin,
            method: "POST",
            body: try JSONEncoder().encode(body),
            contentType: "application/json"
        )
        return try await client.send(req, as: UserProfileResponse.self)
    }

    func emailLogin(body: EmailLoginRequest) async throws -> EmailLoginResponse {
        let req = client.request(
            path: BaseAPIConstants.emailLogin,
            method: "POST",
            body: try JSONEncoder().encode(body),
            contentType: "application/json"
        )
        return try await client.send(req, as: EmailLoginResponse.self)
    }

    func subscribeChannel(body: SubscribeChannelRequest) async throws -> SubscribeChannelResponse {
        let req = client.request(
            path: BaseAPIConstants.subscribeChannel,
            method: "POST",
            body: try JSONEncoder().encode(body),
            contentType: "application/json"
        )
        return try await client.send(req, as: SubscribeChannelResponse.self)
    }

    func updateUserProfile(body: UpdateUserProfile) async throws -> UserProfileUpdateResponse {
        let req = client.request(
            path: BaseAPIConstants.userProfile,
            method: "PATCH",
            body: try JSONEncoder().encode(body),
            contentType: "application/json"
        )
        return try await client.send(req, as: UserProfileUpdateResponse.self)
    }

    func updateUserProfileUsername(body: UserProfilePatchUsername) async throws -> UserProfileUpdateResponse {
        let req = client.request(
            path: BaseAPIConstants.userProfile,
            method: "PATCH",
            body: try JSONEncoder().encode(body),
            contentType: "application/json"
        )
        return try await client.send(req, as: UserProfileUpdateResponse.self)
    }

    func updateUserProfileImage(imageData: Data, mimeType: String, filename: String) async throws -> UserProfileUpdateResponse {
        let multipart = MultipartFormData()
        multipart.appendFile(name: "image", data: imageData, mimeType: mimeType, filename: filename)
        let req = client.request(
            path: BaseAPIConstants.userProfile,
            method: "PATCH",
            body: multipart.bodyData,
            contentType: multipart.contentType
        )
        return try await client.send(req, as: UserProfileUpdateResponse.self)
    }

    func updateProfileSelective(
        fields: [String: String],
        imageData: Data?,
        imageMimeType: String?,
        imageFilename: String?
    ) async throws -> UserProfileUpdateResponse {
        let multipart = MultipartFormData()
        for (k, v) in fields { multipart.appendField(name: k, value: v) }
        if let data = imageData, let mime = imageMimeType, let name = imageFilename {
            multipart.appendFile(name: "user_profile_image", data: data, mimeType: mime, filename: name)
        }
        let req = client.request(
            path: BaseAPIConstants.userProfile,
            method: "PATCH",
            body: multipart.bodyData,
            contentType: multipart.contentType
        )
        return try await client.send(req, as: UserProfileUpdateResponse.self)
    }

    func getChatResponse(
        hashcode: String,
        enterpriseId: String,
        question: String,
        sessionId: String
    ) async throws -> ChatMessageModel {
        // Trailing slash avoids Django's APPEND_SLASH 301 redirect (which iOS
        // would follow without the auth header). Matches the slash-terminated
        // convention of the list endpoints.
        let path = "\(BaseAPIConstants.mainChat)\(hashcode)/\(enterpriseId)/"
        let query = [
            URLQueryItem(name: "question", value: question),
            URLQueryItem(name: "session_id", value: sessionId)
        ]
        let req = client.request(path: path, query: query)
        return try await client.send(req, as: ChatMessageModel.self)
    }

    func getChatHistory(channelId: String, enterpriseId: String) async throws -> ChatHistoryModel {
        let path = "\(BaseAPIConstants.chatHistory)\(channelId)/\(enterpriseId)/"
        let req = client.request(path: path)
        return try await client.send(req, as: ChatHistoryModel.self)
    }

    func getChatListResponse(hashcode: String) async throws -> ChatListModelResponse {
        let req = client.request(
            path: BaseAPIConstants.mainChatList,
            query: [URLQueryItem(name: "channel_id", value: hashcode)],
            headers: ["Content-Type": "application/x-www-form-urlencoded"]
        )
        return try await client.send(req, as: ChatListModelResponse.self)
    }

    func getEnterpriseDiscoverList(
        hashcode: String,
        page: Int,
        search: String,
        industries: String
    ) async throws -> EnterPriseDiscoverModel {
        let query = [
            URLQueryItem(name: "channel_id", value: hashcode),
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "search", value: search),
            URLQueryItem(name: "industries", value: industries)
        ]
        let req = client.request(path: BaseAPIConstants.mainDiscoverList, query: query)
        return try await client.send(req, as: EnterPriseDiscoverModel.self)
    }

    func getIndustries() async throws -> IndustryResponse {
        let req = client.request(path: BaseAPIConstants.industries)
        return try await client.send(req, as: IndustryResponse.self)
    }

    func getEnterprises(enterpriseId: String) async throws -> EnterpriseApiResponse {
        let req = client.request(
            path: BaseAPIConstants.enterprises,
            query: [URLQueryItem(name: "enterprise_id", value: enterpriseId)]
        )
        return try await client.send(req, as: EnterpriseApiResponse.self)
    }

    func createProfile(
        fields: [String: String],
        imageData: Data,
        imageMimeType: String,
        imageFilename: String
    ) async throws -> UserProfileUpdateResponse {
        let multipart = MultipartFormData()
        for (k, v) in fields { multipart.appendField(name: k, value: v) }
        multipart.appendFile(name: "user_profile_image", data: imageData, mimeType: imageMimeType, filename: imageFilename)
        let req = client.request(
            path: BaseAPIConstants.userProfile,
            method: "PATCH",
            body: multipart.bodyData,
            contentType: multipart.contentType
        )
        return try await client.send(req, as: UserProfileUpdateResponse.self)
    }

    func userLogout(refreshToken: String) async throws -> LogoutDataModel {
        // Retrofit @FormUrlEncoded body — Swift equivalent is the same percent-
        // encoded string. Mirrors `apiService.userLogout(refresh_token)`.
        let bodyString = "refresh_token=\(refreshToken.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? refreshToken)"
        let req = client.request(
            path: BaseAPIConstants.userLogout,
            method: "POST",
            body: bodyString.data(using: .utf8),
            contentType: "application/x-www-form-urlencoded"
        )
        return try await client.send(req, as: LogoutDataModel.self)
    }

    func registerDeviceToken(body: DeviceTokenRequest) async throws -> DeviceTokenResponse {
        let req = client.request(
            path: BaseAPIConstants.registerDeviceToken,
            method: "POST",
            body: try JSONEncoder().encode(body),
            contentType: "application/json"
        )
        return try await client.send(req, as: DeviceTokenResponse.self)
    }

    // MARK: - Per-enterprise state mutations

    /// Form-url-encode a flat param map (mirrors Retrofit @FormUrlEncoded @Field). Booleans
    /// are sent as "true"/"false" to match Retrofit's Boolean serialization.
    private func formEncoded(_ params: [String: String]) -> Data? {
        params.map { key, value in
            let k = key.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? key
            let v = value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value
            return "\(k)=\(v)"
        }.joined(separator: "&").data(using: .utf8)
    }

    func blacklistEnterprise(enterprisePk: Int, blacklist: Bool) async throws -> GenericStatusResponse {
        let req = client.request(
            path: BaseAPIConstants.userBlacklistEnterprise,
            method: "POST",
            body: formEncoded(["enterprise_pk": String(enterprisePk), "blacklist": blacklist ? "true" : "false"]),
            contentType: "application/x-www-form-urlencoded"
        )
        return try await client.send(req, as: GenericStatusResponse.self)
    }

    func muteEnterprise(enterpriseId: Int, muteStatus: Bool) async throws -> GenericStatusResponse {
        let req = client.request(
            path: BaseAPIConstants.muteEnterprise,
            method: "POST",
            body: formEncoded(["enterprise_id": String(enterpriseId), "mute_status": muteStatus ? "true" : "false"]),
            contentType: "application/x-www-form-urlencoded"
        )
        return try await client.send(req, as: GenericStatusResponse.self)
    }

    func updateEnterprisePromotional(optIn: Bool, enterpriseId: Int, channelId: String) async throws -> GenericStatusResponse {
        let req = client.request(
            path: BaseAPIConstants.userChannelEnterpriseConfig,
            method: "POST",
            body: formEncoded([
                "promotional_opt_in": optIn ? "true" : "false",
                "enterprise_id": String(enterpriseId),
                "channel_id": channelId
            ]),
            contentType: "application/x-www-form-urlencoded"
        )
        return try await client.send(req, as: GenericStatusResponse.self)
    }
}
