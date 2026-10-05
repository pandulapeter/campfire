# Match `preferences.json` entries to this device's songs by folded name, applying them under the local file's spelling

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the old step 4 compared and applied against a snapshot re-keyed onto local spellings, which hides a dead key the bug already left (`capos {Foo.cho=3}` on a device whose file is `foo.cho` compares equal to the merged `{foo.cho=3}`, so nothing is ever written and the screen keeps reading nothing); `applyTo` now takes the raw snapshot as `since`. All three documents are canonicalized onto one spelling before the merge (instead of collapsing the merged result), the canonical spelling comes from plan 10's effective remote, snapshot collisions prefer the live local key, and a test for the already-affected device is added.
**Files:** `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncedPreferencesSync.kt`,
`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncedPreferences.kt`,
`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncedPreferencesTest.kt`,
`data/repository/implementation/CLAUDE.md`

Lane B, last: apply after 10–14.

## Problem

The engine treats file names that differ only by case or Unicode form as one file (`foldRemoteNamesOntoLocal`), and
each device keeps its own spelling (root `CLAUDE.md`: "a capitalised or decomposed file keeps its spelling"). Two
devices that both held a song before they were connected — imported under an older naming rule as `Foo.cho` on A and
`foo.cho` on B — sync as one file but keep their own names, and the overrides are keyed by each device's own name;
the app reads them by exact key (`transpositions[song.fileName, null]` in `SongsScreen.kt`, `capos[songFileName, …]`
in `SongCapo.kt`).

`SyncedPreferencesSync.synchronize` keeps an entry when its folded name is in the library
(`isKept = { it.folded() in songNames }`) but merges and applies it under the document's spelling. Probe at
ed4a1a5ce: remote `{"Foo.cho":{"capo":3}}`, B's preferences `transpositions {foo.cho=2}`, library `foo.cho` → B ends
with `capos {Foo.cho=3}` (a key no screen on B reads) and the cloud document holds both `Foo.cho` and `foo.cho`; A
likewise applies only its own spelling. The overrides of that song never reach the other device, and each device's
preferences collect a dead key.

## Fix

All in `SyncedPreferencesSync.synchronize`, with the re-keying helpers in `SyncedPreferencesDocument` (pure, testable).
"Folded" is `normalizedToNfc().lowercase()`, as `folded()` already is. Plan 10 is in place, so `effectiveRemote` is the
decoded remote document when it is readable and `previous` otherwise; plan 10's newer-format check comes before all of
this.

1. Build `localNames: Map<String, String>` from the library song names plus `keptFileNames`, folded name → this
   device's spelling (the list `songNames` is already built from; keep it as a map instead of a set, and keep
   `isKept = { it.folded() in localNames }`). Two local names that fold alike (only possible on a case-sensitive file
   system, and one of them is refused by the service anyway): take the first in sort order, so it is deterministic.
2. **Pick one spelling per song** before merging: for each folded name, the canonical spelling is the first in sort
   order among `effectiveRemote`'s song keys with that folded name; failing that, among `previous`'s; failing that,
   this device's own (`localNames`, or the preferences key itself). Every device reads the same remote document, so
   they all converge on the same spelling.
3. **Canonicalize all three inputs** onto those spellings: `previous`, `effectiveRemote`, and `snapshot` (its three
   maps, before `localDocument`). Where one document holds two keys of one folded name (the bug has already left such
   documents in some folders), collapse them field by field into the canonical key, preferring the fields of the entry
   that was already under the canonical key. Where `snapshot` holds two keys of one folded name for the same field
   (a dead key the bug left, `Foo.cho`, beside the live one, `foo.cho`), prefer the value under this device's own
   spelling — the one its screens read and the user set. The merge then compares like with like, and its result already
   has one key per song (no collapse afterwards: collapsing after the merge would combine a base and a side that
   disagree on spelling and read one side's values as additions).
4. **After merging**, re-key `preferencesOf(merged)` onto `localNames` (folded lookup; a name with no local song stays as
   it is) — that is `mergedPreferences`. Compare it with, and apply it over, the **raw** `snapshot` as read, not a
   re-keyed one: `if (mergedPreferences != snapshot) … mergedPreferences.applyTo(preferences, since = snapshot)`. A dead
   key in `snapshot` (`Foo.cho`) is then a key `since` holds and the target does not, so `applyTo` removes it, and the
   value lands under `foo.cho`, which the screens read. Re-keying the snapshot for this comparison would make the two
   equal and leave the dead key, and the song without its value, forever.
5. Keep `if (merged == remoteDocument) return merged` against the raw decoded remote, so a folder whose document still
   holds two spellings is rewritten with one.

With plan 14, the marker's `it == mergedPreferences` check uses this re-keyed `mergedPreferences`. With plan 12,
`localDocument` reads `preferencesOf(base)` of the canonicalized base, so the keys it strips match. With plan 13, the
engine prunes the base by folded name, which canonicalization does not disturb.

Add to the `preferences.json` paragraph of `data/repository/implementation/CLAUDE.md`: entries are matched to songs by
case and Unicode form and applied under this device's spelling; the document keeps one spelling per song, the first
in sort order of those it already holds.

Drop this plan if, at execution time, the root `CLAUDE.md` no longer lets two devices keep different spellings of one
synced file.

## Tests

In `SyncedPreferencesTest` ("The run's step"):

- `an entry spelled differently in the folder applies to this device's file` — remote `{"Foo.cho":{"capo":3}}`,
  preferences `transpositions = mapOf("foo.cho" to 2)`, `library("foo.cho")`, base null → preferences hold
  `capos {foo.cho=3}`, `transpositions {foo.cho=2}`, and no `Foo.cho` key; the uploaded document has exactly one song
  key, `Foo.cho`, with both fields.
- `two spellings of one song in the folder are collapsed` — remote `{"Foo.cho":{"capo":3},"foo.cho":{"tempo":90}}`,
  `library("foo.cho")`, empty preferences, base null → preferences `capos {foo.cho=3}`, `tempos {foo.cho=90}`; the
  uploaded document has one key for the song, `Foo.cho`.
- `a value already stored under the folder's spelling moves onto this device's file` — the device the bug already
  hit: preferences `capos = mapOf("Foo.cho" to 3)`, `transpositions = mapOf("foo.cho" to 2)`, `library("foo.cho")`,
  remote and base both `{"Foo.cho":{"capo":3},"foo.cho":{"transposition":2}}` → preferences `capos {foo.cho=3}` with no
  `Foo.cho` key, `transpositions {foo.cho=2}`; the uploaded document is `{"Foo.cho":{"capo":3,"transposition":2}}`.
  (This is the one that fails with a re-keyed `since`.)

## Manual check

Hard to arrange: on a desktop and a phone that each already hold the same song under names differing only in case,
connect both to one account, set a capo on the song from the library on one, sync both: the other shows the capo, and
`preferences.json` names the song once.
