import Foundation

/// Mirrors `data/repository/ChatRepository.kt`.
public final class ChatRepository: BaseRepository {

    private let apiService: ApiService

    public init(apiService: ApiService, loaderHost: LoaderHost?) {
        self.apiService = apiService
        super.init(loaderHost: loaderHost)
    }

    public func getChatResponse(
        showBlockingLoader: Bool,
        question: String,
        sessionId: String,
        hashcode: String,
        enterpriseId: String
    ) async throws -> ChatMessageModel {
        try await doSafeAPIRequest(showBlockingLoader: showBlockingLoader) {
            try await self.apiService.getChatResponse(
                hashcode: hashcode,
                enterpriseId: enterpriseId,
                question: question,
                sessionId: sessionId
            )
        }
    }

    public func getChatHistory(
        showBlockingLoader: Bool, channelId: String, enterpriseId: String
    ) async throws -> ChatHistoryModel {
        try await doSafeAPIRequest(showBlockingLoader: showBlockingLoader) {
            try await self.apiService.getChatHistory(channelId: channelId, enterpriseId: enterpriseId)
        }
    }

    public func getChatListResponse(
        showBlockingLoader: Bool, hashcode: String
    ) async throws -> ChatListModelResponse {
        try await doSafeAPIRequest(showBlockingLoader: showBlockingLoader) {
            try await self.apiService.getChatListResponse(hashcode: hashcode)
        }
    }

    public func getEnterpriseDetails(
        showBlockingLoader: Bool, enterpriseId: String
    ) async throws -> EnterpriseApiResponse {
        try await doSafeAPIRequest(showBlockingLoader: showBlockingLoader) {
            try await self.apiService.getEnterprises(enterpriseId: enterpriseId)
        }
    }
}
