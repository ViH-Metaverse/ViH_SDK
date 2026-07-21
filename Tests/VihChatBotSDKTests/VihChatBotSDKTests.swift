import XCTest
import UIKit
@testable import VihChatBotSDK

final class VihChatBotSDKTests: XCTestCase {

    func testConfigureSDK() {
        VihChatBotSDK.shared.configure(
            VihSDKConfig(
                apiBaseURL: URL(string: "https://example.com/")!,
                hashcode: "test-hash",
                sdkVersion: "1.0.0-test",
                isDebug: true
            )
        )
        XCTAssertNotNil(VihChatBotSDK.shared.config)
    }

    func testOTPExtractor() {
        XCTAssertEqual(OTPExtractor.extract(from: "Your OTP is 123456"), "123456")
        XCTAssertEqual(OTPExtractor.extract(from: "Code: 4321 expires soon"), "4321")
        XCTAssertNil(OTPExtractor.extract(from: "No code here"))
    }

    func testDateTimeUtils() {
        let now = DateTimeUtils.currentDateTime()
        XCTAssertFalse(now.isEmpty)
    }

    func testColorHex() {
        XCTAssertNotNil(UIColor(hex: "#1A2B3C"))
        XCTAssertNotNil(UIColor(hex: "FF0000"))
        XCTAssertNil(UIColor(hex: "zzz"))
    }
}
