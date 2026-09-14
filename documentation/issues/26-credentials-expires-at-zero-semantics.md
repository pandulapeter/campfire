# 26 · `expiresAt == 0` is documented as "never expires" but treated as "always expired"

**Severity:** low · **Area:** `:data:source:remote:implementation`

`SyncCredentialsDocument.kt:27` says 0 means the token is assumed not to expire; `DropboxSyncProvider.accessToken()`
(:352) evaluates `now < 0 - 60_000` as false and refreshes on every request in that state. Dropbox always returns
`expires_in`, so it self-heals after the first refresh; only the doc and the second-provider path are wrong.

## Fix

Make the code match a sensible contract rather than the other way round: **0 means "unknown, refresh before use"**
(a provider that hands out non-expiring tokens should still set a far-future value). Reword the KDoc on
`expiresAt`, and in `expiryOf` keep returning 0 for `expiresInSeconds <= 0`. No behaviour change for Dropbox.
