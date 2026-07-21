import Foundation

public struct LoanApprovalRequest: Codable {
    public var session_id: String
    public init(session_id: String) { self.session_id = session_id }
}

/// Response from POST /main/loan-approval/. Top-level envelope carries
/// status/message/errorCode plus flat `callId` and `voiceWsUrl`.
public struct LoanApprovalResponse: Codable {
    public var status: Bool?
    public var message: String?
    public var data: LoanApprovalData?
    public var callId: String?
    public var voiceWsUrl: String?
    public var errorCode: String?

    enum CodingKeys: String, CodingKey {
        case status, message, data
        case callId = "call_id"
        case voiceWsUrl = "voice_ws_url"
        case errorCode = "error_code"
    }
}

public struct LoanApprovalData: Codable {
    public var url: String?
    public var callId: String?
    public var requestId: String?
    public var voicebotId: String?
    public var customerPhone: String?
    public var callSid: String?
    public var status: String?
    public var correlationMethod: String?
    public var timestamp: String?
}

public struct CallDetailsRequest: Codable {
    public var call_id: String
    public var session_id: String
    public init(call_id: String, session_id: String) {
        self.call_id = call_id
        self.session_id = session_id
    }
}
