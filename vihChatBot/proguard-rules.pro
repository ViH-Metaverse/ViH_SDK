# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   https://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keepattributes Signature

# SECURITY: Only keep public SDK API surface, obfuscate all internal classes
# Public SDK entry points
-keep class com.vihmessenger.vihchatbot.utils.FloatingButtonView { public *; }
-keep class com.vihmessenger.vihchatbot.AppController { public *; }

# Data models (needed for Gson serialization/deserialization)
-keep class com.vihmessenger.vihchatbot.data.model.** { *; }
-keep class com.vihmessenger.vihchatbot.data.CpaasButton { *; }

# Constants (needed by consuming apps)
-keep class com.vihmessenger.vihchatbot.constants.AppConstants { public *; }

# Public activities (launched by consuming apps)
-keep class com.vihmessenger.vihchatbot.ui.activity.home.DashBoardActivity { public *; }
-keep class com.vihmessenger.vihchatbot.ui.activity.splash.SplashActivity { public *; }

# SharedPreference class (accessed via AppController.prefs)
-keep class com.vihmessenger.vihchatbot.utils.sharedPreference.Prefs { public *; }

# Retrofit API service interfaces
-keep interface com.vihmessenger.vihchatbot.data.services.ApiService { *; }
-keep interface com.vihmessenger.vihchatbot.data.services.BaseApiService { *; }

# SECURITY: Strip verbose/debug logging from release builds. Log.i is kept so
# diagnostic info from production paths (loan-approval, call-details, etc.)
# remains inspectable via logcat without flipping levels in code.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Keep Gson annotations
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
