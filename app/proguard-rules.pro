# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.ventoux.netlens.**$$serializer { *; }
-keepclasseswithmembers class com.ventoux.netlens.** { kotlinx.serialization.KSerializer serializer(...); }

# Ktor CIO engine
-keep class io.ktor.client.engine.cio.** { *; }
-keep class io.ktor.serialization.** { *; }
-dontwarn io.ktor.**

# dnsjava
-keep class org.xbill.DNS.** { *; }
-dontwarn org.xbill.DNS.**

# Google Play Billing
-keep class com.android.billingclient.** { *; }
-keep class com.ventoux.netlens.billing.** { *; }

# Tink (transitive of androidx.security:security-crypto). These annotations are
# referenced by Tink's bytecode but not on the runtime classpath; safe to ignore.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
