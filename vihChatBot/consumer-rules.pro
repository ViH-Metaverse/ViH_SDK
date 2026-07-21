# Consumer ProGuard rules — applied to apps that consume this SDK.
# Rules here flow through the AAR; rules in proguard-rules.pro only apply
# when building the library standalone.

# Preserve runtime annotations (Gson reads @SerializedName via reflection)
-keepattributes *Annotation*
# Signature carries generic type info; InnerClasses + EnclosingMethod let
# TypeToken's anonymous subclasses keep their <T> parameter at runtime.
-keepattributes Signature,InnerClasses,EnclosingMethod

# Data models — Gson serializes/deserializes these by field name
-keep class com.vihmessenger.vihchatbot.data.model.** { *; }
-keep class com.vihmessenger.vihchatbot.data.CpaasButton { *; }

# Any field annotated with @SerializedName must keep its name
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Gson — keep TypeToken and any anonymous subclass so generic info survives R8.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-dontwarn sun.misc.**

# Retrofit interfaces — methods are invoked via reflection
-keep interface com.vihmessenger.vihchatbot.data.services.ApiService { *; }
-keep interface com.vihmessenger.vihchatbot.data.services.BaseApiService { *; }

# Public SDK entry points consumed by host apps
-keep class com.vihmessenger.vihchatbot.utils.FloatingButtonView { public *; }
-keep class com.vihmessenger.vihchatbot.AppController { public *; }
-keep class com.vihmessenger.vihchatbot.constants.AppConstants { public *; }
-keep class com.vihmessenger.vihchatbot.utils.sharedPreference.Prefs { public *; }
-keep class com.vihmessenger.vihchatbot.ui.activity.home.DashBoardActivity { public *; }
-keep class com.vihmessenger.vihchatbot.ui.activity.splash.SplashActivity { public *; }

# Ultravox client SDK — calls into it are reflection-free Kotlin, but the SDK
# itself uses OkHttp WebSocket + JSON internally, so keep its full surface.
-keep class ai.ultravox.** { *; }

# Ultravox transitively depends on livekit-android, which references
# okhttp3.internal.Util — an internal symbol removed in newer OkHttp.
-dontwarn okhttp3.internal.Util

# AWS Amplify (Cognito email-OTP). The smithy runtime references optional HTTP
# engines (okhttp4, etc.) that aren't bundled — suppress the R8 missing-class
# warnings. Amplify/AWS SDK ship their own keep rules via their AARs.
-dontwarn aws.smithy.kotlin.runtime.http.engine.okhttp4.OkHttp4Engine
-dontwarn aws.smithy.kotlin.runtime.**
-dontwarn com.amplifyframework.**
