# Stop the first sync run after an upgrade from 4.7.0 from wiping every device's tag colors and song accents

**Kind:** bug  ·  **Severity:** high  ·  **Platforms:** all
**Files:**
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/SyncedPreferencesDocument.kt`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/SyncIndexDocument.kt`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/SyncEngine.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/SyncRepositoryImplTest.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/SyncedPreferencesTest.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/SyncIndexDocumentTest.kt`,
`data/sync/implementation/CLAUDE.md`

## Problem

`preferences.json` is merged three ways against the base kept in `sync-index.json` (`SyncIndexDocument.syncedPreferences`). Its
local side is built by `SyncedPreferencesDocument.localDocument`, which takes the base and replaces every value of a field this
version owns with what this device's preferences hold. A base value of an owned field that the preferences do not hold is
therefore read as *removed here*:

```kotlin
// SyncedPreferencesDocument.kt, localDocument (d650f3cb0)
val understood = listOfNotNull(
    TRANSPOSITION.takeIf { name in readable.transpositions },
    TEMPO.takeIf { name in readable.tempos },
    CAPO.takeIf { name in readable.capos },
    BEAT_LEVELS.takeIf { name in readable.beatLevels },
)
...
val tags = baseTags.orEmpty().filterValues { !it.isReadableShape } + preferences.tags.mapValues { JsonPrimitive(it.value) }
```

That is only true if the device that wrote the base owned the field too. Two fields are owned here that 4.7.0 (the last
release, and the first one that synced `preferences.json` at all) did not own: each song's `beatLevels`
(`b5d20112f`, "Make the metronome remember the accented beats of each song") and the top-level `tags`
(`d650f3cb0`, "Add support for colored tags"). 4.7.0's `localDocument`
(`data/repository/implementation/.../sync/SyncedPreferences.kt` at tag `4.7.0`) only replaces `transposition`, `tempo`,
`capo` and the `chords` shapes, and passes everything else through, so its base holds `tags` and `beatLevels` verbatim.
No other synced field or value range changed since 4.7.0 (`TEMPO_RANGE` 30..300, `CAPO_RANGE` 0..12 and the
transposition range are unchanged), so these two are the whole of the hazard.

The scenario: device A runs 4.7.0, device B runs this build. B colors tags and sets per-song accents and syncs. A syncs;
its base now holds B's `tags` and `beatLevels`. A upgrades: its `preferences.json` has no `tagColors` or `beatLevels`,
since 4.7.0 never had them. On A's first run `localDocument` writes `tags: {}` and strips every song's `beatLevels`,
the merge sees local ≠ base and remote == base, takes local, and uploads the removals; B's next run applies them. Every
tag color and every per-song accent is erased on every device, with nothing left to restore them from.

Proved at d650f3cb0 with a throwaway test through the whole run (`syncRepository(...)`, `restore()`, `synchronize()`):
an index whose base is
`{"version":1,"songs":{"song_1.cho":{"capo":2,"beatLevels":{"3/4":["accent","normal","muted"]}}},"tags":{"christmas":"red"}}`,
the folder's document the same, and preferences holding only the capo. The run ends in `Success` and the folder's
document becomes `{"songs":{"song_1.cho":{"capo":2}},"tags":{},"version":1}`.

`SyncIndexDocument` has no marker today saying which version wrote the base (only the `version` inside the document
itself, which is the document format, 1 in both versions, and must not change for this, since bumping it makes 4.7.0
treat the document as `isNewerFormat`).

## Fix

The rule the merge already follows is "without a base a removal cannot be told from never having had it, so the merge
only adds". A base written by a version that did not own a field has no opinion on that field either, so the fix is to
take that field out of the base before anything reads it. That needs the index to say which fields the base's writer
owned.

1. In `SyncedPreferencesDocument`, name the owned fields and add one function:

   ```kotlin
   /** The fields this version owns ([localDocument] replaces them), as written into the index beside the base. */
   val OWNED_FIELDS = setOf(TRANSPOSITION, TEMPO, CAPO, BEAT_LEVELS, CHORDS, TAGS)

   /** What 4.7.0, the first version that synced this document, owned: an index with no list was written by it. */
   val OWNED_FIELDS_OF_4_7_0 = setOf(TRANSPOSITION, TEMPO, CAPO, CHORDS)

   /**
    * [base] without the fields [ownedByWriter] does not name: the version that wrote it passed them through and never
    * read them, so it has no opinion on them, and read as this device's they would be removals of everything another
    * device set. A song's entry left empty is dropped, as [localDocument] never writes one.
    */
   fun withoutFieldsNotOwnedBy(base: JsonObject, ownedByWriter: Set<String>): JsonObject
   ```

   It removes each of `TRANSPOSITION`, `TEMPO`, `CAPO` and `BEAT_LEVELS` that is not in `ownedByWriter` from every
   entry of `SONGS` (dropping entries left empty), and each of `CHORDS` and `TAGS` that is not in it from the top
   level. With nothing to remove it returns `base` itself. Make the private field-name constants it uses visible to
   these two sets (they stay `private const val`; the sets live in the same object).

2. In `SyncIndexDocument`, add after `syncedPreferences`:

   ```kotlin
   /**
    * The [SyncedPreferencesDocument] fields the version that wrote [syncedPreferences] owned, or null for an index
    * written by 4.7.0, which did not write it. See [syncedPreferencesBase].
    */
   val syncedPreferencesFields: Set<String>? = null,
   ```

   set it to `SyncedPreferencesDocument.OWNED_FIELDS` in `SyncIndexDocument.of(...)`, and add

   ```kotlin
   /** [syncedPreferences] as a base this version can merge against: what its writer did not own taken out. */
   fun syncedPreferencesBase() = syncedPreferences?.let {
       SyncedPreferencesDocument.withoutFieldsNotOwnedBy(it, syncedPreferencesFields ?: SyncedPreferencesDocument.OWNED_FIELDS_OF_4_7_0)
   }
   ```

3. In `SyncEngine.synchronize`, read the base through it:

   ```kotlin
   var syncedPreferences = document.syncedPreferencesBase().takeIf { document.accountId == accountId }
   ```

   This is the only place the stored base enters a run; from here it travels through `preparePass`, every snapshot
   (`SyncIndexDocument.of`, which now stamps `OWNED_FIELDS`), `Result.Completed.index` and `SyncRunner`'s final
   `result.index.copy(syncedPreferences = ...)`, which keeps the stamp. Stamping a stripped base with the current set is
   sound even if the run dies before the preferences step: a base without a field is one with no opinion on it, under
   any stamp. `SyncedPreferencesSync`'s conflict retry (`previous = effectiveRemote`) needs nothing: by then the merged
   values were already applied to the preferences (`applyTo`), so the device does own what that base holds.

   After the strip, A's first run merges `tags` and each song's `beatLevels` with no base: A holds none and the folder
   holds B's, so `mergeValue` takes the folder's (`local == base`), `applyTo` adopts them into A's preferences, and the
   upload is skipped because `merged == remoteDocument`. A tag color or accent set on A before that first run is merged
   key by key against B's, A's winning on the same key. The cost is one run in which a removal another device made of
   those two fields after A's last 4.7.0 run cannot be seen as a removal on A — which is not a cost at all, since A
   held none of them.

Alternatives weighed:

- **The reviewer's proposal**: keep the base whole, and in `localDocument` treat the base's values of a field the writer
  did not own as this device's own (local entries winning per key), with `applyTo` adopting them. It reaches the same
  result in every case checked (an upgrade with nothing set locally, a value set locally on the same key, a removal on
  the other device since the base), but needs the "writer's fields" passed down into `localDocument` and a per-field
  union there, for the song fields and the two top-level members separately. Stripping the base reuses the merge's
  existing no-base rule and is one function applied in one place.
- **A marker in this device's `preferences.json`** saying which synced fields it has ever held: wrong place. What
  matters is whether the *base* was written by an owner, which is a property of the index; the preferences are also in
  the device backup while the index is not, so a restored phone would carry a marker that says nothing about its (absent)
  base, and the change would reach `:data:model` and `:data:source:local` for no gain.
- **An integer format number in the index** instead of a set of names: works, but every later version would need a table
  from number to fields; a set of names is read directly, and the next field added needs only to join `OWNED_FIELDS`.

## Tests

- `SyncRepositoryImplTest`: `a base written by 4.7.0 does not read the fields it passed through as removed here` — the
  probe described above, made permanent: a `FakeSyncIndexLocalSource` whose index is `SyncIndexDocument.of(...)` for
  `song(1)` (hash of its bytes, revision `"r1"`) with `syncedPreferencesFields` then set to `null` (`.copy(...)`, as
  4.7.0 wrote it), the base and `provider.documents[SyncedPreferencesDocument.FILE_NAME]` both
  `{"version":1,"songs":{"song_1.cho":{"capo":2,"beatLevels":{"3/4":["accent","normal","muted"]}}},"tags":{"christmas":"red"}}`
  (revision `"d1"`), the library holding `song(1)`, and `FakeUserPreferencesRepository(defaultUserPreferences(capos =
  mapOf("song_1.cho" to 2)))`. After `restore()`, `synchronize(SyncDeletionPolicy.ASK)` and `awaitOutcome()` (a
  `Success`): the folder's document decodes to the same JSON as before (and is expected to keep revision `"d1"`, since nothing changed), `preferences.current.tagColors` is
  `mapOf("christmas" to "red")`, `preferences.current.beatLevels` holds song_1's 3/4 accents, and the saved index's
  `syncedPreferencesFields` is `OWNED_FIELDS`. It fails at d650f3cb0 (the document becomes `{"songs":{"song_1.cho":
  {"capo":2}},"tags":{},"version":1}`). A second case with the stamp present (`OWNED_FIELDS`) and the same preferences
  must still upload the removals, proving the strip applies only to a 4.7.0 base.
- `SyncedPreferencesTest`: `withoutFieldsNotOwnedBy` with the 4.7.0 set drops `tags` and every song's `beatLevels`,
  drops a song entry that held only `beatLevels`, keeps `transposition`/`tempo`/`capo`/`chords` and unknown fields, and
  returns the document unchanged with `OWNED_FIELDS`.
- `SyncIndexDocumentTest`: add `"syncedPreferencesFields"` to the current-format literal and the expected document; the
  "written before the newer fields existed" case reads it as `null`.

## Manual check

With two devices on one Dropbox folder: install 4.7.0 on device A, this build on device B. On B color a tag and accent a
beat for a song, sync; sync A. Upgrade A to the fixed build and let it sync, then sync B. The tag color and the accent are
still on B, and now also on A (the tag colored in the songs list, the accent in the metronome panel of that song).

Update `data/sync/implementation/CLAUDE.md`, in the `preferences.json` paragraph after "against the document the last
run settled (`SyncIndexDocument.syncedPreferences`, …)": the index names the fields the base's writer owned
(`syncedPreferencesFields`, absent for 4.7.0's, which owned the transposition, tempo, capo, and chords), and the engine
takes the others out of the base before the run reads it (`syncedPreferencesBase`), so a field this version newly owns
is merged as with no base (only adding) on the first run after an upgrade, rather than this device's empty value
reading as a removal of every other device's; a later field joins `SyncedPreferencesDocument.OWNED_FIELDS`.
