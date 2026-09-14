# 16 · Dropbox's `too_many_write_operations` (409) is never retried, and the engine's own concurrency provokes it

**Severity:** medium (per-file failures on a first sync until they happen to land alone) · **Area:** `:data:source:remote:implementation` (`DropboxSyncProvider`)

## Cause

`DropboxSyncProvider.request` / `retryAfterMillis` (:295–307) retry only on 429 and 5xx. Dropbox answers concurrent
writes into one namespace with **409** and an `error_summary` of `path/too_many_write_operations/...` (upload) or
`too_many_write_operations/...` (`delete_v2`), and documents it as "back off and retry". `ensureSuccessful` turns it
into `DropboxApiException`, which the engine logs as a per-file failure.

## Fix

1. In `retryAfterMillis()` add a 409 branch that reads the body: Ktor saves bodies by default, so `errorSummary()` can
   be called here and again later.

   ```kotlin
   status == HttpStatusCode.Conflict && errorSummary().contains("too_many_write_operations") ->
       (headers["Retry-After"]?.toLongOrNull() ?: DEFAULT_RETRY_SECONDS) * 1000L
   ```

   `retryAfterMillis` becomes `suspend`. Also honour the JSON form Dropbox uses for 429 bodies
   (`{"error": {"reason": {".tag": "too_many_write_operations"}, "retry_after": 3}}`): add
   `@SerialName("error") val error: JsonObject? = null` to `DropboxErrorResponse` or a small
   `DropboxRateLimitError(retryAfter: Long?)` model, and prefer `retry_after` seconds when present.
2. Keep `MAXIMUM_RETRIES` semantics; the growth of the delay is issue 29.
3. The KDoc of `request` already explains why being rate limited is "the expected answer"; add the 409 case to it.
4. Test in `data/source/remote/implementation/src/commonTest` if the provider is testable with a Ktor `MockEngine`
   (`createHttpClient` is a function in `network/`; a test-only client can be passed to the constructor): three 409
   `too_many_write_operations` answers followed by a 200 must yield `Written`.
