# Replace the hand-written "rethrow the cancellation, log the rest, fall back" blocks of the sync code with one `recovering` helper

**Kind:** architecture  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `data/repository/implementation/src/commonMain/.../data/repository/implementation/` — new `base/Recovering.kt` (internal); `SyncRepositoryImpl.kt` (`restoreConnection`, `hasStoredCredentials`, `disconnect`, `rememberDemoLibraryFiles`, `forgetStoredConnectionNow`, `quietly`, `synchronizePreferences`, `refreshAccount`, `discardPendingAuthorization`, `forgetCredentialsOf`, `loadIndex`, `loadIndexOrNull`, `saveIndexQuietly`); `sync/SyncEngine.kt` (`readLocalState`, `discardCopy`, `withoutForeignEntries`); `LibraryDeletion.kt` (`deleteEach`); new test `base/RecoveringTest.kt`; `data/repository/implementation/CLAUDE.md` (one line)
**Depends on:** none (land before 22 and 24, which move the call sites; if those land first, apply it to wherever the blocks then live)

## Problem

`SyncRepositoryImpl` spells out the same three-armed `try` 18 times (`grep -c "catch (exception: CancellationException)"`
= 18 at 2940b0e0a), `SyncEngine` 4 more times, and every copy differs only in its log line and its fallback value:

```kotlin
val isForgettingOwed = try {
    syncStateLocalSource.isForgettingCredentialsOwed()
} catch (exception: CancellationException) {
    throw exception
} catch (exception: Exception) {
    println("Could not tell whether the sync credentials are to be forgotten: ${exception::class.simpleName}")
    false
}
```

```kotlin
private suspend fun loadIndexOrNull() = try {
    loadIndex()
} catch (exception: CancellationException) {
    throw exception
} catch (exception: Exception) {
    println("Could not read the sync index: ${exception.message}")
    null
}
```

The rule they all encode — *a cancellation is never a failure; any other `Exception` is logged and replaced; a
`Throwable` that is not an `Exception` is not caught* — is the important part, and it is the part a reader has to
re-verify 22 times. A future copy that puts `catch (exception: Exception)` first (which catches the cancellation) looks
the same at a glance. `SyncRepositoryImpl` already has a half-step towards a helper (`quietly`, for `Unit` blocks).

Two variations must survive: some blocks log only `exception::class.simpleName` on purpose, since the message may
quote a token or the credentials document (`discardPendingAuthorization`, `quietly`, the forgetting note); and some
blocks have an extra arm before the generic one (`catch (exception: LibraryStorageException)` in `restoreConnection` and
`runSynchronization`, `catch (exception: SyncAuthorizationException) { throw exception }` in `synchronizePreferences`).

## Fix

1. Add `base/Recovering.kt`:

   ```kotlin
   /**
    * Runs [block] and answers its value, or - for any [Exception] but a cancellation - logs [describe]'s line and
    * answers [fallback]'s. A cancellation is rethrown, never logged; a Throwable that is not an Exception is not caught.
    */
   internal inline fun <T> recovering(
       describe: (Exception) -> String,
       fallback: (Exception) -> T,
       block: () -> T,
   ): T = try {
       block()
   } catch (exception: CancellationException) {
       throw exception
   } catch (exception: Exception) {
       println(describe(exception))
       fallback(exception)
   }
   ```

   `inline` so the lambdas may suspend at a suspending call site (every caller is a `suspend fun`). Keep `println` as
   the sink (plan 34 may swap it later). Add `RecoveringTest` (step's own commit).
2. Rewrite the blocks whose only arms are cancellation + `Exception`, keeping each log line **byte for byte**
   (including which ones use `exception::class.simpleName` rather than `exception.message`). Example:

   ```kotlin
   private suspend fun loadIndexOrNull() = recovering(
       describe = { "Could not read the sync index: ${it.message}" },
       fallback = { null },
   ) { loadIndex() }
   ```

   `quietly(action, block)` becomes a one-line wrapper over `recovering` (or its callers call `recovering` directly).
   `forgetStoredConnectionNow`'s loop sets `haveCredentialsGone = false` in the fallback lambda.
3. Leave alone, with a one-line comment if useful: blocks with an extra typed arm (`LibraryStorageException`,
   `SyncAuthorizationException`, `SyncNetworkException` in `resolveWith`), `connect`'s and `completePendingAuthorization`'s
   blocks (their cancellation arm does clean-up work before rethrowing), `runSynchronization`'s
   (its `Throwable` arm), `refreshChangedFiles` (puts keys back on cancellation) and `runOperation`/`deleteRemotely`
   (`endsTheRun`). Those are real logic, not boilerplate.
4. Optionally apply it to `LibraryDeletion.deleteEach` and the other repositories' identical blocks
   (`CoverArtRepositoryImpl`, `SongContentRepositoryImpl`) in a separate commit.

## Tests

- New `RecoveringTest` (commonTest, `runTest`): the block's value is returned; an `IllegalStateException` gives the
  fallback and calls `describe` once; a `CancellationException` is rethrown and calls neither lambda; an `Error`
  (e.g. `AssertionError`) propagates; a suspending block works.
- Guards: `SyncRepositoryImplTest` (forgetting note, unreadable index, clean-up failures) and `SyncEngineTest`
  (unreadable file, foreign entries), unchanged.

## Manual check

none — covered by tests
