# =============== PersonalMoney ProGuard / R8 rules ===============

# --- Keep entry points ---
-keep public class com.personalmoney.MainActivity
-keep public class com.personalmoney.PersonalMoneyApplication
-keep public class com.personalmoney.widget.QuickExpenseWidgetReceiver

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-dontwarn androidx.room.paging.**

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.personalmoney.**$$serializer { *; }
-keepclassmembers class com.personalmoney.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }
-keepclasseswithmembers class com.personalmoney.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Hilt / Dagger ---
-keep,allowobfuscation,allowshrinking class dagger.hilt.android.internal.** { *; }
-keep class * extends dagger.hilt.android.internal.GeneratedComponent { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# --- WorkManager (Hilt workers) ---
-keep class * extends androidx.work.ListenableWorker
-keep class * extends androidx.work.CoroutineWorker
-keepclasseswithmembers class * {
    @dagger.assisted.AssistedInject <init>(...);
}

# --- Glance widget ---
-keep class androidx.glance.appwidget.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }

# --- Retrofit / OkHttp / kotlinx-serialization-converter ---
-keepattributes Signature, *Annotation*
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn retrofit2.Platform$Java8
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepclasseswithmembers,includedescriptorclasses class * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation class * extends retrofit2.Converter

# Frankfurter API response DTOs
-keep class com.personalmoney.core.currency.RatesResponse { *; }

# --- AdMob ---
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-keep class com.google.android.ump.** { *; }

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- Compose ---
-keep,allowobfuscation,allowshrinking class androidx.compose.runtime.snapshots.SnapshotStateObserver

# --- kotlinx.datetime (uses ServiceLoader on some platforms) ---
-keep class kotlinx.datetime.** { *; }
-dontwarn kotlinx.datetime.**

# --- Misc ---
-keep class com.personalmoney.feature.settings.backup.** { *; }
-dontwarn java.lang.invoke.StringConcatFactory
