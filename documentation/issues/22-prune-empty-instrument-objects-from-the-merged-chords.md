# Prune an instrument left with no chord shapes from the merged `preferences.json`, as an emptied song entry is pruned

**Kind:** bug (extra upload)  ·  **Severity:** low  ·  **Platforms:** all (sync)
**Files:**
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncedPreferences.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncedPreferencesTest.kt`

## Problem

`SyncedPreferencesDocument.merge` goes down the tree value by value and only prunes emptied **song** entries:

```kotlin
fun merge(base: JsonObject?, local: JsonObject, remote: JsonObject?): JsonObject {
    val merged = (mergeValue(base, local, remote) as? JsonObject) ?: JsonObject(emptyMap())
    // Each side can take a different field off one song, which leaves an entry neither of them holds any more.
    return withSongsWhere(merged) { (merged[SONGS] as? JsonObject)?.get(it) != JsonObject(emptyMap()) }
}
```

The `chords` member has the same shape one level up (`{"guitar": {"F:0.4.7": "x x 3 2 1 1"}}`). Base
`{"chords": {"guitar": {"F…": a, "G…": b}}}`; this device resets its F shape, another device its G shape. `mergeValue`
on `guitar` merges key by key: F — remote equals base, so local's removal wins; G — local equals base, so remote's
removal wins → the merged document holds `"guitar": {}`. It differs from the remote document, so it is uploaded and
becomes the next base. On the next run `localDocument` drops the empty instrument (`fields.takeIf { it.isNotEmpty() }`)
and writes `"chords": {}` (the base had a `chords` member); local ≠ remote and remote = base, so the merge takes local and
uploads again. Harmless in content (`preferencesOf` skips empty instruments) but one needless upload of
`preferences.json`, on every device that settles it, for every such pair of resets.

## Fix

In `merge`, prune empty instrument objects as well, keeping the `chords` member itself (an empty `chords` is what
`localDocument` writes once a base had one, so removing it would make the next run differ again):

```kotlin
val withoutEmptySongs = withSongsWhere(merged) { (merged[SONGS] as? JsonObject)?.get(it) != JsonObject(emptyMap()) }
val chords = withoutEmptySongs[CHORDS] as? JsonObject ?: return withoutEmptySongs
// The same for an instrument each side took a different shape off: a member neither side holds any more.
return JsonObject(withoutEmptySongs + (CHORDS to JsonObject(chords.filterValues { it != JsonObject(emptyMap()) })))
```

Extend the comment above `merge`'s return. No CLAUDE.md sentence describes the empty-entry pruning in that detail;
none needs changing.

## Tests

`SyncedPreferencesTest`: base `{"version":1,"songs":{},"chords":{"guitar":{"F":"a","G":"b"}}}`, local without F, remote
without G → `merge(...)` is `{"version":1,"songs":{},"chords":{}}`; and settling that result again (local from
`localDocument(base = merged, preferences with no chords)`, remote = merged) returns a document equal to `merged`, so no
second upload would follow. Also: an instrument still holding a shape is kept, and an instrument whose only value is
one this version does not read (a non-string) is kept.

## Manual check

None needed beyond the unit test; optionally, with two synced devices, reset a different guitar chord shape on each
between two runs and check in the Dropbox web UI that `preferences.json` holds no `"guitar": {}` and its revision stops
moving after one run on each device.
