# Stop a second device's first sync from doubling a setlist that differs only in the date an import gave it

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** amended — recommendation moved from (c) to (b): a "first meeting" (no index entry) is not only a new device; the index is also cleared by every disconnect, an account switch, a reinstall and a backup restore, so (c) would silently overwrite a date the user deliberately moved on this device in those documented flows, where today both survive as a conflict copy. (c) is kept as an option with its gaps fixed (date only, not the countdown; `unknownFields` compared; the write re-checks the local bytes under the lock). Added the release timing (dated demo copies exist only since 4.6.0, 2026-10-02), which shrinks (b)'s main drawback.
**Decision:** the user chose (b), plant the demo undated (2026-10-04).
**Files:**
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncPlanner.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt` (constructs `SyncEngine`)
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncPlannerTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt` (and its fakes in the same folder)
- option (a)/(b) only: `presentation/src/commonMain/composeResources/files/demo/getting_started.setlist.json`, `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImpl.kt`, `domain/api/.../ImportFilesUseCase.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `CLAUDE.md` (root, Conventions → "A setlist shows every song it names", and Sync), `data/repository/implementation/CLAUDE.md` (sync engine section), `domain/implementation/CLAUDE.md` if the import's dating changes

## Problem

An import dates every setlist that names no day of its own by the day it is imported on.
`ImportFilesUseCaseImpl.kt`:

```kotlin
// A setlist that arrives naming no day of its own is dated the way a new one is, by the day it was created
// here. The bundled demo setlist is one of them, so it is dated by the first run that plants it.
val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
...
date = it.date ?: replaced?.date ?: today,
```

The bundled `presentation/src/commonMain/composeResources/files/demo/getting_started.setlist.json` has no `"date"`.
Every fresh installation plants it on its first run, before the user can connect sync. So a phone installed on Monday
holds `getting_started.setlist.json` with `"date": "<Monday>"`, and a tablet installed on Thursday holds the same file
with `"date": "<Thursday>"`. `SetlistDocument` has no other per-device field (title, description, date,
isCountdownShown, priority, isArchived, songs), and the demo songs are written through the same prettifier on every
device, so the songs are byte-identical and the setlist differs in the date alone.

When the tablet connects to the folder the phone already syncs, its first run has an empty index. `SyncPlanner`
(`operationFor`, both sides present):

```kotlin
val hasChangedLocally = indexEntry == null || indexEntry.localHash != local.hash
val hasChangedRemotely = indexEntry == null || indexEntry.remoteRevision != remote.revision
...
else -> SyncOperation.Resolve(key, remote.revision)
```

`SyncEngine.resolve` only short-circuits on identical bytes:

```kotlin
if (isSameContent(provider, local, remoteFiles[key]?.contentHash)) { ... }
val remote = downloadWithinLimit(provider, key, remoteFiles)
return resolveWith(...)   // remote.contentEquals(localBytes) is false -> writeLibraryFileToFreeName -> "getting_started (2).setlist.json"
```

So the tablet keeps its copy under the name, writes the phone's next to it as `getting_started (2).setlist.json`,
uploads both, and from then on every device shows two "Getting started" setlists and a conflict in the summary. The
songs do not double (same bytes, recorded as same content). Installations on the same calendar day (in the same time
zone) do not hit it, which is why it is easy to miss in testing.

The same happens to any ordinary undated setlist file (a hand-written one, or one exported before setlists had a
date) imported on two devices on different days, and to the demo planted again from Settings on one of them. A setlist
exported by the app carries its date, so a zip exported on one device and imported on another is not affected.

Note that the import itself already treats an undated incoming setlist as the same as a dated library one
(`ImportPlanner.holdsTheSameAs`: `other.date == null || (date == other.date && isCountdownShown == other.isCountdownShown)`);
only sync compares bytes.

The root `CLAUDE.md` documents the current dating on purpose: "an import dates a setlist that carries none the same
way, the demo one included". That is why this is the user's decision.

## Fix

Options:

- **(a) A fixed date in the resource.** Add `"date": "2026-10-01"` (any fixed day) to `getting_started.setlist.json`.
  One-line change, new installations then plant identical bytes. Drawbacks: the demo is "for" a day in the past
  forever; installations that already planted a dated copy (the released apps) still conflict with new ones; and
  re-adding the demo from Settings on an existing installation whose library still holds its own dated
  `getting_started.setlist.json` (Settings offers it while any demo song is missing) now meets a dated incoming file
  with a different date, which `holdsTheSameAs` calls different — the import would ask its keep-both/replace question
  where today it silently disregards the setlist. Not recommended.
- **(b) Plant the demo undated.** Give `ImportFilesUseCase` a way to leave an undated setlist undated (for example a
  `isDatingUndatedSetlists: Boolean = true` parameter that `CampfireViewModel`'s demo planting passes as false), so the
  demo setlist sorts after the dated ones and reads as "for no particular day". New installations then agree; the
  re-add from Settings still matches through `holdsTheSameAs`. Drawbacks: does not help installations that already hold
  a dated copy, nor ordinary undated imports; changes documented behaviour (root `CLAUDE.md`).
- **(c) Let sync's first meeting of a setlist ignore the date.** Covers new installations, the copies already planted
  by 4.6.0, and ordinary undated setlists imported on two devices, with no change to the import or the resource:
  1. In `SyncPlanner`, carry whether the file was ever indexed into `Resolve`:
     `data class Resolve(override val key: SyncKey, val revision: String, val isFirstMeeting: Boolean = false)`, set
     to `indexEntry == null` in `operationFor`. (`SyncEngine.download` builds a `Resolve` itself for a local file that
     appeared during the run — a conflict copy written earlier in the pass, or a file the user just made; leave that
     one `false`.)
  2. Give `SyncEngine` a pure comparison, without parsing JSON itself: a constructor parameter such as
     `isSameSetlistButForItsDate: suspend (ByteArray, ByteArray) -> Boolean`, built in `SyncRepositoryImpl` from
     `SetlistLocalSource.parseSetlist(document: String)` (it is `suspend`): both parse, and every field but `date` and
     `fileName` is equal — title, description, `isCountdownShown`, `isArchived`, the entries and `unknownFields`
     (`Setlist` carries them; `ImportPlanner.holdsTheSameAs` compares them too). Do **not** ignore `isCountdownShown`:
     an import never sets it on an undated setlist, so the demo copies agree on it, and it is a choice the user made.
     Fake it in tests.
  3. In `SyncEngine.resolve`, after the `isSameContent` check and the download, when
     `operation.isFirstMeeting && key.kind == LibraryFileKind.SETLIST && isSameSetlistButForItsDate(local, remote)`:
     take the remote version — under `libraryFileLock`, re-read the local file and write `remote` over it only if it
     still equals `local` (the way `download` re-checks; a save that landed meanwhile falls through to `resolveWith`
     and its conflict copy), call `onLocalFileChanged(key)`, and return
     `SyncIndexEntry(localContentHash(remote), operation.revision)` with the summary counting one download and no
     conflict.
  4. Only on a first meeting: once a file is indexed, two devices that moved its date differently made real edits.

  **Why it is not recommended:** "no index entry" is not the same as "a device that has never synced". The index is
  deleted by every **Disconnect** (`SyncRepositoryImpl.disconnect`, `saveSyncIndex(null)`), dropped when the account
  changes (`SyncEngine.synchronize` ignores an index written for another account), and absent after a reinstall and
  after a device backup restore (it is kept out of the backup on purpose). In each of those the user may well have
  moved a setlist's date on purpose — the day of a gig, with its countdown — and today the first run after
  reconnecting keeps that date and the cloud's side by side as a conflict copy; under (c) the cloud's older date
  silently replaces it, which goes against "Nothing is ever overwritten implicitly" and "A file changed on both sides
  is never merged" (root `CLAUDE.md`). The bug it fixes costs one duplicate setlist the user can delete; the
  regression costs a date nobody is told was lost. If (c) is chosen anyway, narrow it to the case the bug is about:
  only when one side's date is the day this installation planted or imported it — which nothing records today — or
  only for `getting_started.setlist.json`.

**Recommended: (b).** Setlist dates first shipped in 4.6.0 (tagged 2026-10-02, two days before this plan), and a
library from before it kept its undated demo setlist, so the installations that hold a dated demo copy are those made
in the last few days; (b) stops new ones from appearing, needs no change to sync, and keeps every sync invariant as
documented. Pass the flag from both demo paths in `CampfireViewModel` (the first-run planting and Settings' "add the
demo songs"), so a re-added demo setlist is undated too. Accept the leftovers: a 4.6.0 installation and a new one
syncing together still double the demo setlist once, and two devices importing the same undated hand-written setlist
on different days still conflict — both visible, both harmless, both resolved by deleting one copy.

Whichever is chosen, update the root `CLAUDE.md` sentence on setlist dates (and the Sync section for (c): "A file
changed on both sides is never merged" gains the exception), and `data/repository/implementation/CLAUDE.md`'s engine
description for (c).

## Tests

For (b): an `ImportFilesUseCaseImpl` test (in `:domain:implementation` `desktopTest`) that the flag leaves an undated
setlist undated, that it keeps the day of a library setlist it replaces, and that the default still dates it today.
For (c), in `:data:repository:implementation` `desktopTest`:
- `SyncPlannerTest`: a file on both sides with no index entry plans `Resolve(..., isFirstMeeting = true)`; with an
  index entry whose hash and revision both moved, `isFirstMeeting = false`.
- `SyncEngineTest`: two setlist files with the same name, identical but for `"date"`, no index entry → after the run
  the local file holds the remote bytes, no ` (2)` file exists on either side, the summary has no conflict. The same
  pair with an index entry (both changed since) → still the conflict copy. Two setlists that differ in a song, or in
  `isCountdownShown` → still the conflict copy. A local file saved between the download and the write (fake storage
  that changes on read) → still the conflict copy.
For (a): none.

## Manual check

Two devices (or the desktop build with two data directories). On device A, start fresh, let the demo plant, connect
Dropbox and sync. On device B, start fresh with the system clock set a day later (or edit B's
`library/setlists/getting_started.setlist.json` to another `"date"`), connect the same account. After B's first run,
the Setlists screen on both devices shows one "Getting started", and the Dropbox folder holds no
`getting_started (2).setlist.json`.
