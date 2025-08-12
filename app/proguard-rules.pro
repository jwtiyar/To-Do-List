# Add project specific ProGuard rules here.
# Production-ready configuration for SimplerTask

# Keep Room database entities and DAOs
-keep class io.github.jwtiyar.simplertask.data.** { *; }
-keepclassmembers class io.github.jwtiyar.simplertask.data.** { *; }

# Keep Gson serialization classes for backup functionality
-keep class com.google.gson.** { *; }
-keepclassmembers class ** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep notification receivers
-keep class io.github.jwtiyar.simplertask.NotificationReceiver { *; }

# Keep application class
-keep class io.github.jwtiyar.simplertask.TaskApp { *; }

# Preserve line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep ViewModel classes
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Room specific rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Paging 3 rules
-keep class androidx.paging.** { *; }

# Material Design Components
-keep class com.google.android.material.** { *; }

# Coroutines rules
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory {
    void <init>();
}
-keepclassmembers class kotlinx.coroutines.CoroutineExceptionHandler {
    void <init>();
} 