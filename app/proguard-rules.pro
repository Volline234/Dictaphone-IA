# ==============================================================================
# ProGuard / R8 Rules for Dictaphone IA
# ==============================================================================

# Preserve source file names and line numbers for readable crash stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ------------------------------------------------------------------------------
# Data Models & Entities (Ensure fields are not obfuscated/stripped)
# ------------------------------------------------------------------------------
-keep class com.example.model.** { *; }
-keep class com.example.data.local.** { *; }
-keep class com.example.data.remote.** { *; }

# ------------------------------------------------------------------------------
# AndroidX Room
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------------------------
# Moshi JSON
# ------------------------------------------------------------------------------
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <fields>;
}
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# ------------------------------------------------------------------------------
# OkHttp 3 / Okio
# ------------------------------------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ------------------------------------------------------------------------------
# Kotlin Coroutines
# ------------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
