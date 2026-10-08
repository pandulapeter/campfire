# Make the three run-ending sync exceptions subclasses of one sealed SyncRunEndingException carrying its SyncFailureReason

**Kind:** architecture  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `data/source/remote/api/src/commonMain/.../remote/api/SyncProvider.kt` (`SyncAuthorizationException`, `SyncNetworkException`, `SyncRemoteStorageFullException`); `data/repository/implementation/.../sync/SyncEngine.kt` (`endsTheRun`); `data/repository/implementation/.../SyncRepositoryImpl.kt` (`toFailureReason`); `data/source/remote/implementation/.../dropbox/DropboxSyncProvider.kt` (`transport`); new test in `data/source/remote/api/src/commonTest/.../SyncRunEndingExceptionTest.kt` (or extend an existing remote-api test); `data/source/remote/api/CLAUDE.md`, `data/repository/implementation/CLAUDE.md` (the "only the three failures that make every further call pointless" sentence)
**Depends on:** none (after 22, `toFailureReason` lives in `SyncRunner`/`SyncConnectionManager`; after the Dropbox transport split of the concurrent lane, `transport` is in its own file — find it by name)

## Problem

"Which exceptions end a run" is enumerated by hand in three places in three modules, and each list has to be kept in
step when a fourth kind appears:

```kotlin
// SyncEngine
private val Exception.endsTheRun
    get() = this is CancellationException ||
        this is SyncAuthorizationException ||
        this is SyncNetworkException ||
        this is SyncRemoteStorageFullException
```

```kotlin
// SyncRepositoryImpl
private fun Throwable.toFailureReason() = when (this) {
    is SyncAuthorizationException -> SyncFailureReason.AUTHORIZATION
    is SyncNetworkException -> SyncFailureReason.NETWORK
    is SyncRemoteStorageFullException -> SyncFailureReason.REMOTE_STORAGE_FULL
    is LibraryStorageException -> SyncFailureReason.STORAGE
    else -> SyncFailureReason.UNKNOWN
}
```

```kotlin
// DropboxSyncProvider.transport
} catch (exception: SyncAuthorizationException) {
    throw exception
} catch (exception: SyncNetworkException) {
    throw exception
} catch (exception: DropboxApiException) {
    throw exception
} catch (throwable: Throwable) {
    throw SyncNetworkException(…)
}
```

The three classes are three unrelated `Exception` subclasses, so the compiler cannot say "you forgot one". (The
`transport` list does not include `SyncRemoteStorageFullException` — correct today, since `ensureSuccessful`, the only
thrower, is never called inside a `transport { }` block, but invisible.)

## Fix

1. In `SyncProvider.kt`:

   ```kotlin
   /** A failure that makes every further call of a run pointless, and so ends it; [reason] is what the user is told. */
   sealed class SyncRunEndingException(message: String, cause: Throwable?, val reason: SyncFailureReason) : Exception(message, cause)

   class SyncAuthorizationException(message: String, cause: Throwable? = null) : SyncRunEndingException(message, cause, SyncFailureReason.AUTHORIZATION)
   class SyncNetworkException(message: String, cause: Throwable? = null) : SyncRunEndingException(message, cause, SyncFailureReason.NETWORK)
   class SyncRemoteStorageFullException(message: String, cause: Throwable? = null) : SyncRunEndingException(message, cause, SyncFailureReason.REMOTE_STORAGE_FULL)
   ```

   (`SyncFailureReason` is in `:data:model`, which this module already `api()`s.) Constructors and class names are
   unchanged, so every `throw` and every specific `catch (exception: SyncAuthorizationException)` keeps compiling and
   meaning the same.
2. `endsTheRun` becomes `this is CancellationException || this is SyncRunEndingException`.
3. `toFailureReason` becomes
   `when (this) { is SyncRunEndingException -> reason; is LibraryStorageException -> SyncFailureReason.STORAGE; else -> SyncFailureReason.UNKNOWN }`.
4. In `transport`, replace the two pass-through arms with one `catch (exception: SyncRunEndingException) { throw exception }`.
   This now also passes a `SyncRemoteStorageFullException` through instead of wrapping it as a network failure — no
   change today (nothing inside a `transport` block throws it), and the right answer if something ever does. Say so in
   the commit message.
5. Leave the specific `SyncAuthorizationException` catches (`runSynchronization`, `synchronizePreferences`,
   `resolveWith`'s `SyncNetworkException` arm, `DropboxSyncProvider.loadAccount`) alone: they single out one kind on
   purpose.

One commit.

## Tests

- New: `SyncRunEndingException` reasons — each of the three maps to its `SyncFailureReason` (a three-line test, so a
  fourth subclass added without a reason fails to compile and one added with the wrong one is caught).
- Guards: `SyncEngineTest` (a run ended by authorization/network/full), `SyncRepositoryImplTest`
  (`a full remote folder is reported as that`, `a run the service refuses reports the connection as failed…`),
  `DropboxRequestTest` (transport wrapping).

## Manual check

none — covered by tests
