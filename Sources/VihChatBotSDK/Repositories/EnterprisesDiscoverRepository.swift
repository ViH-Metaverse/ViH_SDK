import Foundation

/// Mirrors `data/repository/EnterprisesDiscoverRepository.kt`.
public final class EnterprisesDiscoverRepository: BaseRepository {

    private let apiService: ApiService

    public init(apiService: ApiService, loaderHost: LoaderHost?) {
        self.apiService = apiService
        super.init(loaderHost: loaderHost)
    }

    public func getEnterpriseDiscoverListResponse(
        showBlockingLoader: Bool,
        hashCode: String,
        page: Int,
        search: String,
        industries: String
    ) async throws -> EnterPriseDiscoverModel {
        try await doSafeAPIRequest(showBlockingLoader: showBlockingLoader) {
            try await self.apiService.getEnterpriseDiscoverList(
                hashcode: hashCode, page: page, search: search, industries: industries
            )
        }
    }

    public func getIndustriesResponse(showBlockingLoader: Bool) async throws -> IndustryResponse {
        try await doSafeAPIRequest(showBlockingLoader: showBlockingLoader) {
            try await self.apiService.getIndustries()
        }
    }
}
