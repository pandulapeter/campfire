# Keep the values this version cannot read when it builds this device's side of `preferences.json`, instead of stripping them

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncedPreferences.kt`,
`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncedPreferencesTest.kt`,
`data/repository/implementation/CLAUDE.md`

Lane B, third: apply after 11 and before 13 (11 and this edit `SyncedPreferences.kt`).

## Problem

The document is meant to pass through what a later version writes (KDoc of `SyncedPreferencesDocument`: "a version
that does not know them passes them through untouched"). That holds for unknown fields, but not for values in the
three known fields that this version cannot read. `localDocument`:

```kotlin
val fields = (baseSongs?.get(name) as? JsonObject).orEmpty() - SONG_FIELDS.toSet() + listOfNotNull(
    preferences.transpositions[name]?.let { TRANSPOSITION to JsonPrimitive(it) },
    preferences.tempos[name]?.let { TEMPO to JsonPrimitive(it) },
    preferences.capos[name]?.let { CAPO to JsonPrimitive(it) },
)
```

strips every known field from the base and re-adds only what the preferences hold, while `preferencesOf` never put a
value it rejects into the preferences (`?.takeUnless { it.isString }?.intOrNull?.takeIf { it in range }`, plus
`.filterValues { it != 0 }` for transpositions). So a value a later version may legitimately write — tempo 400 or
92.5, capo 13, a transposition outside -11..11 — is missing from the local document, which then differs from the
base; the merge reads that as "removed on this device" and the next upload erases it for the newer device. Proven
with a probe at ed4a1a5ce: base `{a.cho:{tempo:400}}`, empty preferences → `localDocument` gives `{songs:{}}`, and
`merge(base, local, remote = base)` gives `{songs:{}}`.

## Fix

In `localDocument`, strip from a base entry only the known fields whose value this version did read, i.e. the ones
`preferencesOf(base)` returned for that song; a base field whose value `preferencesOf` rejects stays as it was unless
the preferences hold a value for it, which then replaces it:

```kotlin
val readable = base?.let(::preferencesOf) ?: SyncedPreferences()
...
val baseEntry = (baseSongs?.get(name) as? JsonObject).orEmpty()
val understood = listOfNotNull(
    TRANSPOSITION.takeIf { name in readable.transpositions },
    TEMPO.takeIf { name in readable.tempos },
    CAPO.takeIf { name in readable.capos },
)
val fields = baseEntry - understood.toSet() + listOfNotNull(/* the three from preferences, as now */)
```

Note a stored `transposition: 0` is rejected by `preferencesOf` and so would now be passed through; this version never
writes one, so only another writer could have put it there, and passing it through is what "untouched" means.
Mention in `data/repository/implementation/CLAUDE.md`'s `preferences.json` paragraph that a value of a known field
this version cannot read passes through like an unknown field.

## Tests

In `SyncedPreferencesTest` ("The merge"):

- `a value this version cannot read is carried over from the base` — base
  `{"version":1,"songs":{"a.cho":{"tempo":400,"capo":2}}}`, preferences `SyncedPreferences()` (capo reset here) →
  `localDocument` is `{"version":1,"songs":{"a.cho":{"tempo":400}}}`.
- `a value set here replaces one this version cannot read` — same base, preferences `tempos = mapOf("a.cho" to 120)`,
  `capos = mapOf("a.cho" to 2)` → `{"a.cho":{"tempo":120,"capo":2}}`.

## Manual check

None practical without a newer app version; optionally put `"tempo": 400` on a song in `preferences.json` in the
Dropbox web UI, change something else on a device and Sync now: the 400 is still in the file afterwards.
