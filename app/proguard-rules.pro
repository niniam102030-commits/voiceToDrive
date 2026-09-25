# ProGuard / R8 rules for the Audio → Drive app.
#
# `isMinifyEnabled` is currently false for both build types, so nothing is
# stripped at the moment. The rules below are here so that turning R8 on does
# not break the libraries we rely on.

# ------------------------------------------------------------------ AppAuth
-keep class net.openid.appauth.** { *; }
-dontwarn net.openid.appauth.**

# --------------------------------------------------------------- Retrofit/Gson
# Retrofit speaks to our Drive API interface through reflection, and Gson fills
# the JSON models from raw field names, so both must be preserved.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep class com.personal.audioapp.network.** { *; }
-keepclasseswithmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Reflection based JSON parsing (Gson): keep the generic type information.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# --------------------------------------------------------------- Room / KSP
# Room generates implementations referenced by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ------------------------------------------------------- FFmpegKit (maintained fork)
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# ---------------------------------------------------------------- Coroutines
-dontwarn kotlinx.coroutines.**
