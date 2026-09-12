# Add project specific ProGuard rules here.

# Gson deserializes our API models via reflection (see data/model/*.kt) - keep them and the
# generic signatures Retrofit/Gson need to resolve List<T>, etc. Retrofit and OkHttp ship their
# own consumer-proguard-rules.pro bundled in their AARs, so only our own model classes need this.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.retailapp.android.data.model.** { *; }
