import Foundation

/// Mirrors `viewmodel/HomeViewModel.kt`.
public final class HomeViewModel: BaseViewModel {

    private let homeRepository: HomeRepository
    private let chatRepository: ChatRepository
    private let enterprisesDiscoverRepository: EnterprisesDiscoverRepository

    public let sdkFeatureLiveData = LiveData<SdkFeatureModel>()
    public let userProfileLiveData = LiveData<UserProfileResponse>()
    public let emailLoginLiveData = LiveData<EmailLoginResponse>()
    public let emailLoginErrorLiveData = SingleLiveEvent<String>()
    public let chatListLiveData = LiveData<ChatListModelResponse>()
    public let enterprisesDiscoverListLiveData = LiveData<EnterPriseDiscoverModel>()
    public let industryListLiveData = LiveData<IndustryResponse>()
    public let errorListLiveData = SingleLiveEvent<String>()
    public let fragmentTransitionLiveData = SingleLiveEvent<String>()

    private var _selectedIndustries: String = ""

    public init(loaderHost: LoaderHost?) {
        let api = APIClient.shared.apiService
        self.homeRepository = HomeRepository(apiService: api, loaderHost: loaderHost)
        self.chatRepository = ChatRepository(apiService: api, loaderHost: loaderHost)
        self.enterprisesDiscoverRepository = EnterprisesDiscoverRepository(apiService: api, loaderHost: loaderHost)
        super.init()
    }

    public func getSdkFeatures(showBlockingLoader: Bool, hashCode: String) {
        launch { [weak self] in
            guard let self = self else { return }
            do {
                let response = try await self.homeRepository.getCustomerHome(
                    showBlockingLoader: showBlockingLoader, hashCode: hashCode
                )
                self.sdkFeatureLiveData.postValue(response.data)
                // Persist for theme + welcome lookup (mirrors Android `getVihSettings()`).
                if let encoded = try? JSONEncoder().encode(response.data),
                   let json = String(data: encoded, encoding: .utf8) {
                    VihChatBotSDK.shared.prefs?.vihSettings = json
                }
                DynamicThemeManager.shared.apply(from: response.data)
            } catch {
                CorrelationLogger.warn(message: "getSdkFeatures: \(error.localizedDescription)")
            }
        }
    }

    public func getUserProfile(showBlockingLoader: Bool, request: UserProfileRequest) {
        launch { [weak self] in
            guard let self = self else { return }
            do {
                let response = try await self.homeRepository.createUserProfile(
                    showBlockingLoader: showBlockingLoader, body: request
                )
                self.userProfileLiveData.postValue(response)
            } catch {
                CorrelationLogger.warn(message: "createUserProfile failed", error: error)
            }
        }
    }

    /// Exchanges a verified Cognito ID token (+ mobile for delivery) for app-session tokens.
    public func emailLogin(showBlockingLoader: Bool, request: EmailLoginRequest) {
        launch { [weak self] in
            guard let self = self else { return }
            do {
                let response = try await self.homeRepository.emailLogin(
                    showBlockingLoader: showBlockingLoader, body: request
                )
                self.emailLoginLiveData.postValue(response)
            } catch {
                self.emailLoginErrorLiveData.postValue(error.localizedDescription)
            }
        }
    }

    public func getChattingListResponse(showBlockingLoader: Bool, hashcode: String) {
        launch { [weak self] in
            guard let self = self else { return }
            do {
                let response = try await self.chatRepository.getChatListResponse(
                    showBlockingLoader: showBlockingLoader, hashcode: hashcode
                )
                self.chatListLiveData.postValue(response)
            } catch {
                CorrelationLogger.warn(message: "chatList failed", error: error)
            }
        }
    }

    public func getEnterpriseDiscoverListResponse(
        showBlockingLoader: Bool,
        hashCode: String,
        page: Int,
        search: String,
        industries: String
    ) {
        launch { [weak self] in
            guard let self = self else { return }
            do {
                let response = try await self.enterprisesDiscoverRepository
                    .getEnterpriseDiscoverListResponse(
                        showBlockingLoader: showBlockingLoader,
                        hashCode: hashCode, page: page, search: search, industries: industries
                    )
                self.enterprisesDiscoverListLiveData.postValue(response)
            } catch {
                self.errorListLiveData.postValue(error.localizedDescription)
            }
        }
    }

    public func getIndustriesListResponse(showBlockingLoader: Bool) {
        launch { [weak self] in
            guard let self = self else { return }
            do {
                let response = try await self.enterprisesDiscoverRepository.getIndustriesResponse(
                    showBlockingLoader: showBlockingLoader
                )
                self.industryListLiveData.postValue(response)
            } catch {
                self.errorListLiveData.postValue(error.localizedDescription)
            }
        }
    }

    public func selectedIndustries() -> String { _selectedIndustries }
    public func setSelectedIndustries(_ industries: String) { _selectedIndustries = industries }
}
