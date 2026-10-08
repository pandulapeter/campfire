# Move rememberDemoLibraryFiles out of SyncRepository into a DemoLibraryRepository of its own

**Kind:** architecture  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `data/repository/api/src/commonMain/.../data/repository/api/SyncRepository.kt` (`rememberDemoLibraryFiles`), new `DemoLibraryRepository.kt`; `data/repository/implementation/src/commonMain/.../implementation/SyncRepositoryImpl.kt` (`rememberDemoLibraryFiles`), new `DemoLibraryRepositoryImpl.kt`; `domain/implementation/src/commonMain/.../useCases/SyncUseCaseImpls.kt` (`RememberDemoLibraryFilesUseCaseImpl`, ~line 82); `domain/api/.../useCases/RememberDemoLibraryFilesUseCase.kt` (KDoc reference); tests `SyncRepositoryImplTest.kt` (`rememberDemoLibraryFiles records the content of the files that are there`, `a run takes the folder's version of a remembered demo song`), new `DemoLibraryRepositoryImplTest.kt`, `domain/implementation/src/commonTest/.../DeleteLibraryUseCaseImplTest.kt` (`FakeSyncRepository.rememberDemoLibraryFiles`); `tools/screenshots/src/main/kotlin/.../screenshots/Fakes.kt` (`ConnectedSyncRepository.rememberDemoLibraryFiles`); `data/repository/api/CLAUDE.md`, `data/repository/implementation/CLAUDE.md`, `domain/implementation/CLAUDE.md` if they name it
**Depends on:** 20 (only for the `recovering` form of the body; not required)

## Problem

`SyncRepository` — the state machine of the cloud connection — also owns a job that is not sync:

```kotlin
override suspend fun rememberDemoLibraryFiles(songFileNames: Collection<String>, setlistFileNames: Collection<String>) {
    try {
        val keys = songFileNames.map { SyncKey(LibraryFileKind.SONG, it) } + setlistFileNames.map { SyncKey(LibraryFileKind.SETLIST, it) }
        val hashes = libraryFileLock.withLock {
            keys.mapNotNull { key -> libraryFileLocalSource.readLibraryFile(key.kind, key.name)?.let { key.path to localContentHash(it) } }
        }
        if (hashes.isNotEmpty()) {
            userPreferencesRepository.updateUserPreferences { it.copy(demoLibraryContentHashes = it.demoLibraryContentHashes + hashes) }
        }
    } catch …
}
```

It runs on every fresh installation and every "add the demo songs" from Settings, sync connected or not; it touches no
sync state, no provider and no run lock. It sits in `SyncRepositoryImpl` only because the hash it records is read
later by the engine. The result: every `SyncRepository` fake (the screenshot tool's `ConnectedSyncRepository`,
`DeleteLibraryUseCaseImplTest.FakeSyncRepository`) has to stub it, and the sync split (plan 22) would have to find a
home for it among sync collaborators.

The engine's side does not need to move: its lookup reads the preferences directly —
`userPreferencesRepository.loadUserPreferencesIfNeeded()?.demoLibraryContentHashes?.get(key.path)`.

## Fix

1. Add to `:data:repository:api`:

   ```kotlin
   /** The demo songs and setlist the app plants, and what it remembers about them. */
   interface DemoLibraryRepository {
       /** Moved verbatim from SyncRepository, KDoc included. */
       suspend fun rememberDemoLibraryFiles(songFileNames: Collection<String>, setlistFileNames: Collection<String>)
   }
   ```
2. Add `@Single internal class DemoLibraryRepositoryImpl(libraryFileLocalSource, libraryFileLock, userPreferencesRepository) : DemoLibraryRepository`
   with the body moved unchanged (same lock, same log line `"Could not remember the demo library: …"`). It still uses
   `SyncKey`, `SyncKey.path` and `localContentHash` so the recorded key is exactly what the engine looks up — keep it
   that way, and say so in the KDoc.
3. `RememberDemoLibraryFilesUseCaseImpl` injects `DemoLibraryRepository` instead of `SyncRepository`.
4. Remove the member from `SyncRepository` and `SyncRepositoryImpl`, and the stubs from the two fakes.
5. Move the test `rememberDemoLibraryFiles records the content of the files that are there` to
   `DemoLibraryRepositoryImplTest` (same fakes: `FakeLibraryFileLocalSource`, `FakeUserPreferencesRepository`). In
   `a run takes the folder's version of a remembered demo song`, replace the call with a `DemoLibraryRepositoryImpl`
   built on the same fakes (or seed `demoLibraryContentHashes` in the fake preferences), so the engine-side behaviour
   stays covered.
6. Update the CLAUDE.md lines that say the record is kept by `SyncRepositoryImpl`.

Steps 1–6 are one commit (the interface member and its implementation move together). The Koin compiler plugin checks
the new `@Single` at `:app:di` compile time.

## Tests

- `DemoLibraryRepositoryImplTest`: the moved test; plus `a file that is not there is not recorded` and `a failing read
  records nothing and does not throw`.
- Guards: `a run takes the folder's version of a remembered demo song` (SyncRepositoryImplTest), the engine's demo tests
  in `SyncEngineTest`.

## Manual check

Fresh install (clear app data) on one platform: the demo songs appear; connect Dropbox with a folder that already holds
another version's demo files; after the first run the demo songs are not duplicated as ` (2)` copies.
