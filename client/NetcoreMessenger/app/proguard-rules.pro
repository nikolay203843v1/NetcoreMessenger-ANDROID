# Keep Gson TypeToken for generic types
-keepattributes Signature
-keepattributes *Annotation*

# Gson
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data classes for Gson serialization
-keep class com.netcoremessenger.core.data.remote.dto.** { *; }
-keep class com.netcoremessenger.core.data.websocket.** { *; }

# Hilt
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# DataStore
-keep class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { *; }

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Kotlin Serialization (if used)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep sealed classes and their subclasses
-keep class * extends java.io.Serializable

# Proguard optimizations
-optimizations !code/simplification/cast,!field/*,!class/merging/*
-allowaccessmodification
-mergeinterfacesaggressively

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
