# Keep Gson model + API response classes (serialized via reflection).
-keep class com.stockwidget.app.data.model.** { *; }
-keep class com.stockwidget.app.data.remote.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
