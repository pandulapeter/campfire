# Keep the synced preferences of a song that reached the cloud folder after the run listed it

**Kind:** data-loss  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncedPreferencesSync.kt`,
`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncedPreferencesTest.kt`,
`data/repository/implementation/CLAUDE.md`, `CLAUDE.md` (root, Sync section — lane E's file, one sentence)

## Problem

The last step of a completed run, `SyncedPreferencesSync.synchronize`, downloads `preferences.json` at the **end** of the
run, but prunes it against the songs the run left on this device, which came from a listing taken at the **start** of
the run (`SyncedPreferencesSync.kt:83-88` and `:114-126` at 8ee010b36):

```kotlin
val localNames = libraryFileLocalSource.loadLibraryFiles()
    .filter { it.kind == LibraryFileKind.SONG }
    .map { it.name }
    .plus(keptFileNames)
    ...
val merged = SyncedPreferencesDocument.withSongsWhere(
    document = SyncedPreferencesDocument.merge(base = canonicalBase, local = ..., remote = ...),
    isKept = { it.folded() in localNames },
)
```

A song another device added to the folder (with an override in the document) while this run was going is in the
remote document but not in `localNames`, so it is dropped, the document without it is uploaded over the other device's
(`expectedRevision = remote?.revision` matches, so the upload succeeds), and this device's base no longer names it
either. The other device's next run then sees base `S:X`, local `S:X`, remote nothing; `mergeValue` takes
`local == base -> remote`, the entry is removed, and `mergedPreferences.applyTo` deletes the override from that
device's own preferences. When this device later downloads the song, nothing names it any more: the capo / tempo /
transposition is gone on every device.

Proven with a throwaway probe against the real `SyncedPreferencesSync` and the test fakes:
- device A, base `{"a.cho":{"capo":1}}`, library `a.cho`, folder document `{"a.cho":{"capo":1},"s.cho":{"capo":3}}`
  → A uploads `{"songs":{"a.cho":{"capo":1}}}`;
- device B, base and preferences holding `s.cho` capo 3, library `a.cho, s.cho` → after its next run B's capos are
  `{a.cho=1}` and the folder document names `a.cho` only;
- the same with `base = null` (A's very first sync — the long run on a new phone that makes the window minutes wide):
  A uploads `{"songs":{}}`.

Realistic triggers: B creates or imports a song and sets a capo/tempo/transposition (its automatic run uploads the
file, then the document) while A is in a long run (a first sync of a large library under rate limiting); or B uses
**Update file name** on a song with an override during A's run — B moves the entry to the new name, A drops the new
name (not local yet) and follows B's removal of the old one.

This contradicts the root CLAUDE.md rule "A song no longer in the library after the run takes its entry with it": the
song was never in this device's library, it simply had not arrived.

## Fix

Prune only entries this device can have seen. An entry is exempt from pruning when its song is named by the folder's
document as read in this attempt and **not** by the last synced document the step was called with (the `base`
parameter — not `previous`, which a conflict retry moves onto the losing remote document; using `previous` would let
the retry prune the very entry the first attempt kept). A song deleted on this device was in the base, so it is
still pruned exactly as before; a song deleted on another device was pruned from the folder by that device.

In `synchronize`, before the `repeat`:

```kotlin
// The songs the last synced document named, folded. An entry the folder's document holds for any other song was
// written by another device since then, possibly for a song that reached the folder after this run listed it:
// it is kept until a run that has seen the song decides about it, or the next run, which has it in its base, prunes it.
val baseSongNames = SyncedPreferencesDocument.songNamesOf(base).mapTo(hashSetOf()) { it.folded() }
```

and inside the loop, after `effectiveRemote` is known:

```kotlin
val arrivingSongNames = SyncedPreferencesDocument.songNamesOf(effectiveRemote)
    .map { it.folded() }
    .filterTo(hashSetOf()) { it !in baseSongNames }
...
isKept = { it.folded() in localNames || it.folded() in arrivingSongNames },
```

Everything is compared folded (`String.folded()` already in the class), since the merged document is spelled through
`spelling` and the base may spell a song differently.

Effects, checked against the root CLAUDE.md Sync rules:
- *Three-way merge*: unchanged; only the pruning after it is narrowed.
- *Kept entry here*: the kept value is applied to this device's preferences too (`preferencesOf(merged)`), under the
  folder's spelling (`localNames[...] ?: it`). Harmless: it is keyed by a file name nothing here shows until the file
  arrives, which the next run does.
- *Convergence*: the step returns `merged` as the new base, so the next run has the song in its base. If the song was
  downloaded, it is local and kept; if its file was deleted elsewhere meanwhile, it is in the base and not local and is
  pruned there and in the folder. A stale entry (a song named by the document whose file is in no folder) survives at
  most one extra run.
- *"Unless the run failed to move it"*: `keptFileNames` still counts as local, unchanged.
- *Keep and upload*: `KEEP_AND_UPLOAD` drops the kept songs from the base; those songs are local (they are what is
  being kept), so they are kept by `localNames` either way. *Keep and download*: the songs are downloaded by the run
  and are local.
- *A missing or unreadable document*: `effectiveRemote` is then `previous` (the base on the first attempt), so it
  names nothing that is not in the base and nothing new is exempted.

Update the docs: in `data/repository/implementation/CLAUDE.md` ("A song no longer in the library — compared by case
and Unicode form, the run's failed files counting as there — is dropped…") add "and a song the folder's document has
gained since the last synced one counting as there too, since it may have reached the folder after the run listed
it"; in the root CLAUDE.md Sync bullet, change "unless the run failed to move it" to "unless the run failed to move it
or it reached the folder after the run listed it". Update the KDoc of `SyncedPreferencesSync` (the class comment's
"a song the run could not move excepted") the same way.

## Tests

In `SyncedPreferencesTest`:
- Change `a song that is not in the library takes its preferences with it on both sides` to call `synchronize` with
  `base = document("""{"gone.cho":{"tempo":140}}""")` — the song was synced before and is gone here, which is the
  deletion the test is about; with `base = null` the new rule keeps `gone.cho` for one run. Keep its assertions.
- Add `a song another device added during the run keeps its preferences on both sides`: folder document
  `{"a.cho":{"capo":1},"s.cho":{"capo":3}}`, base `{"a.cho":{"capo":1}}`, preferences capo `a.cho` 1, library `a.cho`
  → the folder document still holds `s.cho` capo 3, and the returned base names `s.cho`.
- Add `a song added elsewhere is kept on the first run too`: the same with `base = null`.
- Add `a song added elsewhere is still kept after a conflict`: as the first, with `provider.onUploadDocument` writing a
  third document (`{"a.cho":{"capo":1},"s.cho":{"capo":3},"b.cho":{"tempo":90}}`, library `a.cho, b.cho`) once → the
  final folder document holds `s.cho` and `b.cho`.
- Add `a song added elsewhere and gone by the next run is pruned then`: run once as in the first case, then again with
  the returned base and a folder document that still names `s.cho`, library still without it → `s.cho` is pruned.

Run `./gradlew :data:repository:implementation:desktopTest`.

## Manual check

Two devices on one Dropbox account. On device A start a sync of a large library (or throttle its network) so the run
takes a while; meanwhile on device B create a song, set a capo on it from the song details screen, and let B's run
finish. When A's run ends, wait for B's next run (edit anything on B), then open the song on both devices: the capo is
still set on both, and `preferences.json` in the Dropbox folder names the song.
