# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class ai.lillith.pocketstream.network.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
