# 19 · Every Dropbox request rewrites `sync-credentials.json`, under the lock every other transfer needs

**Severity:** medium (perf: a first sync of N files performs N+ atomic file writes) · **Area:** `:data:source:remote:implementation` (`SyncCredentialsStore`)

## Cause

`SyncCredentialsStore.update` (:40–44) calls `write(updated)` unconditionally; `DropboxSyncProvider.accessToken()`
(:352–354) returns the unchanged document from inside `update` on the fast path, and it runs once per request (and
again per retry). Each write is a temp file plus rename (or an OPFS writable) while holding the mutex.

## Fix

```kotlin
suspend fun <T> update(block: suspend (SyncCredentialsDocument?) -> Pair<SyncCredentialsDocument?, T>): T = mutex.withLock {
    val current = read()
    val (updated, result) = block(current)
    // The common case is a token that is still good, and that is not a change worth a file write.
    if (updated != current) write(updated)
    result
}
```

`SyncCredentialsDocument` is a data class, so `!=` is a field comparison. Keep the KDoc promise that the whole
read-change-write happens under the lock. Verify with a log line (temporarily) that a sync of 50 files writes the
credentials at most once (the refresh).
