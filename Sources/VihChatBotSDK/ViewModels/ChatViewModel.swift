import Foundation

/// Mirrors `viewmodel/ChatViewModel.kt`.
public final class ChatViewModel: BaseViewModel {

    private let chatRepository: ChatRepository

    public let chatMessageLiveData = LiveData<ChatMessageModel>()
    public let chatHistoryLiveData = LiveData<ChatHistoryModel>()
    public let enterpriseDetails = LiveData<EnterpriseApiResponse>()
    public let errorLiveData = SingleLiveEvent<String>()

    public init(loaderHost: LoaderHost?) {
        self.chatRepository = ChatRepository(apiService: APIClient.shared.apiService, loaderHost: loaderHost)
        super.init()
    }

    public func getChatResponse(
        showBlockingLoader: Bool,
        question: String,
        sessionId: String,
        hashcode: String,
        enterpriseId: String
    ) {
        launch { [weak self] in
            guard let self = self else { return }
            do {
                let response = try await self.chatRepository.getChatResponse(
                    showBlockingLoader: showBlockingLoader,
                    question: question, sessionId: sessionId,
                    hashcode: hashcode, enterpriseId: enterpriseId
                )
                self.chatMessageLiveData.postValue(response)
            } catch {
                self.errorLiveData.postValue(error.localizedDescription)
            }
        }
    }

    public func getChatHistoryResponse(showBlockingLoader: Bool, channelId: String, enterpriseId: String) {
        launch { [weak self] in
            guard let self = self else { return }
            do {
                let response = try await self.chatRepository.getChatHistory(
                    showBlockingLoader: showBlockingLoader,
                    channelId: channelId, enterpriseId: enterpriseId
                )
                self.chatHistoryLiveData.postValue(response)
            } catch {
                CorrelationLogger.warn(message: "chatHistory failed", error: error)
            }
        }
    }

    public func getEnterpriseModel(showBlockingLoader: Bool, enterpriseId: String) {
        launch { [weak self] in
            guard let self = self else { return }
            do {
                let response = try await self.chatRepository.getEnterpriseDetails(
                    showBlockingLoader: showBlockingLoader, enterpriseId: enterpriseId
                )
                self.enterpriseDetails.postValue(response)
            } catch {
                CorrelationLogger.warn(message: "enterprise details failed", error: error)
            }
        }
    }
}
