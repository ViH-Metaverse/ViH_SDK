import Foundation

/// Lightweight multipart/form-data builder. Replaces Retrofit's @Multipart /
/// @Part / @PartMap. Boundary is a UUID — same convention as OkHttp.
final class MultipartFormData {

    private let boundary = "----VihBoundary-\(UUID().uuidString)"
    private(set) var bodyData = Data()

    var contentType: String { "multipart/form-data; boundary=\(boundary)" }

    func appendField(name: String, value: String) {
        bodyData.append("--\(boundary)\r\n")
        bodyData.append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n")
        bodyData.append("\(value)\r\n")
    }

    func appendFile(name: String, data: Data, mimeType: String, filename: String) {
        bodyData.append("--\(boundary)\r\n")
        bodyData.append("Content-Disposition: form-data; name=\"\(name)\"; filename=\"\(filename)\"\r\n")
        bodyData.append("Content-Type: \(mimeType)\r\n\r\n")
        bodyData.append(data)
        bodyData.append("\r\n")
    }

    func finalise() -> Data {
        bodyData.append("--\(boundary)--\r\n")
        return bodyData
    }
}

private extension Data {
    mutating func append(_ string: String) {
        if let data = string.data(using: .utf8) { append(data) }
    }
}
