# 23 · Start-up offline reports "not connected" when the account was never named

**Severity:** low (narrow) · **Area:** `:data:source:remote:implementation` (`DropboxSyncProvider.loadAccount`)

## Cause

`DropboxSyncProvider.loadAccount` (:106–136) already survives a network failure by returning the stored
`displayName` — but only `credentials.displayName.takeIf { it.isNotEmpty() }`. `completeAuthorization` (:98–119)
saves the credentials *before* the first `loadAccount`, "so that a failure to read the account still leaves a usable
connection"; if that first read fails, `displayName` stays empty, and every later offline start-up returns null →
`SyncRepositoryImpl.restore()` shows `Disconnected` although the refresh token is fine. The user then reconnects an
account that was never broken.

## Fix

In the generic `catch` of `loadAccount`, fall back to the account id when the name is empty:

```kotlin
val name = credentials.displayName.ifEmpty { credentials.accountId }.ifEmpty { return null }
SyncAccount(providerId = id, displayName = name, email = credentials.email.takeIf(String::isNotEmpty))
```

`accountId` is what `completeAuthorization` writes on line 104, so it is always there. The next online start-up
replaces it with the real name through the `credentialsStore.update` on line 110. Combined with issue 22, the
remaining null case (a real `SyncAuthorizationException`) is shown as a connection failure rather than as nothing.
