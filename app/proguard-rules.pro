# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class com.duggustore.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.duggustore.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Models
-keepclassmembers class com.duggustore.app.data.model.** { *; }

# Razorpay Checkout SDK
# Its classes carry proguard.annotation.Keep annotations but that annotation
# type isn't on the classpath, which fails the release build with R8's
# "Missing class" error. Annotation-only — never needed at runtime.
-dontwarn proguard.annotation.Keep
# GPay's in-app client is an optional Razorpay integration — referenced but
# never shipped; Razorpay guards the code path at runtime.
-dontwarn com.google.android.apps.nbu.**
-dontwarn com.razorpay.**
# The SDK uses reflection/serialization internally; keep it whole.
-keep class com.razorpay.** { *; }
