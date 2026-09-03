# Ktor, Ktorfit and kotlinx.serialization ship their own consumer rules; only the coroutine machinery needs an explicit keep.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn org.slf4j.**
