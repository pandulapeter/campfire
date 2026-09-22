# 03 — Skip remote names Windows cannot store, and say so once

## What the user sees

A Windows user whose library also lives on an iPhone (or whose cloud folder somebody has dropped a file into by
hand) has a song called `Who Are You?.cho`, or `Rock: The Musical.cho`, or `4*4.cho`. Every one of those names is
legal on iOS, macOS, Linux, Android and OPFS. None of them is legal on Windows.

On the Windows PC, every sync run tries to download that file, fails on it, and reports "One file could not be
synced, and Campfire will try again next time". It tries again next time, and the time after that, forever. Worse
than the noise: a run with any failed file deliberately does not move `lastSyncedAt`
(`SyncRepositoryImpl.kt:377-383`), so Settings goes on saying the library was last synced successfully on the day
before that file appeared — for as long as the file exists. The one PC in the household looks permanently out of
date, and the user has no way of telling which file is the cause from the message, because the message names it
but in the same breath promises to try again, which it never successfully will.

## Cause

`requireValidFileName`, the one check a name passes before it becomes a path, rejects path separators and nothing
else. `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.kt:92-94`:

```kotlin
internal fun requireValidFileName(name: String) = require(
    name.isNotEmpty() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\')
) { "Invalid file name: \"$name\"." }
```

`JvmFileStorage.writeAtomically` then converts the name to a `Path`
(`.../desktopMain/.../JvmFileStorage.kt:91-92`):

```kotlin
    private fun writeAtomically(directory: StorageDirectory, name: String, write: (File) -> Unit) {
        val target = file(directory, name).toPath()
```

On Windows, `File.toPath()` throws `java.nio.file.InvalidPathException` for any of `? : * " < > |`.
`InvalidPathException` extends `IllegalArgumentException`, which extends `RuntimeException` — it is **not** an
`IOException`, so the wrapper it is thrown inside does not touch it
(`JvmFileStorage.kt:111-116`):

```kotlin
    /** A file that is there but will not be read or written is a failure of the storage, not of whoever asked. */
    private inline fun <T> failingAsStorage(name: String, operation: () -> T): T = try {
        operation()
    } catch (exception: IOException) {
        throw LibraryStorageException("Could not access \"$name\".", exception)
    }
```

It surfaces to `SyncEngine.runOperation`, whose last clause catches every `Exception`
(`SyncEngine.kt:325-329`):

```kotlin
    } catch (exception: Exception) {
        // The index is left alone, so the next run sees this file as it was and tries again.
        println("Could not sync \"${operation.key.path}\": ${exception.message}")
        OperationOutcome(summary = SyncSummary(failed = listOf(operation.key.name)))
    }
```

so the run does not crash — it fails that one file, leaves the index alone, and comes back to it on the next run
with exactly the same result. And `SyncRepositoryImpl.kt:377-383` holds `lastSyncedAt` back for it:

```kotlin
                        val syncedAt = if (result.summary.failed.isEmpty()) {
                            Clock.System.now().toEpochMilliseconds()
                        } else {
                            result.index.lastSyncedAt
                        }
```

The read path is already safe by accident: `readBytes` checks `it.isFile` first, which is false for a name
Windows cannot open, so it returns null before `toPath()` is reached. It is the *write* that throws, which is
exactly the operation that would have put the file on the PC.

### What Windows already does here, and what it is consistent with

`JvmFileStorage` already knows about one class of Windows-illegal name — the reserved device names — and it
**maps** them (`JvmFileStorage.kt:137-141, 149-150`):

```kotlin
    private fun String.toStoredName() = if (escapesDeviceNames && trimStart(DEVICE_NAME_ESCAPE).isDeviceName()) DEVICE_NAME_ESCAPE + this else this

    private fun String.toLibraryName() = if (escapesDeviceNames && startsWith(DEVICE_NAME_ESCAPE) && trimStart(DEVICE_NAME_ESCAPE).isDeviceName()) drop(1) else this
```

with `DEVICE_NAME_ESCAPE = '_'` and the escape itself escaped, so `con.cho` is stored as `_con.cho` and `_con.cho`
as `__con.cho`, and the library name the rest of the app sees is the original either way. That is the precedent
this plan has to be consistent with, and it is tested
(`JvmFileStorageTest.kt:126-136`, `stores a Windows device name under an escaped one and reports the name it was
given`).

## The change

### The decision: skip, do not map

**Recommended: leave such a file out of the plan on both sides and name it once in the run's summary, the way a
file too large to be a song is already left out.** Reject the mapping approach. The reasons, in order of weight:

1. **A mapping needs an escape character that cannot appear in a name, and there is none.** The device-name escape
   works because `_` is a *prefix* and the rule is anchored: only a name that would otherwise be a device name is
   ever escaped, so the ambiguity is confined to about a dozen names and is resolved by escaping the escape.
   Escaping seven characters *anywhere in a name* means either picking substitute characters the user might have
   typed (`？`, `：` — a Japanese title really can contain those) or moving into a private-use range the way WSL
   does. Either way the escaping has to be idempotent and self-inverse for every possible name, including names
   that already contain the substitutes, on a value that **is the song's identity**. The root `CLAUDE.md` is
   unambiguous: "The file name is a song's (and a setlist's) identity", and "a setlist points at its songs by file
   name". Getting this wrong does not produce a cosmetic bug; it produces two songs, or a setlist entry pointing
   at nothing.
2. **A mapped name would go back up.** The engine sees whatever `list()` reports. If `toLibraryName` reverses the
   mapping perfectly, nothing goes up wrong — but if it does not, on any name, that PC uploads a second copy under
   the mangled name, and every other device downloads it. The failure mode of skipping is a file that does not
   arrive on one PC. The failure mode of a mapping bug is a duplicated library.
3. **The skip already has a shape in this codebase.** `SyncEngine.readLocalStates` and the block around
   `SyncEngine.kt:129-161` already do exactly this for a file too large to read: it is left out of the plan on both
   sides, *its index entry is dropped too* so it is not read as a deletion, and it is named among the run's
   failures. The new case is the same sentence with a different reason.
4. It is honest. The two sides genuinely are not in step on that PC, and `lastSyncedAt` genuinely should not move.
   What changes is that the user is told *which* file and *why*, and the remedy — rename it on the device that can
   hold it, which the app offers as **Update file name** — is something they can act on. Today they are told a file
   failed and will be retried, which is not true.

### 1. Ask the storage whether it can hold a name

`FileStorage` (commonMain) gains a predicate with a default, next to `keepOutOfDeviceBackup`, which is the same
pattern — a question only one platform answers:

```kotlin
    /**
     * Whether a file called [name] can exist in this storage at all. Only Windows answers no: `? : * " < > |` are
     * legal in a name on iOS, macOS, Linux, Android and OPFS, and a library assembled on any of those can hold one.
     * Asked rather than attempted because the attempt is an `InvalidPathException` from deep inside the JVM, which
     * is not an `IOException` and so is not a storage failure the caller could tell from any other.
     */
    fun canHoldFileName(name: String): Boolean = true
```

`JvmFileStorage` overrides it:

```kotlin
    override fun canHoldFileName(name: String) = !isWindows || name.none { it in WINDOWS_RESERVED_CHARACTERS }
```

with

```kotlin
        /** The characters Windows refuses in a file name; `/` and `\` are already refused everywhere by `requireValidFileName`. */
        const val WINDOWS_RESERVED_CHARACTERS = "?:*\"<>|"
```

Two details worth getting right while you are there, both of which `toPath()` also refuses or silently mangles on
Windows and which a name from another platform can carry: a name ending in a space or a dot (Windows strips them),
and a control character below ` `. Include them in the predicate:

```kotlin
    override fun canHoldFileName(name: String) = !isWindows || (
        name.none { it in WINDOWS_RESERVED_CHARACTERS || it < ' ' } && !name.endsWith(' ') && !name.endsWith('.')
    )
```

`isWindows` is the renamed `escapesDeviceNames` constructor parameter from plan 02; if plan 02 has not landed,
read `escapesDeviceNames` and rename it here instead. Note that device names are **not** part of this predicate:
they are mapped and therefore *can* be held.

### 2. Carry it to the engine

`LibraryFileLocalSource` (`:data:source:local:api`) gains the same predicate, forwarded by
`LibraryFileLocalSourceImpl`:

```kotlin
    /** Whether this device's file system can hold a library file called [name] at all, see `FileStorage`. */
    fun canHoldFileName(kind: LibraryFileKind, name: String): Boolean
```

(take the `kind` even though no platform uses it — the two directories could differ on a future platform, and the
rest of the interface is keyed the same way.)

`FakeLibraryFileLocalSource` in
`data/repository/implementation/src/commonTest/.../sync/FakeLibraryFileLocalSource.kt` gains an overridable
implementation, defaulting to true, so the tests can make a fake Windows.

### 3. Leave such a remote file out of the plan

In `SyncEngine.synchronize`, immediately after the remote listing is built and before
`foldRemoteNamesOntoLocal` — so the unstorable name is never folded onto a local one — partition the listed remote
files:

```kotlin
            val (storable, unstorable) = listed
                .filter { it.kind.matches(it.name) }
                .partition { libraryFileLocalSource.canHoldFileName(it.kind, it.name) }
            // Named once per run rather than tried again every time: this device's file system cannot hold the name,
            // which no number of runs will change. Left out of the plan on both sides, index entry included, exactly
            // like a file too large to read - with the entry kept, the planner would read it as a file gone from
            // here and delete the remote copy.
            if (pass == 0) summary = summary.plus(SyncSummary(failed = unstorable.map { it.name }))
            val unstorableKeys = unstorable.mapTo(mutableSetOf()) { SyncKey(kind = it.kind, name = it.name) }
```

then build `remote` from `storable` instead of the inline `listed.filter { … }` at lines 142-149, and add
`index = index - unstorableKeys` next to the existing `index = index - tooLarge` at line 161, with the comment
extended to cover both reasons.

The local side needs nothing: such a file cannot exist on this device, so it is never in the local listing.

### 4. Let the failure message say what it means

`settings_sync_files_failed` currently promises a retry:

```xml
    <plurals name="settings_sync_files_failed">
        <item quantity="one">One file could not be synced, and Campfire will try again next time: %2$s</item>
        <item quantity="other">%1$d files could not be synced, and Campfire will try again next time. Among them: %2$s</item>
    </plurals>
```

That promise is now false for this class of file, and it was already false for a file too large to read. Rather
than a second summary field and a second message — which would mean threading a reason through `SyncSummary` for
one sentence — soften the existing one so that it is true of every file it names:

```xml
    <plurals name="settings_sync_files_failed">
        <item quantity="one">One file could not be synced: %2$s</item>
        <item quantity="other">%1$d files could not be synced. Among them: %2$s</item>
    </plurals>
```

and the Hungarian to match. If you would rather keep the retry promise for the ordinary case, the alternative is a
`SyncSummary.rejected: List<String>` beside `failed` with its own plural — more code, a truer message, and it also
lets those files stop holding `lastSyncedAt` back if that is ever wanted. Recommend the simple version now and note
the alternative in the commit message. These are read with
`com.pandulapeter.campfire.presentation.localization.pluralTextResource` already (the names are text somebody else
wrote), which `SyncSettings.kt` gets right — do not change that to `pluralStringResource`.

## Tests

`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`,
with `FakeLibraryFileLocalSource(canHoldFileName = { name -> '?' !in name })`:

1. `a remote file this device cannot store is left alone and named once` — remote holds `ok.cho` and `who?.cho`,
   local and index empty. Assert `ok.cho` was downloaded, `local.files` does not hold `who?.cho`,
   `provider.files` still holds it, `summary.failed == listOf("who?.cho")` and the returned index has no entry for
   it.
2. `a remote file this device cannot store is not taken for a deletion` — the same file **with** an index entry
   (which is what an index copied from another device, or written before the app knew the rule, looks like).
   Assert no `DeleteRemote` happened: `provider.files` still holds it, and the index that comes back has dropped
   its entry.
3. `a name this device cannot store is not folded onto a local one` — local `who.cho`, remote `who?.cho`. Assert
   the remote file is not matched onto the local one and nothing is uploaded over it.
4. `a device that can store every name reports no failures` — the same library with the default predicate; assert
   `summary.failed` is empty. This is the regression guard for every other platform.

`data/source/local/implementation/src/desktopTest/kotlin/.../JvmFileStorageTest.kt`:

5. `refuses to hold a name Windows cannot store` — `JvmFileStorage(root, isWindows = true).canHoldFileName` is
   false for `who?.cho`, `a:b.cho`, `a*b.cho`, `a".cho`, `a<b.cho`, `a>b.cho`, `a|b.cho`, `a .cho`, `a.cho.` and a
   name with `\u0001`, and true for `con.cho` (which is mapped, not refused) and `катюша.cho`.
6. `holds every name on a file system that is not Windows` — `JvmFileStorage(root, isWindows = false)` answers true
   for all of the above, and actually writes and reads back `who?.cho` (this runs on macOS CI, where the name is
   legal, so it is a real round trip).

## Verification

```
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
./gradlew :app:desktop:run
./gradlew :app:android:assembleDebug
./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64
./gradlew :app:web:wasmJsBrowserDistribution
```

Manual, **needs a Windows PC and a Dropbox account**, which is the only place the bug is visible:

1. On an iPhone or a Mac, create a song whose title makes the file name `who_are_you.cho` — then rename the file
   by hand in the library folder to `Who Are You?.cho` (the app's own normalization never produces a `?`, so the
   name has to be put there from outside, which is exactly how it happens in life).
2. Sync it up. Confirm Dropbox holds `Who Are You?.cho`.
3. Sync on the Windows PC. Before the change: a failure every run and a `lastSyncedAt` that never moves. After it:
   the file is named once as one that could not be synced, every other file is in step, and the message no longer
   promises a retry.
4. Rename the file on the iPhone to something Windows can hold, sync, and confirm the Windows PC picks it up and
   stops complaining.

Note while testing: **Dropbox itself refuses `< > : " | ? *` in a file name**, so with the current single provider
the remote folder cannot normally hold one — the file has to arrive there some other way, or the check is
exercised against a future provider. That does not make the plan unnecessary: the same characters are legal in the
*local* library on four of the five platforms, the check is the thing that keeps a Windows PC from failing on
them forever, and the upload side fails on them today with a provider error that the run also retries forever.
Consider a follow-up that treats a name the *provider* refuses the same way; it is out of this plan's scope.

## Docs

`data/source/local/implementation/CLAUDE.md` — this sentence lists the Windows behaviour and is now incomplete:

> - The JVM storage removes its own temporary files older than an hour on first touching each directory. On Windows
>   it stores device names such as `con.cho` with a leading underscore and reports the ordinary library name back.

Add that a name Windows cannot hold at all (`? : * " < > |`, a trailing space or dot, a control character) is
refused by `canHoldFileName` rather than mapped, and say why the two are treated differently.

`data/repository/implementation/CLAUDE.md` — this sentence describes the too-large rule and now describes two
rules:

> A local file larger than a run downloads is not read or uploaded either: it is left out of the plan on both sides
> — its index entry too, so that it is not taken for a deletion — and named among the run's failures.

`CLAUDE.md` (root), Sync section — this sentence is the one that promises the engine sees a flat folder of names,
and the new exception belongs next to it:

> What is not a song or a setlist by its extension is invisible to the engine on both sides, so whatever else the
> user keeps in the folder is left alone.

`documentation/sync.md` — the bullet list under "What is synced" says:

> - Only song and setlist files are synced. Anything else you keep in those two folders is left exactly where it
>   is, and so is a file too large to be a song (over 8 MB).

Add: on Windows, a file whose name contains a character Windows does not allow is left where it is too, and named
in the run's summary.

## Files touched

- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.kt`
- `data/source/local/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`
- `data/source/local/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`
- `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/LibraryFileLocalSource.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/LibraryFileLocalSourceImpl.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`
- `presentation/src/commonMain/composeResources/values/strings.xml`
- `presentation/src/commonMain/composeResources/values-hu/strings.xml`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeLibraryFileLocalSource.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorageTest.kt`
- `data/source/local/implementation/CLAUDE.md`, `data/repository/implementation/CLAUDE.md`, `CLAUDE.md`,
  `documentation/sync.md`

## Depends on

Plan 02, loosely: it renames `escapesDeviceNames` to `isWindows`, which this plan reads. Land 02 first. Nothing
else.

## Rules

- Load the `code-style` skill before the first edit.
- The two `JvmFileStorage.kt` copies stay identical.
- `commonMain` stays JVM-free: `canHoldFileName`'s default lives in the interface, the Windows answer in the JVM
  source sets, and no `java.nio` type crosses into common code.
- The changed plural goes into both language files and keeps its arguments.
