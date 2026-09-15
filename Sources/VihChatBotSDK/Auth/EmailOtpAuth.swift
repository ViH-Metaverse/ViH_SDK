import Foundation
import Amplify
import AWSCognitoAuthPlugin
import AWSPluginsCore  // AuthCognitoTokensProvider

/// Swift mirror of Android's `CognitoEmailAuth`. Wraps Amplify Auth (Cognito) for
/// passwordless email-OTP sign-in.
///
/// Flow: `requestOtp` attempts a passwordless sign-up first and falls through to a USER_AUTH
/// email-OTP sign-in for already-registered addresses (Cognito emails the code via SES) — see
/// `requestOtp` for why that order is required; `confirmOtp` submits the code and returns the
/// Cognito **ID token**, which the caller exchanges with the backend `account/email-login/`
/// endpoint for the existing app-session tokens.
///
/// NOTE: Not yet compiled against the Amplify Swift toolchain — verify in Xcode. Amplify
/// must be configured once at launch via `configure(with:)` before these calls.
public enum EmailOtpAuth {

    /// True once Amplify Auth has been successfully configured. Guards every Amplify call:
    /// the Amplify Auth category **traps** (an uncatchable precondition, surfacing as
    /// EXC_BREAKPOINT) if used before `Amplify.configure()`. When the Cognito pool isn't set
    /// for this build we never configure it, so callers must degrade gracefully instead.
    public private(set) static var isConfigured = false

    /// Email of the in-flight request — needed by the sign-up confirm step.
    private static var pendingEmail: String?
    /// True when [requestOtp] fell back to sign-up, so [confirmOtp] uses confirmSignUp.
    private static var isSignUpFlow = false

    /// Configures Amplify Auth (Cognito) programmatically from `VihSDKConfig`, mirroring the
    /// Android `AppController.initAmplify()`. No-ops when the pool isn't configured.
    public static func configure(with config: VihSDKConfig) throws {
        // Amplify.configure() must run exactly once per process; skip if already done.
        guard !isConfigured else { return }
        guard !config.cognitoUserPoolId.isEmpty, !config.cognitoAppClientId.isEmpty else {
            CorrelationLogger.warn(message: "EmailOtpAuth.configure: Cognito pool not set — email login/silent restore disabled for this build.")
            return
        }
        // This is the awsCognitoAuthPlugin's OWN config (the object that lives at
        // auth.plugins.awsCognitoAuthPlugin). It must NOT be wrapped in another
        // "plugins"/"awsCognitoAuthPlugin" layer — AuthCategoryConfiguration(plugins:) already
        // supplies that key. Double-wrapping made Amplify.configure() throw (plugin couldn't
        // find CognitoUserPool), which surfaced as "Email login is not configured".
        let cognitoPluginConfig: JSONValue = [
            "CognitoUserPool": [
                "Default": [
                    "PoolId": .string(config.cognitoUserPoolId),
                    "AppClientId": .string(config.cognitoAppClientId),
                    "Region": .string(config.cognitoRegion)
                ]
            ],
            "Auth": [
                "Default": [
                    "authenticationFlowType": "USER_AUTH"
                ]
            ]
        ]
        try Amplify.add(plugin: AWSCognitoAuthPlugin())
        try Amplify.configure(AmplifyConfiguration(auth: AuthCategoryConfiguration(plugins: ["awsCognitoAuthPlugin": cognitoPluginConfig])))
        isConfigured = true
    }

    /// Requests an email OTP for `email`.
    ///
    /// Sign-up is attempted FIRST, falling through to an EMAIL_OTP sign-in when Cognito reports
    /// the address is already registered. Either way the caller then shows the same code-entry
    /// screen. Mirrors Android's `CognitoEmailAuth.requestOtp`.
    ///
    /// **Why that order.** Both app clients have *prevent user existence errors* enabled, so
    /// `signIn` for an UNREGISTERED email returns a **simulated** EMAIL_OTP challenge — plausible
    /// delivery details and all — rather than an error. It is indistinguishable from a real
    /// challenge, so a sign-in-first flow strands new users on the OTP screen while Cognito never
    /// mails anything. `SignUp` is exempt from that masking (verified against both pools,
    /// 2026-08-20): it reports `usernameExists` for a registered user, which we can branch on.
    public static func requestOtp(email: String) async throws {
        guard isConfigured else { throw APIError("Email login is not configured for this build.") }
        pendingEmail = email
        isSignUpFlow = false
        _ = try? await Amplify.Auth.signOut()
        do {
            try await signUp(email: email)
        } catch {
            // Already registered → normal sign-in. Any OTHER sign-up failure (self-registration
            // disabled, throttling, …) also falls through rather than erroring out, so returning
            // users can still log in if the sign-up path is misconfigured.
            isSignUpFlow = false
            if !isUserAlreadyRegistered(error) {
                CorrelationLogger.warn(
                    message: "signUp failed (\(error.localizedDescription)) — falling back to sign-in"
                )
            }
            let options = AWSAuthSignInOptions(authFlowType: .userAuth(preferredFirstFactor: .emailOTP))
            let result = try await Amplify.Auth.signIn(username: email, options: .init(pluginOptions: options))
            try await ensureOtpDelivered(from: result)
        }
    }

    /// The USER_AUTH flow frequently returns `.continueSignInWithFirstFactorSelection` WITHOUT
    /// emailing a code — `preferredFirstFactor` doesn't reliably collapse the selection step.
    /// We must explicitly select EMAIL_OTP (a second confirmSignIn) to make Cognito issue the
    /// challenge and send the code. Mirrors Android's `CognitoEmailAuth.handleSignInResult`.
    ///
    /// Reaching this means `signUp` already established the email is registered, so failures are
    /// surfaced rather than retried as a sign-up (which would loop).
    private static func ensureOtpDelivered(from result: AuthSignInResult) async throws {
        switch result.nextStep {
        // Matched before the OTP case: the account exists but was never verified (an earlier
        // attempt whose OTP never arrived), so Cognito wants a sign-UP confirmation and sign-in
        // can never complete. Re-send the verification code and finish that stalled sign-up.
        case .confirmSignUp:
            guard let email = pendingEmail else {
                throw APIError("Missing email to re-send the verification code")
            }
            CorrelationLogger.warn(message: "Account exists but is unverified — re-sending the sign-up code")
            isSignUpFlow = true
            _ = try await Amplify.Auth.resendSignUpCode(for: email)
            return
        case .confirmSignInWithOTP:
            return // code already sent — go to the OTP entry screen
        case .continueSignInWithFirstFactorSelection:
            let selected = try await Amplify.Auth.confirmSignIn(
                challengeResponse: AuthFactorType.emailOTP.challengeResponse
            )
            if selected.isSignedIn { return }
            if case .confirmSignInWithOTP = selected.nextStep { return }
            throw APIError("Verification code was not sent (step: \(selected.nextStep))")
        default:
            if result.isSignedIn { return }
            throw APIError("Verification code was not sent (step: \(result.nextStep))")
        }
    }

    /// Passwordless sign-up — the entry point of the request flow. Two outcomes: a brand-new
    /// email → Cognito creates the user and mails the OTP; an already-registered address (whether
    /// confirmed or not) → throws `usernameExists`, which `requestOtp` turns into a sign-in. An
    /// unconfirmed account then surfaces as the `.confirmSignUp` step in `ensureOtpDelivered`,
    /// which re-sends the verification code.
    /// The code is later submitted via `confirmOtp` (which routes to confirmSignUp for this path).
    private static func signUp(email: String) async throws {
        isSignUpFlow = true
        let options = AuthSignUpRequest.Options(userAttributes: [AuthUserAttribute(.email, value: email)])
        let result = try await Amplify.Auth.signUp(username: email, password: nil, options: options)
        if result.isSignUpComplete { return }
        if case .confirmUser = result.nextStep { return } // OTP emailed for verification
        // Nothing was mailed — throwing sends `requestOtp` down its sign-in fallback rather than
        // stranding the user on the OTP screen.
        throw APIError("Couldn't start sign-up for this email (step: \(result.nextStep))")
    }

    /// Cognito's "this email is already a user" signal from `signUp`, i.e. the cue to switch to
    /// the sign-in path. Unlike the auth APIs, SignUp is NOT masked by the pool's
    /// prevent-user-existence setting, so this is a trustworthy discriminator.
    private static func isUserAlreadyRegistered(_ error: Error) -> Bool {
        let text = ("\(error) " + error.localizedDescription).lowercased()
        return text.contains("usernameexists") || text.contains("already exists")
    }

    /// True when a confirm call failed because the auth state machine is on the *other* path
    /// (sign-up vs sign-in) rather than because the code was wrong. Deliberately narrow — it must
    /// never swallow code-mismatch or expired-code errors, which are real user-facing failures.
    private static func isWrongConfirmPath(_ error: Error) -> Bool {
        let text = ("\(error) " + error.localizedDescription).lowercased()
        return text.contains("invalid state")
            || text.contains("current status is confirmed")
            || text.contains("user cannot be confirmed")
    }

    /// Outcome of a silent session restore ([restoreSession]).
    public enum SessionResult {
        /// A live Cognito session; the associated value is a fresh ID token.
        case token(String)
        /// Cognito has no signed-in session (refresh token expired / signed out) → require login.
        case signedOut
        /// Couldn't determine the session (e.g. offline / Amplify not ready) → caller decides.
        case unavailable
    }

    /// Silently restores the Cognito session and returns a fresh ID token when available.
    /// `fetchAuthSession` reads cached tokens and only refreshes over the network when the pool
    /// tokens have expired (and the refresh token is still valid), so this is cheap on a warm
    /// session. Mirrors Android's `CognitoEmailAuth.restoreSession`; used to keep the user signed
    /// in across launches without re-entering an OTP — the caller exchanges the ID token via
    /// `account/email-login/` for app-session tokens.
    public static func restoreSession() async -> SessionResult {
        // Never touch Amplify before it's configured — that traps (EXC_BREAKPOINT). Treat an
        // unconfigured build as "can't determine" so the splash falls back to the existing
        // token / login instead of crashing.
        guard isConfigured else { return .unavailable }
        do {
            let session = try await Amplify.Auth.fetchAuthSession()
            guard session.isSignedIn else { return .signedOut }
            guard let provider = session as? AuthCognitoTokensProvider else { return .unavailable }
            let idToken = try provider.getCognitoTokens().get().idToken
            return idToken.isEmpty ? .unavailable : .token(idToken)
        } catch {
            CorrelationLogger.warn(message: "restoreSession: could not fetch auth session: \(error.localizedDescription)")
            return .unavailable
        }
    }

    /// Submits the OTP `code`; on success returns the Cognito ID token. Routes to confirmSignUp
    /// for the sign-up path, else confirmSignIn — and retries down the *other* path if Cognito
    /// says the auth state doesn't match. `isSignUpFlow` is process-local, so it can legitimately
    /// disagree with Cognito's view (the OTP screen can be restored after the app was killed, and
    /// a resend may take a different branch than the original request).
    public static func confirmOtp(code: String) async throws -> String {
        guard isConfigured else { throw APIError("Email login is not configured for this build.") }
        do {
            try await confirm(code: code, asSignUp: isSignUpFlow)
        } catch {
            guard isWrongConfirmPath(error) else { throw error }
            CorrelationLogger.warn(
                message: "confirm rejected the auth state — retrying down the other path"
            )
            try await confirm(code: code, asSignUp: !isSignUpFlow)
        }
        return try await currentIdToken()
    }

    private static func confirm(code: String, asSignUp: Bool) async throws {
        if asSignUp {
            try await confirmSignUp(code: code)
        } else {
            let result = try await Amplify.Auth.confirmSignIn(challengeResponse: code)
            guard result.isSignedIn else {
                throw APIError("Sign-in could not be completed")
            }
        }
    }

    /// Confirms the sign-up OTP, then completes sign-in via autoSignIn so we can read the freshly
    /// issued ID token without asking the user for a second code.
    private static func confirmSignUp(code: String) async throws {
        guard let email = pendingEmail else { throw APIError("Missing email for sign-up confirmation") }
        let result = try await Amplify.Auth.confirmSignUp(for: email, confirmationCode: code)
        guard result.isSignUpComplete else { throw APIError("Sign-up could not be completed") }
        let signIn = try await Amplify.Auth.autoSignIn()
        guard signIn.isSignedIn else { throw APIError("Could not finish sign-in after sign-up") }
    }

    private static func currentIdToken() async throws -> String {
        let session = try await Amplify.Auth.fetchAuthSession()
        guard let provider = session as? AuthCognitoTokensProvider else {
            throw APIError("Session does not expose Cognito tokens")
        }
        return try provider.getCognitoTokens().get().idToken
    }
}
