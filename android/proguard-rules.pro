# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK's proguard-defaults.txt file.

# Keep Jackson annotations for JSON serialization
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod

-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

-keep class com.timemanagement.core.dataclass.** { *; }
