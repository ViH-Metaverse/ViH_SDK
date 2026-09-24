package com.vihmessenger.vihchatbot.constants

import com.vihmessenger.vihchatbot.BuildConfig


object BaseAPIConstants {
    const val BASE_URL = BuildConfig.API_BASE_URL
    // Backend has no api/ namespace; this route lives under developer/ and is
    // SimpleRouter-backed (trailing slash required — see ApiService.getSdkFeatures).
    const val SDK_FEATURES = "developer/channels-tracking-code-sdk-feature/"
    const val MAIN_CHAT = "main/chat/"
    const val INDUSTRIES = "main/industries/"
    const val ENTERPRISES = "main/enterprise-details/"
    const val CHAT_HISTORY = "main/chat-history/"
    const val MAIN_CHAT_LIST = "main/get-user-session/"
    const val MAIN_DISCOVER_LIST = "main/enterprises/"
    const val USER_PROFILE = "account/profile/"
    // Passwordless SDK sign-in. `account/sdk-login/` and the older `account/signup-login/`
    // map to the same backend view; sdk-login is the current name and the one new work should
    // target. signup-login survives only because APKs already in the field POST there.
    const val USER_SIGNUP_LOGIN = "account/sdk-login/"

    // Single-use, channel-bound nonce for the attestation on sdk-login. TTL 120s, throttled
    // 30/min per client. Fetch it immediately before requesting the integrity token — not at
    // app start — because the nonce is redeemed before verification, so one nonce is one login
    // attempt and a retry needs a fresh challenge.
    const val SDK_LOGIN_ATTESTATION_CHALLENGE = "account/sdk-login/attestation-challenge/"

    // Refresh-token exchange. Takes {"refresh": "<token>"} and returns {"access": "<token>"} —
    // access only, the refresh token is not rotated. Verified live on api.platform,
    // api.prod.platform and api.messenger (2026-09-23).
    //
    // This is what 401 recovery uses now. Previously the only way to renew was to re-run
    // account/signup-login/, which meant every shipped client depended on that endpoint
    // minting a session from {mobile, channel_id} alone — the thing the backend needs to be
    // able to restrict. See docs/backend-security-work.md.
    const val TOKEN_REFRESH = "account/token/refresh/"

    // Email-OTP login. Two backend generations share this route — Cognito backends take a
    // verified Cognito ID token, backend-SMTP backends take email + otp. See EmailLoginRequest.
    const val EMAIL_LOGIN = "account/email-login/"

    // Backend-SMTP OTP issuance (saas flavour only; 404 on the Cognito backends). Asks the
    // server to email a 6-digit code, valid 10 minutes, single-use, one live code per account.
    const val REQUEST_LOGIN_OTP = "account/request-login-otp/"

    // Subscribe the current authenticated user to a channel (used when switching the
    // channel hashkey in Settings — login already subscribes).
    const val SUBSCRIBE_CHANNEL = "account/subscribe-channel/"
    const val USER_LOGOUT = "account/logout/"

    // Per-enterprise state mutations (read side is the four flags on EnterPriseModel).
    // Block/unblock the enterprise for this user (UserBlacklistEnterprise).
    const val USER_BLACKLIST_ENTERPRISE = "developer/user-blacklist-enterprise/"
    // Mute/unmute the enterprise for this (user, channel).
    const val MUTE_ENTERPRISE = "main/mute-enterprise-user-channel/"
    // Promotional opt-in/out for this (user, channel, enterprise). Note: promotional_opt_in
    // is the INVERSE of the read flag is_promotional_message_blocked.
    const val USER_CHANNEL_ENTERPRISE_CONFIG = "main/user-channel-enterprise-configuration/"

    // AWS migration — Phase 1. Session registry endpoint (architecture §3.3) that maps
    // deviceId -> FCM token in ElastiCache Redis. Path is provisional; the backend team
    // owns the final route. Update here when finalized — no other call sites need to change.
    const val REGISTER_FCM_TOKEN = "main/sdk-device-token/"
}