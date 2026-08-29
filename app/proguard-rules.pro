# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\Nafira\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# kotlinx-serialization
-keepattributes *Annotation*, EnclosingMethod, Signature
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.json.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class com.nfrdev.blockblitzhost.blockblitz.** {
    *** Companion;
    *** $serializer;
}
