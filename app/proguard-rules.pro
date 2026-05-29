# Keep Gson model classes (serialized via reflection).
-keep class com.stockwidget.app.data.model.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
