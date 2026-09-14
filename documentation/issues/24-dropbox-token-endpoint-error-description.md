# 24 · Token-endpoint failures lose their reason

**Severity:** low (diagnostics) · **Area:** `:data:source:remote:implementation`

`DropboxSyncProvider.exchange` (:275) logs `errorSummary()`, which parses `error_summary`; `/oauth2/token` answers with
standard OAuth `error` / `error_description`. An `invalid_grant` (spent code, revoked refresh token) logs
"Dropbox refused the authorization: 400 " with nothing after it.

## Fix

Add to `DropboxModels.kt`:

```kotlin
@Serializable
internal data class DropboxOAuthErrorResponse(
    val error: String = "",
    @SerialName("error_description") val errorDescription: String = "",
)
```

and in `exchange`, on a non-success status decode that (ignore-unknown-keys is on) and build the message from
`"$error: $errorDescription"`, falling back to `errorSummary()` when both are empty. Keep the 429/5xx → network branch.
