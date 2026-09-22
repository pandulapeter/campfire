# 45 · When Dropbox refuses the access token mid-run, every transfer in flight renews it again, one after another

**Severity:** minor (all platforms; rare — a token Dropbox stops accepting before the device thinks it expires: a clock
changed by hand or by a time-zone/NTP jump, a token revoked server side; costs up to six serial token requests during
which every request of the run waits, and more ways for a busy token endpoint to end the run) · **Area:** `:data:source:remote:implementation` (`DropboxSyncProvider.request` / `accessToken`)

## Symptom
Six transfers are in flight (`CONCURRENT_TRANSFERS`); all six are answered 401. Each of them then forces its own
token renewal. The renewals run one after another under the credentials lock (each a request with a 60 s timeout),
every other request of the run waits for all of them, and the token endpoint is asked six times in a row — where a 429
or 5xx on any one of those renewals ends the whole run as a network failure (`DropboxSyncProvider.kt:460-461`).
If the service ever did retire the previous access token on renewal, five of the six retries would be sent with a
token already replaced and be reported as a refusal that needs the account connected again (second 401 → `SyncAuthorizationException`,
`:345`, `:407`); nothing in the code depends on it not doing so.

## Cause
`DropboxSyncProvider.kt:340-355` and `:431-448`:

```kotlin
if (response.status == HttpStatusCode.Unauthorized && !hasRefreshed) {
    hasRefreshed = true
    accessToken(forceRefresh = true)
    continue
}
…
if (!forceRefresh && credentials.accessToken.isNotEmpty() && now < credentials.expiresAt - EXPIRY_MARGIN_MILLIS) {
    return@update credentials to credentials.accessToken
}
val token = exchange(…refresh_token…)
```

`forceRefresh` renews unconditionally, even when another request has renewed the refused token a moment earlier.
The lock serialises the renewals (so they cannot overwrite each other — that part is sound) but does not make them
one.

## Fix
Renew only if the stored token is still the one that was refused.

1. `request` obtains the token and hands it to the call, so it knows which one was refused:
   ```kotlin
   private suspend fun request(block: suspend (accessToken: String) -> HttpResponse): HttpResponse {
       var attempt = 0
       var refusedToken: String? = null
       while (true) {
           var token = ""
           // The token is asked for inside transport, as it is today from inside block: a renewal whose answer
           // cannot be decoded must stay a network failure of the run rather than become a per-file one.
           val response = transport {
               token = accessToken(refused = refusedToken)
               block(token)
           }
           if (response.status == HttpStatusCode.Unauthorized && refusedToken == null) {
               refusedToken = token
               continue
           }
           …
   ```
   The three call sites (`rpc` `:311`, `download` `:220`, `upload` `:245`) use the parameter in their
   `Authorization` header instead of calling `accessToken()` inside the block.
2. `accessToken(refused: String? = null)`: inside the `update`, renew when `refused != null &&
   credentials.accessToken == refused` or when the stored token is empty or about to expire; otherwise return the
   stored one — which, when it differs from `refused`, is the one another request has just renewed. KDoc: "A token that
   was refused is renewed only while it is still the stored one: six transfers refused at once make one renewal, and
   the other five use its result."
3. `disconnect` (`:133`) keeps calling `accessToken()` with no argument.
4. Do not move the renewal out of `SyncCredentialsStore.update` — that is what keeps two renewals from overwriting
   each other's refresh token.

## Tests
`DropboxRequestTest`:
- `several requests refused at once renew the token once`: storage with `expiresAt = Long.MAX_VALUE`; the handler
  answers 401 to any request carrying `Bearer access`, 200 to `Bearer renewed`, and counts `TOKEN_URL` calls
  (answering `{"access_token":"renewed","expires_in":14400}`). Run `provider.download(...)` six times concurrently
  (`coroutineScope { repeat(6) { launch { … } } }`): all succeed, token calls == 1.
- The existing `refreshes the token once when dropbox refuses it` and `believes a refusal of a token it has just
  refreshed` keep passing (the second one: 401 for every token → still `SyncAuthorizationException` after one renewal).

## Verify
`./gradlew :data:source:remote:implementation:desktopTest`. Manually: with a connected desktop build, set the system
clock back five hours after a sync, start **Sync now** with many files to move: the run completes, and the log shows a
single "refresh" exchange.

## Docs
`data/source/remote/implementation/CLAUDE.md`, the OAuth bullet: after "a 401 is answered once with a forced refresh
and the request sent again", add "— a refresh only of the token that was refused, so that the transfers refused
together share one renewal".

## Touches
- `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt`
- `data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxRequestTest.kt`
- `data/source/remote/implementation/CLAUDE.md`

## Depends on
05 edits `transport` and `disconnect` in the same file; do 05 first, then this (the snippet above relies on
`transport` as 05 leaves it, which changes nothing it uses).
