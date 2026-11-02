# Run the storage contract tests on the iOS simulator

**Kind:** testing · **Severity:** medium · **Platforms:** iOS (desktop unchanged) · **Priority:** P2 · **Effort:** M
**Files:** `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorageTest.kt`,
new `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorageContractTest.kt`,
new `…/src/desktopTest/…/storage/file/JvmFileStorageContractTest.kt`, new `…/src/iosTest/…/storage/file/IosFileStorageContractTest.kt`,
`data/source/local/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/IosFileStorage.kt`,
new `…/src/iosMain/…/storage/file/FoundationFileStorage.kt`, `.github/workflows/tests.yml` (the `compile-ios` job),
`data/source/local/implementation/CLAUDE.md`, root `CLAUDE.md` (Tests section)
(`…` = `data/source/local/implementation`, packages as in the first path)

**Challenged:** amended — decision D2 taken (only this module's iOS tests run in CI, no other module's); the CI step
names what to do if the runner lacks Kotlin's default simulator; `iosArm64Test` is only compiled, never run.

## Problem

`FileStorage` is the only per-platform part of the library's storage, and its contract carries the sync's safety:
"a read returns null **only** for a file that is not there… sync plans a missing file as a deletion that reaches every
device" (`data/source/local/implementation/CLAUDE.md`). `JvmFileStorageTest` (33 tests) checks it on the desktop only.
`IosFileStorage` (231 lines: `dataWithContentsOfFile` with its `NSError`, the `fileExistsAtPath` re-check, listing with
prefetched keys, atomic writes, the case-only move) runs in no test at all; CI only links it
(`./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64` in `compile-ios`). Every library already has the
`iosSimulatorArm64` target (`gradle/build-logic`), so `:data:source:local:implementation:iosSimulatorArm64Test` exists
and runs `commonTest` on a simulator — but `commonTest` holds no storage test.

Out of scope: `OpfsFileStorage` (needs a browser runner), and the secret stores (a bare simulator test binary has no
Keychain entitlement; the Android Keystore is not host-testable).

## Fix

1. **Make the iOS root injectable** without a Koin default parameter (code-style), as the desktop does
   (`DesktopFileStorage : FileStorage by JvmFileStorage(desktopDataDirectory())`): move `IosFileStorage`'s body into
   `internal class FoundationFileStorage(logger: Logger, rootPath: (StorageDirectory) -> String) : FileStorage`, and keep
   `@Single internal class IosFileStorage(logger: Logger) : FileStorage by FoundationFileStorage(logger, ::appRootPath)`
   with today's `rootPath` (Application Support for `PREFERENCES` and `COVERS`, Documents otherwise) as the top-level
   `appRootPath`. Mind that `FileStorage` has default method bodies (`readTexts`, `listForScan`, `readScan`): Kotlin's
   `by` delegates those to the delegate too, which is what the desktop already relies on. No behaviour change.
2. **Contract tests in `commonTest`**: `abstract class FileStorageContractTest` with `abstract fun newStorage(): FileStorage`
   (a fresh, empty root per test) and `open fun tearDown()`; the tests run with `runTest` (coroutines-test is on every
   `commonTest`, `runBlocking` is not in common). Move into it every `JvmFileStorageTest` case that goes through the
   `FileStorage` API alone (writes/lists/reads back, one file's info, directories apart, overwrite, concurrent writes of
   one file, bytes round trip, BOM stripped, Windows-1252 fallback, delete and missing delete, null for a missing file,
   a directory deleted under it, a directory under a file's name read as missing, scan listing sorted, scan reads,
   rename only changing case, a file moved to its own name in another case and Unicode form, names that are paths
   refused). Cases that set up through `java.io.File`, `isWindows`, `retryingWhileDenied`, or permissions stay in
   `JvmFileStorageTest`. A case needing a raw file the API cannot make (a directory under a file's name, a directory
   deleted under it) gets an `abstract` hook (`deleteDirectoryBehindItsBack(directory)`, …) rather than staying JVM-only.
3. **Subclasses**: `JvmFileStorageContractTest` (desktopTest) over `JvmFileStorage(Files.createTempDirectory(…))`;
   `IosFileStorageContractTest` (iosTest, run by `iosSimulatorArm64Test`; `iosArm64` only compiles it, a device test binary is never run) over
   `FoundationFileStorage(logger, root)` with a per-test directory under `NSTemporaryDirectory()`, removed in
   `tearDown`. The Android host test and wasm compile `commonTest` too; an abstract class needs no `actual` there.
   Kotlin/Native runs `@Test` functions inherited from an abstract class; check it in the first run (`--info` lists the
   tests) before writing them all.
4. **CI**: in `tests.yml`'s `compile-ios` job, add a step after "Compile the iOS framework":
   `./gradlew :data:source:local:implementation:iosSimulatorArm64Test` ("Run the storage tests on the iOS
   simulator"). It adds to the link step rather than replacing it (the link builds `:app:ios`'s framework and every
   module's `iosMain`; the test task builds only this module). Not in the pre-commit hook: a Kotlin/Native test link
   takes minutes. Only this module (decided): no other module's iOS tests are added. If the task cannot find the
   simulator Kotlin boots by default on the `macos` runner, name an installed one in this module's build file
   (`kotlin.iosSimulatorArm64().testRuns["test"].deviceId`), not in the workflow.
5. Docs: one line in the `FileStorage` section of `data/source/local/implementation/CLAUDE.md` (the contract is
   `FileStorageContractTest`, run on desktop and the iOS simulator); the root `CLAUDE.md` Tests section names the iOS
   run beside `./gradlew desktopTest`.

## Tests

The moved cases themselves. A failure on iOS is a real defect of `IosFileStorage`: fix it in its own commit before the
test lands (skill §2), or stop and ask the owner; never skip a case on iOS.
