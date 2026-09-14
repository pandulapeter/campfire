# 18 · A 401 is never answered with a refresh, so a corrected device clock ends in "reconnect your account"

**Severity:** medium · **Area:** `:data:source:remote:implementation` (`DropboxSyncProvider`)

## Cause

`accessToken()` (:345–355) decides freshness purely from the locally computed `expiresAt`; `ensureSuccessful` (:330)
maps any 401 straight to `SyncAuthorizationException`, which the repository reports as `AUTHORIZATION` ("connect the
account again"). Connect with the clock a day ahead, correct the clock: for the next day the local check says fresh,
Dropbox says expired after four hours, and the refresh token that would fix it is never used.

## Fix

1. Give `accessToken()` a `forceRefresh: Boolean = false` parameter; when true, skip the freshness check and refresh.
2. In `request(block)`, retry **once** on 401 after a forced refresh. The block builds its own request and calls
   `accessToken()` inside, so a forced refresh must make the next call refresh: implement it as
   `credentialsStore.update { it?.copy(accessToken = "") to Unit }` (an empty access token is already treated as
   "refresh now" by the freshness check) followed by the block again:

   ```kotlin
   var hasRefreshed = false
   while (true) {
       val response = transport { block() }
       if (response.status == HttpStatusCode.Unauthorized && !hasRefreshed) {
           hasRefreshed = true
           invalidateAccessToken()
           continue
       }
       …existing retry-after handling…
   }
   ```

   A second 401 after a fresh token is the real refusal and goes through `ensureSuccessful` as today.
3. `loadAccount` and `exchange` are unaffected (`exchange` is the refresh itself; a 400/401 there stays a refusal).
4. Test with `MockEngine`: 401 then 200 → success and exactly one call to `/oauth2/token`.
