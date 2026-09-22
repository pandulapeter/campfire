# 01 — Guard remote deletions the way local ones are guarded

## What the user sees

A user with sync connected loses the whole cloud folder, and every other device empties itself after it.

Concretely, on iOS: the library is in the Documents directory, which the Files app can reach. The user drags
`Campfire/library/songs` somewhere else, or deletes it, or an iCloud Drive move takes it away. Nothing warns them —
the app does not own that folder any more than a text editor owns its documents folder. They open Campfire. The
launch sync runs on its own. `NSFileManager.contentsOfDirectoryAtPath` returns null for a directory that is not
there, and the iOS storage turns that into `emptyList()` rather than a failure; on desktop and Android the JVM
storage calls `mkdirs()` before it lists, so the directory is *recreated empty* and lists empty. Either way the run
sees a library of zero songs and an index that remembers four hundred.

The planner turns every one of those four hundred into `DeleteRemote`. The engine's safety check counts only
`DeleteLocal`, finds zero, and carries the plan out without asking. Four hundred songs are deleted from Dropbox.
Every other device then syncs, sees four hundred files gone from the remote folder — *that* trips the guard, so
each of them stops and asks, which is the one thing that saves the user, and only for as long as they answer
"Keep them and upload" rather than "Delete them here too". A user who has only the one device, or who answers the
wrong way on the phone that still has the library, has lost the library.

The mirror case is milder but the same shape: a phone restored from a backup that carries `library/` but not
`sync-index.json` starts with no index and is safe; one whose library restore failed while the index survived is
exactly the case above.

## Cause

`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt:169-175`,
verified at HEAD `984861e4`:

```kotlin
            val plan = SyncPlanner.plan(local = local, remote = remote, index = index)
            // Which is what most runs find, so nothing below this costs anything on an ordinary launch.
            if (plan.isEmpty()) break
            val deletions = plan.count { it is SyncOperation.DeleteLocal }
            if (deletionPolicy == SyncDeletionPolicy.ASK && index.isNotEmpty() && deletions.isTooManyToDeleteOutOf(index.size)) {
                return Result.DeletionsNeedConfirmation(count = deletions, total = index.size)
            }
```

`deletions` counts `DeleteLocal` and nothing else. `SyncOperation.DeleteRemote` — produced by
`SyncPlanner.kt:137-142` for every key that is in the index and the remote listing but not the local one —
passes the guard untouched and is carried out by `SyncEngine.kt:308-311`:

```kotlin
            is SyncOperation.DeleteRemote -> {
                provider.delete(operation.key.kind, operation.key.name, operation.revision)
                OperationOutcome(removals = setOf(operation.key), summary = SyncSummary(deletedRemotely = 1))
            }
```

The KDoc on the guard (`SyncEngine.kt:586-594`) states the rule in one direction only:

```kotlin
    /**
     * Whether a plan deletes enough of what the last run saw that it is more likely a remote folder that was emptied,
     * renamed or replaced than songs somebody deleted. …
     */
    private fun Int.isTooManyToDeleteOutOf(total: Int) =
        this > 0 && (this == total || (this >= MIN_DELETIONS_TO_ASK && this * 2 > total))
```

The premise that a vanished local directory lists as empty rather than failing is verified in both storages:

- `data/source/local/implementation/src/iosMain/.../FileStorage.ios.kt:65-68`:
  `?: if (fileManager.fileExistsAtPath(directoryPath)) throw IllegalStateException(…) else emptyList<Any?>()`
- `data/source/local/implementation/src/desktopMain/.../JvmFileStorage.kt:123-128`: `directoryFile` calls
  `it.mkdirs()` before `list` ever looks, so `list` sees a fresh empty directory, not a missing one.

## The change

Make the guard symmetric, and give the question a direction so that the two Settings answers can be worded for the
side the files would be deleted from. Four pieces.

### 1. A direction on the outcome, and two more answers

`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/SyncState.kt`:

```kotlin
/** Which side a stopped run would have deleted from, see [SyncOutcome.DeletionsNeedConfirmation]. */
enum class SyncDeletionDirection {

    /** The files are gone from the cloud folder and the run would remove them from this device. */
    LOCAL,

    /** The files are gone from this device and the run would remove them from the cloud folder. */
    REMOTE,
}
```

and `DeletionsNeedConfirmation` gains it:

```kotlin
    data class DeletionsNeedConfirmation(
        val count: Int,
        val total: Int,
        val direction: SyncDeletionDirection,
    ) : SyncOutcome
```

`SyncDeletionPolicy` gains the two mirrored answers. Keep it one flat enum rather than a pair of (answer,
direction): a policy belongs to one run, and a run only ever answers one question.

```kotlin
enum class SyncDeletionPolicy {

    /** Deletes them, unless there are so many that it stops and asks instead. What every run does unless told otherwise. */
    ASK,

    /** Deletes them from this device however many there are: the user has seen the number and said yes. */
    DELETE_LOCALLY,

    /** Keeps them here and treats them as new on this device, so they go back up into the folder. */
    KEEP_AND_UPLOAD,

    /** Deletes them from the cloud folder however many there are. */
    DELETE_REMOTELY,

    /** Keeps them in the folder and treats them as new there, so they come back down onto this device. */
    KEEP_AND_DOWNLOAD,
}
```

**An answer waives one guard, never both.** This is the rule that makes the two questions compose: a run told
"Delete them here too" must still stop if it turns out it would also empty the cloud folder. Express it on the enum
so the engine reads as one line each:

```kotlin
internal val SyncDeletionPolicy.waivesLocalGuard
    get() = this == SyncDeletionPolicy.DELETE_LOCALLY || this == SyncDeletionPolicy.KEEP_AND_UPLOAD

internal val SyncDeletionPolicy.waivesRemoteGuard
    get() = this == SyncDeletionPolicy.DELETE_REMOTELY || this == SyncDeletionPolicy.KEEP_AND_DOWNLOAD
```

(put these next to the enum in `:data:model`, or private in `SyncEngine.kt` — either is fine; `:data:model` is
where the enum's meaning already lives.)

### 2. The engine

`SyncEngine.synchronize`, replacing lines 162-175:

```kotlin
            if (deletionPolicy == SyncDeletionPolicy.KEEP_AND_UPLOAD) {
                // Forgetting that the last run saw these files is what makes them new on this device: a file that is
                // here, is not there and has no index entry is planned as an upload.
                val localKeys = local.mapTo(mutableSetOf()) { it.key }
                val remoteKeys = remote.mapTo(mutableSetOf()) { it.key }
                index = index.filterKeys { it !in localKeys || it in remoteKeys }
            }
            if (deletionPolicy == SyncDeletionPolicy.KEEP_AND_DOWNLOAD) {
                // The same the other way round: a file that is there, is not here and has no index entry is a
                // download, which is how the folder's files come back onto a device that lost its library folder.
                val localKeys = local.mapTo(mutableSetOf()) { it.key }
                val remoteKeys = remote.mapTo(mutableSetOf()) { it.key }
                index = index.filterKeys { it !in remoteKeys || it in localKeys }
            }
            val plan = SyncPlanner.plan(local = local, remote = remote, index = index)
            // Which is what most runs find, so nothing below this costs anything on an ordinary launch.
            if (plan.isEmpty()) break
            val localDeletions = plan.count { it is SyncOperation.DeleteLocal }
            if (!deletionPolicy.waivesLocalGuard && index.isNotEmpty() && localDeletions.isTooManyToDeleteOutOf(index.size)) {
                return Result.DeletionsNeedConfirmation(
                    count = localDeletions,
                    total = index.size,
                    direction = SyncDeletionDirection.LOCAL,
                )
            }
            val remoteDeletions = plan.count { it is SyncOperation.DeleteRemote }
            // A library folder that is gone rather than emptied looks exactly like one emptied on purpose: the JVM
            // storage recreates it and lists nothing, and iOS lists nothing for a directory that is not there. So an
            // empty local listing with an index that is not empty always asks, however small the library - the
            // proportional rule below would let a library of four files go without a word.
            val hasLostEverythingLocally = local.isEmpty() && index.isNotEmpty()
            if (!deletionPolicy.waivesRemoteGuard &&
                index.isNotEmpty() &&
                (hasLostEverythingLocally || remoteDeletions.isTooManyToDeleteOutOf(index.size))
            ) {
                return Result.DeletionsNeedConfirmation(
                    count = remoteDeletions,
                    total = index.size,
                    direction = SyncDeletionDirection.REMOTE,
                )
            }
```

`Result.DeletionsNeedConfirmation` gains the same `direction` field.

Note the ordering: the local guard is checked first, so a run that would empty both sides asks about this device
first — the side the user is looking at, and the one they can still fix. Answering it reruns with
`DELETE_LOCALLY` (or `KEEP_AND_UPLOAD`), which waives only that guard, and the remote question is then put on the
next pass of the next run. That is deliberate and worth a comment: two questions in a row about one folder is
better than one answer that empties both sides.

Rewrite the `isTooManyToDeleteOutOf` KDoc so it no longer says "remote folder" as though only one direction
existed — it is now the shared test for "more of what the last run saw than a person deletes one by one".

### 3. The repository

`SyncRepositoryImpl.kt:358-372` already maps `SyncEngine.Result.DeletionsNeedConfirmation` onto
`SyncOutcome.DeletionsNeedConfirmation`; add `direction = result.direction` to that copy. Nothing else in the
repository changes: the run stops before anything moved, the index is written with `isRunInProgress = false`, and
an earlier pass's files are rescanned exactly as now.

### 4. Settings

`presentation/.../screens/settings/SyncSettings.kt:197-210` shows two rows whenever the outcome is
`DeletionsNeedConfirmation`. Make the pair depend on the direction:

```kotlin
    AnimatedSettingsRow(value = (syncState.lastOutcome as? SyncOutcome.DeletionsNeedConfirmation)?.direction) { direction ->
        Column {
            when (direction) {
                SyncDeletionDirection.LOCAL -> {
                    ActionListItem(
                        title = stringResource(Res.string.settings_sync_delete_locally),
                        icon = painterResource(Res.drawable.ic_delete),
                        onClick = { viewModel.synchronizeLibrary(SyncDeletionPolicy.DELETE_LOCALLY) },
                    )
                    ActionListItem(
                        title = stringResource(Res.string.settings_sync_keep_and_upload),
                        icon = painterResource(Res.drawable.ic_cloud),
                        onClick = { viewModel.synchronizeLibrary(SyncDeletionPolicy.KEEP_AND_UPLOAD) },
                    )
                }

                SyncDeletionDirection.REMOTE -> {
                    ActionListItem(
                        title = stringResource(Res.string.settings_sync_delete_remotely),
                        icon = painterResource(Res.drawable.ic_delete),
                        onClick = { viewModel.synchronizeLibrary(SyncDeletionPolicy.DELETE_REMOTELY) },
                    )
                    ActionListItem(
                        title = stringResource(Res.string.settings_sync_keep_and_download),
                        icon = painterResource(Res.drawable.ic_cloud),
                        onClick = { viewModel.synchronizeLibrary(SyncDeletionPolicy.KEEP_AND_DOWNLOAD) },
                    )
                }
            }
        }
    }
```

Check the `AnimatedSettingsRow(value = …)` overload's signature before using it this way — the file already uses
both the `isVisible` and the `value` forms (`SyncSettings.kt:197` and `:221`); pick whichever keeps the collapse
animation. If only `isVisible` fits, keep it and read the direction inside.

`SettingsScreen.kt:200` derives a boolean from the outcome for something else; it does not need the direction.

The status line, `SyncSettings.kt:248-254`, picks a different plural per direction:

```kotlin
        is SyncOutcome.DeletionsNeedConfirmation -> pluralStringResource(
            when (outcome.direction) {
                SyncDeletionDirection.LOCAL -> Res.plurals.settings_sync_deletions_pending
                SyncDeletionDirection.REMOTE -> Res.plurals.settings_sync_remote_deletions_pending
            },
            outcome.count,
            outcome.count,
            outcome.total,
        )
```

### 5. Strings — both files

New keys in `presentation/src/commonMain/composeResources/values/strings.xml`, next to the existing
`settings_sync_deletions_pending` block (around line 346):

```xml
    <plurals name="settings_sync_remote_deletions_pending">
        <item quantity="one">The file you had synced is gone from this device. Delete it from the cloud folder too, or keep it and download it again?</item>
        <item quantity="other">%1$d of your %2$d synced files are gone from this device. Delete them from the cloud folder too, or keep them and download them again?</item>
    </plurals>
    <string name="settings_sync_delete_remotely">Delete them from the cloud too</string>
    <string name="settings_sync_keep_and_download">Keep them and download</string>
```

and in `values-hu/strings.xml` at the matching place:

```xml
    <plurals name="settings_sync_remote_deletions_pending">
        <item quantity="one">A szinkronizált fájlod eltűnt erről az eszközről. Töröljük a felhőmappából is, vagy megtartod és újra letöltöd?</item>
        <item quantity="other">%2$d szinkronizált fájlodból %1$d eltűnt erről az eszközről. Töröljük őket a felhőmappából is, vagy megtartod és újra letöltöd őket?</item>
    </plurals>
    <string name="settings_sync_delete_remotely">Törlés a felhőből is</string>
    <string name="settings_sync_keep_and_download">Megtartás és letöltés</string>
```

These sentences carry no text anybody else wrote, only numbers, so `pluralStringResource` /
`stringResource` from `com.pandulapeter.campfire.presentation.localization` is right — **not** the
`org.jetbrains.compose.resources` variant, which ignores the in-app language. Both files get every new key, and
the formatted ones are always called with their arguments.

### What this does *not* change

`SyncPlanner` itself. The planner is unchanged: it already emits `DeleteRemote` for exactly these keys, and its
tests stay green. The guard is the engine's, because it needs the index size and the policy, neither of which is a
question about one file.

## Tests

`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`
— alongside `a run that would delete the whole library stops and asks before anything moves` (line 665) and
`keeping the files a run asked about uploads them again` (line 684):

1. `a run that would empty the cloud folder stops and asks before anything moves` — ten files in the index and in
   `FakeSyncProvider`, `FakeLibraryFileLocalSource(files = emptyMap())`. Assert
   `SyncEngine.Result.DeletionsNeedConfirmation(count = 10, total = 10, direction = REMOTE)` and that
   `provider.files.keys` is untouched.
2. `a small library that vanished from this device stops and asks` — three files, index of three, empty local
   listing. The proportional rule would not trip here (`3 < MIN_DELETIONS_TO_ASK`, `3 != 4`… and in fact
   `3 == total` *would* trip; use a case where it does not: index of five, two of them also gone from the remote
   so they become `Forget`, three `DeleteRemote`). Assert it asks all the same, on the strength of
   `hasLostEverythingLocally`.
3. `deleting the files a run asked about removes them from the cloud folder` — same setup, policy
   `DELETE_REMOTELY`. Assert the provider is empty and the result is `Completed` with `deletedRemotely = 10`.
4. `keeping the files a run asked about downloads them again` — same setup, policy `KEEP_AND_DOWNLOAD`. Assert
   `summary.downloaded == 10`, `local.files.keys == library.keys` and `provider.files.keys == library.keys`.
5. `answering about this device does not let a run empty the cloud folder` — a plan that trips **both** guards
   (index of ten, local empty, remote holding two of them and eight gone): run with `DELETE_LOCALLY` and assert it
   still returns `DeletionsNeedConfirmation(direction = REMOTE)` rather than deleting remotely. Build the case so
   that both counts trip; if that turns out to be impossible to construct (a key can be only one of
   `DeleteLocal` / `DeleteRemote` / `Forget`), assert the weaker, still-valuable thing: `DELETE_REMOTELY` does not
   waive the local guard.
6. `a run with no index never asks` — index empty, local empty, remote empty: the `index.isNotEmpty()` condition
   is what keeps a first run on a fresh install from asking about nothing.

`SyncPlannerTest.kt` — one case if it is not already there:
`every file the index knows becomes a remote deletion when the library folder is gone` — local `emptyList()`,
remote and index holding the same five keys, assert five `SyncOperation.DeleteRemote`. This is the planner output
the new guard keys on, and it documents the input shape.

`SyncRepositoryImplTest.kt:168` asserts `assertIs<SyncOutcome.DeletionsNeedConfirmation>(state.lastOutcome)`; adding
a field to that data class compiles as long as nothing constructs it positionally there — check line 165-170 and
the fakes in `FakeSyncCollaborators.kt`. Add one case that a remote-direction confirmation reaches
`SyncState.Connected.lastOutcome` with `direction = REMOTE`.

## Verification

```
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
./gradlew :app:desktop:run
./gradlew :app:android:assembleDebug
./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64
./gradlew :app:web:wasmJsBrowserDistribution
```

Manual, **needs a Dropbox account** (and is the whole point of the plan):

1. Connect sync on desktop, let a library of a dozen songs go up, confirm both sides hold them.
2. Quit the app. Move `library/songs` out of the desktop data directory by hand (leave `library/setlists`).
3. Start the app. The launch sync must stop with the new Hungarian/English sentence naming the count and the
   direction, and **the Dropbox folder must still hold every song**.
4. Press **Keep them and download**. The songs come back onto the device and the folder is unchanged.
5. Repeat, answering **Delete them from the cloud too**, and confirm the folder empties and the message is gone.
6. Switch the app language to Hungarian and confirm both sentences and both buttons read correctly.

Manual, **needs an iOS device or simulator**: same thing through the Files app — delete the `songs` folder inside
Campfire's Documents, reopen the app, confirm it asks rather than emptying Dropbox.

## Docs

`CLAUDE.md` (root), the Sync section — this sentence becomes untrue as written and must name both directions:

> A plan that would delete **on this device** more than half of the files the index knows (and at least five of
> them), or every one of them, is not carried out: the run stops before anything moves and Settings asks.

and so does the sentence that follows it, which lists only the two local answers:

> **Delete them here too** runs again with the deletions allowed; **Keep them and upload** runs again with those
> files' index entries dropped, so they are new on this device and go back up.

`data/repository/implementation/CLAUDE.md`:

> A plan whose local deletions are more than half of the index (at least `MIN_DELETIONS_TO_ASK` of them) or the
> whole of it is not applied under `SyncDeletionPolicy.ASK`

and

> `DELETE_LOCALLY` applies such a plan as it is, and `KEEP_AND_UPLOAD` first drops the index entries of every file
> that is here and not there, which the planner then reads as new local files. The policy is a parameter of the one
> run it was given to, never state.

Both need the remote half, and the "an answer waives one guard" rule stated.

`data/model/CLAUDE.md` mentions `SyncOutcome` among the types the UI needs; add `SyncDeletionDirection`.

`documentation/sync.md:31-35` — this bullet is user-facing and now describes half the behaviour:

> - A run that would delete most of your library on this device — more than half of the files it has synced before
>   and at least five of them, or all of them — stops before anything moves and asks. … **Delete them here too**
>   goes ahead; **Keep them and upload** puts the files back into the cloud folder instead. Until you answer, every
>   run asks again.

Add the mirror bullet: a run that would delete most of the cloud folder because this device's library folder is
gone stops the same way, with **Delete them from the cloud too** / **Keep them and download**.

## Files touched

- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/SyncState.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SyncSettings.kt`
- `presentation/src/commonMain/composeResources/values/strings.xml`
- `presentation/src/commonMain/composeResources/values-hu/strings.xml`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncPlannerTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`
- `CLAUDE.md`, `data/repository/implementation/CLAUDE.md`, `data/model/CLAUDE.md`, `documentation/sync.md`

## Depends on

Nothing. It touches the same `SyncEngine.synchronize` prologue as plan 03, so land one and rebase the other.

## Rules

- Load the `code-style` skill before the first edit, and keep it loaded: the MPL header on any new file, KDoc on
  declarations and `//` inside statements, "why, not what" comments, trailing commas, `modifier` first.
- `commonMain` stays JVM-free. Everything here is already pure Kotlin.
- Every new string goes into **both** `values/strings.xml` and `values-hu/strings.xml`, and is read with
  `com.pandulapeter.campfire.presentation.localization.stringResource` / `pluralStringResource`.
- The per-module `CLAUDE.md` files are part of the change, not a follow-up.
