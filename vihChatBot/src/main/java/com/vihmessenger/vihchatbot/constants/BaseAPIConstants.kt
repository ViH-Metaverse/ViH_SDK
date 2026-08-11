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
    const val USER_SIGNUP_LOGIN = "account/signup-login/"

    // Email-OTP token exchange: client posts a verified Cognito ID token (+ mobile for
    // hashcode-matched delivery) and receives the existing app-session tokens.
    const val EMAIL_LOGIN = "account/email-login/"

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