# Merge a song reset on one side field by field against the other side's change, instead of taking the other side's whole entry

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncedPreferences.kt`,
`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncedPreferencesTest.kt`

Lane B, second: apply after 10 and before 12 (all edit `SyncedPreferences.kt`).

## Problem

Device A resets a song's only override (the transposition back to 0), so the song's entry disappears from A's local
document. Meanwhile device B set a capo on the same song. A's next run brings the reset transposition back.

`SyncedPreferencesDocument.mergeValue` in `SyncedPreferences.kt`:

```kotlin
private fun mergeValue(base: JsonElement?, local: JsonElement?, remote: JsonElement?): JsonElement? = when {
    local == remote -> local
    local == base -> remote
    remote == base -> local
    local is JsonObject && remote is JsonObject -> { /* recurse key by key */ }
    else -> local ?: remote
}
```

For the song key: base `{transposition:2}`, local `null`, remote `{transposition:2, capo:3}`. None of the first three
match, local is not an object, so `else -> local ?: remote` returns the whole remote entry, transposition included.
Proven with a probe at ed4a1a5ce: `merge(base {a:{transposition:2}}, local {}, remote {a:{transposition:2,capo:3}})`
gives `{a:{transposition:2,capo:3}}`; expected `{a:{capo:3}}` (A removed the transposition, B changed only the capo,
so each keeps its change — the rule the KDoc of `merge` promises). The mirrored case (local changed a field, remote
dropped the whole entry) has the same shape: local's whole entry wins, resurrecting fields the remote removed.

## Fix

Before the `else` branch, add: when one side is null and the other side and the base are both `JsonObject`s,
recurse with an empty object standing for the null side:

```kotlin
(local == null || local is JsonObject) && (remote == null || remote is JsonObject) && base is JsonObject -> {
    val localObject = local as? JsonObject ?: JsonObject(emptyMap())
    val remoteObject = remote as? JsonObject ?: JsonObject(emptyMap())
    // the same key-by-key JsonObject(...) the object branch builds, over localObject and remoteObject
}
```

(Fold it into the existing object branch by computing `localObject`/`remoteObject` this way and taking the branch when
both are objects-or-null with at least one object and, if either is null, the base is an object.) An entry that comes
out empty is already dropped by `merge`'s `withSongsWhere(...) != JsonObject(emptyMap())`. Keep `else -> local ?: remote`
for everything else (no base: a removal cannot be told from never having had it, so the merge only adds).

Update the KDoc of `merge` only if its wording no longer fits (it already says every value is decided on its own).

## Tests

In `SyncedPreferencesTest` ("The merge"), using the existing `merge(base, local, remote)` helper:

- `a song reset here keeps the field another device changed` —
  `merge(base = """{"a":{"transposition":2}}""", local = "{}", remote = """{"a":{"transposition":2,"capo":3}}""")`
  equals `document("""{"a":{"capo":3}}""")`.
- `a song reset elsewhere keeps the field changed here` — the mirror:
  `merge(base = """{"a":{"transposition":2}}""", local = """{"a":{"transposition":2,"capo":3}}""", remote = "{}")`
  equals `document("""{"a":{"capo":3}}""")`.

## Manual check

Two devices, one library song with transposition +2 synced to both. On A set the transposition back to 0 (do not
sync); on B add capo 3 and let B sync. Sync A: the song shows transposition 0 and capo 3 on A, and after B syncs
again, on B too.
