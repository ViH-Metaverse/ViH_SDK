// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "VihChatBotSDK",
    defaultLocalization: "en",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(
            name: "VihChatBotSDK",
            targets: ["VihChatBotSDK"]
        )
    ],
    dependencies: [
        // Ultravox iOS SDK — voicebot WebRTC client (mirror of Android ai.ultravox).
        // Pin to a tag once Ultravox publishes an official iOS release.
        // .package(url: "https://github.com/fixie-ai/ultravox-client-sdk-ios.git", from: "0.1.0"),

        // Firebase iOS — used for FCM cross-platform push parity with Android.
        // 11.x is the current major; pin to .upToNextMajor for predictable updates.
        .package(url: "https://github.com/firebase/firebase-ios-sdk.git", from: "11.0.0"),

        // AWS Amplify Swift (Cognito) — powers passwordless email-OTP sign-in.
        // Mirror of the Android com.amplifyframework:aws-auth-cognito dependency.
        .package(url: "https://github.com/aws-amplify/amplify-swift.git", from: "2.27.0"),
    ],
    targets: [
        .target(
            name: "VihChatBotSDK",
            dependencies: [
                // .product(name: "UltravoxClient", package: "ultravox-client-sdk-ios"),
                .product(name: "FirebaseMessaging", package: "firebase-ios-sdk"),
                .product(name: "Amplify", package: "amplify-swift"),
                .product(name: "AWSCognitoAuthPlugin", package: "amplify-swift"),
                // Exposes AuthCognitoTokensProvider (used to read the Cognito ID token).
                .product(name: "AWSPluginsCore", package: "amplify-swift"),
            ],
            path: "Sources/VihChatBotSDK",
            resources: [
                .process("Resources")
            ]
        ),
        .testTarget(
            name: "VihChatBotSDKTests",
            dependencies: ["VihChatBotSDK"],
            path: "Tests/VihChatBotSDKTests"
        )
    ]
)
