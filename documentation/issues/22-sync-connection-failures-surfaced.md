# 22 · A failed connection attempt leaves the user with the Connect button and no explanation

**Severity:** medium · **Area:** `:data:model`, `:data:repository:implementation`, `:presentation` (Settings)

## Cause

`CampfireViewModel.connectSyncProvider` (:1244–1247) discards the use case's `Boolean`. Every failure path in
`SyncRepositoryImpl.connect` / `completePendingAuthorization` is `println` + `SyncState.Disconnected`. The settings
screen renders `lastOutcome` of a *run* only (`SyncSettings.kt:180–187`). A refused consent, a `state` mismatch or a
failed code exchange just puts "Connect Dropbox" back. On the web that is the whole result of the return-to-Settings
dance.

## Fix

1. **Model.** Add `data class ConnectionFailed(val providerId: SyncProviderId, val reason: SyncFailureReason) : SyncState`.
   `Disconnected` stays a `data object` so its existing uses compile.
2. **Repository.** In `connect`, `completePendingAuthorization` and `fail(...)`: where the state is set to
   `Disconnected` because something went wrong (not on a user cancel), set `ConnectionFailed(providerId, reason)`:
   `Cancelled` from the authenticator with a message → `UNKNOWN`; the service answering `error=` → `AUTHORIZATION`;
   an exception from the token exchange → `SyncNetworkException` → `NETWORK`, `SyncAuthorizationException` →
   `AUTHORIZATION`, else `UNKNOWN`. `restore()` with credentials the service refused (issue 23 narrows that case) →
   `ConnectionFailed(…, AUTHORIZATION)`.
3. **UI.** `SyncSettings.kt`: render `ConnectionFailed` exactly like `Disconnected` (the Connect row) plus a
   `SyncMessage` above it keyed `sync_connection_failed`, text from a new
   `settings_sync_connection_failed_<reason>` string set (three strings; reuse the `settings_sync_failed_*` wording
   where it fits: "Could not reach the service", "Dropbox refused the connection", "The connection did not go
   through"). Pressing Connect goes to `Connecting` as today, which clears the message.
4. Every `when (syncState)` over `SyncState` (grep for `is SyncState.` in `:presentation` and the Android
   `CampfireSyncService`) gets the new branch; the service treats it as disconnected.
5. Hungarian strings; docs in `presentation/CLAUDE.md` if the sync section is described there.
