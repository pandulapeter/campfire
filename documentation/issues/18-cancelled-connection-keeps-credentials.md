# 18 — A connection given up or failed after the token exchange keeps the tokens, and the next launch syncs

**Severity:** the app silently connects and syncs an account the screen said was not connected (all platforms) ·
**Area:** `:data:repository:implementation` (`SyncRepositoryImpl.kt`)

**Read, not run.** This was found by reading the code at HEAD (`2065e47f`); it has not been reproduced in a running
build. The repository test below reproduces it deterministically; the "Verification" section is the by-hand check.

> **Work in progress at review time.** `FakeSyncProvider.kt` (test fake) and `DropboxSyncProvider.kt` had uncommitted
> modifications by another agent when this plan was written (batch deletion). This plan only adds a hook to the fake
> and does not change the provider; quotes are from `git show HEAD:<path>`.

Reviewer finding 3-sync#4.

## What the user sees

The user taps **Connect Dropbox**, approves on Dropbox's page, and — while the app is exchanging the code for tokens,
which on a slow network takes seconds — taps **Cancel** (or leaves the settings screen on a platform where that
cancels, or the storage refuses to reset the index). Settings says *not connected*. On the next launch the app is
connected to that account and **starts a sync run on its own** (`RestoreSyncUseCase`), uploading the library into a
folder the user believes they backed out of.

## Cause

The Dropbox provider stores the tokens before it asks who they belong to
(HEAD `DropboxSyncProvider.kt:96-126`):

```kotlin
        credentialsStore.save(
            SyncCredentialsDocument(
                providerId = id.id,
                accessToken = token.accessToken,
                refreshToken = refreshToken,
                expiresAt = expiryOf(token.expiresInSeconds),
                accountId = token.accountId,
            )
        )
        // Written a second time with the name on it, so that a failure to read the account still leaves a usable
        // connection rather than tokens nobody can put a face to.
        return loadAccount() ?: SyncAccount(providerId = id, id = token.accountId, displayName = token.accountId, email = null)
```

`loadAccount()` is a request; a cancellation during it propagates out of `completeAuthorization` with the tokens
already saved. After it returns, `SyncRepositoryImpl.completePendingAuthorization` (`SyncRepositoryImpl.kt:620-647`)
still reads and may rewrite the index:

```kotlin
            else -> try {
                val account = provider.completeAuthorization(
                    response = RemoteAuthorizationResponse(code = code, state = state),
                    verifier = pending.verifier,
                    redirectUri = pending.redirectUri,
                )
                // …
                val document = loadIndexOrNull()?.adoptedBy(account)
                if (document != null && document.accountId != account.indexKey()) {
                    saveIndex(SyncIndexDocument())
                }
                _syncState.update {
                    SyncState.Connected(account = account, progress = null, lastSyncedAt = null, lastOutcome = null)
                }
                true
            } catch (exception: CancellationException) {
                // Giving up during the token exchange is not the exchange failing: connect() answers it by going back
                // to Disconnected, which it can only do if this arrives there as a cancellation.
                throw exception
            } catch (exception: Exception) {
                fail(
                    providerId = pending.providerId,
                    reason = exception.toFailureReason(),
                    message = "The authorization could not be completed: ${exception.message}",
                )
            }
```

A cancellation reaches `connect` (`:236-241`), which discards the pending authorization and shows `Disconnected`; a
failure of `saveIndex` shows `ConnectionFailed`. Neither forgets the stored tokens. On the next start,
`restoreConnection` (`:160`) finds `provider.isConnected()` true and restores `Connected`, and
`RestoreSyncUseCaseImpl` (`SyncUseCaseImpls.kt:92-99`) starts a run.

## The change

Invoke the **`code-style`** skill before the first edit.

Once `completeAuthorization` has been called, any way out of `completePendingAuthorization` other than `Connected`
forgets what the provider may have stored — locally, without a request (`forgetStoredCredentials`, the same call a
fresh installation uses), and under `NonCancellable`, since the cancellation is exactly the case being handled. The
pending authorization is already cleared at `:598`, and the index is left alone (it may be the good index of this
very account from an earlier connection; see the comment at `:627-628`).

```kotlin
            else -> try {
                val account = provider.completeAuthorization(...)
                ...unchanged...
                true
            } catch (exception: CancellationException) {
                // Giving up during the token exchange is not the exchange failing: connect() answers it by going back
                // to Disconnected, which it can only do if this arrives there as a cancellation. The tokens may be
                // stored by now - a provider writes them before it asks whose they are - and left there, the next
                // launch would find itself connected to an account the screen said was not, and sync it.
                withContext(NonCancellable) { forgetCredentialsOf(provider) }
                throw exception
            } catch (exception: Exception) {
                withContext(NonCancellable) { forgetCredentialsOf(provider) }
                fail(
                    providerId = pending.providerId,
                    reason = exception.toFailureReason(),
                    message = "The authorization could not be completed: ${exception.message}",
                )
            }
```

```kotlin
    /**
     * Drops whatever [provider] stored for an authorization that did not end connected, without telling the service:
     * there is nothing to revoke on the user's behalf for a connection they never saw made. A failure is only logged,
     * since the outcome the caller is on its way to report is the one that matters.
     */
    private suspend fun forgetCredentialsOf(provider: SyncProvider) = try {
        provider.forgetStoredCredentials()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not forget the ${provider.id} credentials: ${exception.message}")
    }
```

(`forgetStoredConnection`, `:293-317`, has the same try/catch inline; it may call this helper too, but it is not
necessary.)

Consequence worth stating: when the user was **reconnecting** an account whose credentials the service had refused
(`ConnectionFailed(AUTHORIZATION)`), cancelling the reconnect during the exchange now also drops those refused
credentials — the next launch shows *not connected* instead of *connection refused*. Both are true and both offer
**Connect**; the index is kept either way, so the deletions made before are still remembered for the same account.

## Tests

`data/repository/implementation/src/commonTest/.../sync/FakeSyncProvider.kt` — replace the `completeAuthorization`
that throws (HEAD `:68-72`) with a hook, default unchanged:

```kotlin
    /** What finishing an authorization does; by default being asked is a mistake of the test. */
    var onCompleteAuthorization: suspend () -> SyncAccount = { throw UnsupportedOperationException() }

    override suspend fun completeAuthorization(
        response: RemoteAuthorizationResponse,
        verifier: String,
        redirectUri: String?,
    ): SyncAccount = onCompleteAuthorization()
```

and mention it in the class KDoc. `SyncRepositoryImplTest.kt`, two tests (redirect URI
`"campfire://sync?code=c&state=state"`; the fake's `buildAuthorizationRequest` uses `state = "state"`):

1. `` `giving up after the tokens were stored forgets them` ``: provider with `connected = false` and
   `onCompleteAuthorization = { connected = true; awaitCancellation() }`; authenticator
   `FakeSyncAuthenticator(outcome = AuthorizationOutcome.Received(REDIRECT))`. `val job = launch { repository.connect(DROPBOX, COMPLETION_PAGE) }`,
   `runCurrent()`, `assertTrue(provider.connected)` (the tokens are "stored"), `job.cancelAndJoin()`. Assert `provider.hasForgottenCredentials`, `!provider.isConnected()`,
   `syncState.value == SyncState.Disconnected`; then `repository.restore().isConnected` is false.
2. `` `a connection whose index cannot be reset forgets the tokens it stored` ``: `onCompleteAuthorization = { connected = true; ACCOUNT }`,
   `FakeSyncStateLocalSource(index = """{"accountId":"someone-else"}""", onSaveIndex = { throw LibraryStorageException("Full") })`.
   `assertFalse(repository.connect(...))`; `syncState.value == ConnectionFailed(DROPBOX, STORAGE)`;
   `provider.hasForgottenCredentials`.
3. Keep the existing connect tests passing (none of them reaches `completeAuthorization`).

## Verification

1. Tests above; root unit test command.
2. By hand on desktop with a slow network (macOS Network Link Conditioner, "Very Bad Network"): Connect Dropbox,
   approve in the browser, and press **Cancel** in Settings while the app is still "Connecting". Quit and start again.
   **Before:** connected, and a sync run starts. **After:** Connect Dropbox is offered, `preferences/sync-credentials.json`
   is gone (desktop) and no request to Dropbox is made at start.

## Docs

- `data/repository/implementation/CLAUDE.md` (the `connect` / `restore` paragraph, HEAD `:171-178`): after "`connect`
  never throws anything but a cancellation — every way out of it leaves `Connecting`," add "and every way out of it
  that does not end connected forgets whatever the provider stored during the token exchange
  (`forgetStoredCredentials`, no request), so an authorization given up after the tokens arrived is not a connection
  the next launch restores and syncs."

## Files touched

- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncProvider.kt`
- `data/repository/implementation/CLAUDE.md`

## Depends on

- Plan **19** edits `SyncRepositoryImpl.kt` too (`restoreConnection`, `disconnect`, `runSynchronization`) and the same
  fake; land 18 first, 19 rebases.
- Lane C's plan 28 (`ios-forget-credentials-failure`) is about `forgetStoredConnection` on iOS; if it changes that
  function's error handling, keep `forgetCredentialsOf` consistent with it.
- `FakeSyncProvider.kt` and the repository `CLAUDE.md` had uncommitted work at review time — rebase onto it.
