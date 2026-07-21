import Foundation

/// Mirrors `api/services/AuthInterceptor.kt`. Skips injection when the caller
/// has already set `Authorization` (login/signup endpoints) or when no access
/// token is cached (avoids the "Bearer null" race the Android fix targeted).
enum AuthInterceptor {

    static func inject(into request: inout URLRequest) {
        if request.value(forHTTPHeaderField: "Authorization") != nil { return }
        guard let token = VihChatBotSDK.shared.prefs?.accessToken,
              !token.isEmpty else { return }
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
    }
}
