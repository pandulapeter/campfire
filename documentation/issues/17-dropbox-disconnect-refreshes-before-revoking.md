# 17 · Disconnect revokes with the stored access token without refreshing it, so an idle grant is never revoked

**Severity:** medium (the app says "disconnected" while the grant stays live in the user's Dropbox) · **Area:** `:data:source:remote:implementation`

## Cause

`DropboxSyncProvider.disconnect()` (:122–133) posts `credentials.accessToken` raw to `/auth/token/revoke`. Access
tokens live four hours; an expired one gets 401, which is caught and printed. Dropbox revokes the refresh token
*through* a valid access token, so after four idle hours nothing is revoked.

## Fix

```kotlin
override suspend fun disconnect() {
    val accessToken = try {
        withTimeoutOrNull(REVOKE_TIMEOUT_MILLIS) { accessToken() }   // refreshes if stale; see accessToken()
    } catch (exception: CancellationException) { throw exception } catch (exception: Exception) { null }
    credentialsStore.save(null)
    if (accessToken.isNullOrEmpty()) return
    try {
        withTimeoutOrNull(REVOKE_TIMEOUT_MILLIS) { httpClient.post(REVOKE_URL) { header("Authorization", "Bearer $accessToken") } }
    } catch (exception: CancellationException) { throw exception } catch (exception: Exception) {
        println("Could not revoke the Dropbox token: ${exception.message}")
    }
}
```

- `accessToken()` throws `SyncAuthorizationException` when nothing is connected; that is the `null` case.
- The local credentials are still cleared **before** the revoke request goes out, and the refresh is bounded by
  `REVOKE_TIMEOUT_MILLIS = 10_000L`, so a dead network cannot keep the user "connected".
- Keep the comment: best effort, a token that cannot be revoked simply expires.
- `SyncRepositoryImpl.disconnect()` already `cancelAndJoin`s the run first, so no request races the refresh.
