# Route the data and domain layers' println calls through an injected Logger, so that "logged, not thrown" paths can be asserted

**Challenged:** amended — said how `SyncEngine` (66 direct constructor calls, no builder) gets the logger without a Koin default-value trap, and that plan 21's scope handler and plan 22's collaborators take it too.

**Kind:** testability  ·  **Severity:** low  ·  **Effort:** M  ·  **Risk:** low  ·  **Platforms:** all
**Files:** new `data/model/src/commonMain/.../data/model/domain/Logger.kt`; one `@Single` provider (see Fix); every class with a `println(` in `data/repository/implementation` (39 calls: `SyncRepositoryImpl`, `sync/SyncEngine`, `sync/SyncedPreferencesSync`, `CoverArtRepositoryImpl`, `SongContentRepositoryImpl`, `LibraryDeletion`, `base/BaseLocalDataRepository`, …), `data/source/local/implementation` (21), `data/source/remote/implementation` (7), `domain/implementation` (4) — 71 at 2940b0e0a (`grep -rn 'println(' <module>/src/*Main`); their tests' builders; `base/Recovering.kt` from plan 20; new `RecordingLogger` test helper per test module; CLAUDE.md of each touched module (one line)
**Depends on:** 20 (the helper is the first place to take the logger); best after 22/24 so the constructors are touched once

## Problem

Many behaviours in the data layer are specified as "a failure here is logged and nothing more": the periodic index
write (`saveIndexQuietly`), the clean-ups after a refused authorization (`discardPendingAuthorization`,
`forgetCredentialsOf`), an unreadable library file in a run (`readLocalState`), a copy that cannot be taken back
(`discardCopy`), the scope's `CoroutineExceptionHandler` that must "never be what closes" the app. All of them write
with `println`, so a test can check that nothing was thrown but not that the failure was *noticed* — and some log lines
are written carefully to **not** include `exception.message` because it may quote a token
(`"Could not clear the pending authorization: ${exception::class.simpleName}"`), a promise nothing checks.

Tests also cannot silence these lines: `SyncEngineTest` and `SyncRepositoryImplTest` print dozens of expected failures
into the Gradle output, which hides the one that matters when a test fails.

## Fix

1. In `:data:model` (seen by every module):

   ```kotlin
   /** Where the data and domain layers say what went wrong and was not worth an exception. */
   fun interface Logger {
       fun log(message: String)

       companion object {
           /** Standard output, which is what the desktop app's campfire.log and the platforms' consoles collect. */
           val Standard = Logger { println(it) }
       }
   }
   ```
2. Provide it once: `@Single internal fun logger(): Logger = Logger.Standard` in `DataRepositoryModule` (any module's
   object works, since `:app:di` checks the whole graph; pick one and say so in its CLAUDE.md). Do **not** give
   constructor parameters a default value unless the Koin compiler plugin is confirmed to inject over defaults —
   otherwise production would silently use the default.
3. Module by module, one commit each (repository, sync, local source, remote source, domain): add `logger: Logger` to
   the constructor of each `@Single`/`@Factory` that prints, replace `println(x)` with `logger.log(x)` keeping every
   string byte for byte, and pass `Logger.Standard` (or a `RecordingLogger`) in the tests' builders. Top-level functions
   that print (`deleteEach`, `recovering`) take a `logger` parameter. Objects without a constructor
   (`PdfTextExtractor`, `ZipReader`) keep `println` or return the message to their caller — list them in the commit.
   Classes that are not Koin-constructed need their own route. `SyncEngine` has no builder in `SyncEngineTest` — its 66
   `SyncEngine(…)` calls are direct — so give its constructor `logger: Logger = Logger.Standard` (a default is safe
   there, because after plan 22 it is built by the `@Single fun syncEngine(…)` in `DataRepositoryModule`, which must
   then take the injected `Logger` and pass it explicitly; never annotate the class). `SyncedPreferencesSync` and the
   plan-22 collaborators are `@Single` classes: no default, tests pass a logger through `FakeSyncCollaborators.kt`'s
   builder. The `CoroutineExceptionHandler` line in plan 21's `RepositoryEnvironment.scopeFor` takes the logger as a
   constructor argument of the environment (its `@Single` function receives it).
4. `:presentation`'s 31 `println`s are out of scope (UI shell code, not tested by this repo's rules).

## Tests

- A `RecordingLogger` (`val lines = mutableListOf<String>()`) in each test module.
- New assertions in existing tests rather than new tests: `a periodic index write that fails does not end the run`
  logs `"Could not write the sync index: Full"`; `a closed browser is not a failure even when the clean up is` logs a
  line containing `LibraryStorageException` and **not** the exception's message; `SyncEngineTest`'s unreadable-file test
  logs the file's path; `deleteEach` logs one line per failed file.
- Guards: every existing suite in the four modules.

## Manual check

Desktop: trigger one logged failure (e.g. import an encrypted PDF) and see its line in `campfire.log` as before.
