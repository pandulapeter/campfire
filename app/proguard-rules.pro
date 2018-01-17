
# Annotations
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
-dontwarn javax.annotation.ParametersAreNonnullByDefault

# Retrofit
-dontnote retrofit2.Platform
-dontwarn retrofit2.Platform$Java8
-keepattributes Signature
-keepattributes Exceptions

# Okio, OkHTTP
-dontwarn okio.**
-dontwarn okhttp3.**