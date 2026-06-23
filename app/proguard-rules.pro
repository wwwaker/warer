# Warer ProGuard / R8 Rules

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.wwwaker.warer_android.**$$serializer { *; }
-keepclassmembers class com.wwwaker.warer_android.** {
    *** Companion;
}
-keepclasseswithmembers class com.wwwaker.warer_android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit + OkHttp
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# mXparser
-keep class org.mariuszgromada.math.mxparser.** { *; }
-keep class org.mariuszgromada.math.mxparser.parsertokens.** { *; }

# Compose
-dontwarn androidx.compose.**
