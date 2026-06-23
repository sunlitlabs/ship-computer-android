-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class dev.jamlab.shipcomputer.**$$serializer { *; }
-keepclassmembers class dev.jamlab.shipcomputer.** {
    *** Companion;
}
-keepclasseswithmembers class dev.jamlab.shipcomputer.** {
    kotlinx.serialization.KSerializer serializer(...);
}
