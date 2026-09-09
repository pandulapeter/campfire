# kotlinx.serialization ships its own consumer rules; only the coroutine machinery needs an explicit keep.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
