# Read a missing, unreadable or newer-version `preferences.json` as "unchanged in the cloud" instead of "everything removed"

**Kind:** bug  ·  **Severity:** high  ·  **Platforms:** all
**Challenged:** amended — the newer-format path returns `previous ?: JsonObject(emptyMap())`, since with no base yet a bare `previous` is null and would report every run as failed; the trade-off of that path is stated.
**Files:** `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncedPreferencesSync.kt`,
`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncedPreferences.kt`,
`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncedPreferencesTest.kt`,
`data/repository/implementation/CLAUDE.md`, root `CLAUDE.md` (Sync section, the "library's per-song overrides travel too" bullet)

Lane B, first of 10–15: apply before 11, 12, 13, 14 and 15, which edit the same two files.

## Problem

Once a device has synced the document once (the index holds a base), any of these wipes every library
transposition, tempo and capo on that device, then on every other one:

- `preferences.json` deleted from the Dropbox folder by hand,
- the file edited by hand into something that does not parse (a trailing comma) or is not an object,
- a document that parses but whose `songs` is missing or not an object (`{"version":1,"songs":[]}`).

`SyncedPreferencesSync.synchronize`:

```kotlin
val remote = provider.downloadDocument(SyncedPreferencesDocument.FILE_NAME)
// A document that is not one is replaced, as if there were none: nothing this version reads is in it.
val remoteDocument = remote?.bytes?.let(SyncedPreferencesDocument::decode)
...
SyncedPreferencesDocument.merge(base = previous, local = SyncedPreferencesDocument.localDocument(base = previous, preferences = snapshot), remote = remoteDocument)
```

With no local change since the last run, `local == base`, and `SyncedPreferences.kt`'s

```kotlin
private fun mergeValue(base: JsonElement?, local: JsonElement?, remote: JsonElement?): JsonElement? = when {
    local == remote -> local
    local == base -> remote
```

returns `remote` — null — so `merge` falls back to `JsonObject(emptyMap())`. `preferencesOf({})` is empty, `applyTo`
removes every override here, and `{}` (not even a `version`) is uploaded; every other device then merges a remote
that removed everything against an unchanged local and removes them too. With `songs: []` the same happens one level
down (`local songs == base songs -> remote songs`, an array `preferencesOf` reads as no songs); there the merged
document equals the remote one, so nothing is uploaded, but the local wipe still happens. Proven with a probe test
at ed4a1a5ce: base `{a.cho:{capo:2}}`, preferences `capo a.cho=2`, remote malformed / missing / `songs:[]` → capos
`{}` afterwards in all three cases.

The comment and the KDoc of `decode` ("which the run then replaces as if there were none") state the intent —
replace the bad document — but "as if there were none" against a non-null base is a removal of everything.

Related: `FORMAT_VERSION` (`private const val FORMAT_VERSION = 1`) is only ever written (`localDocument`), never
compared. The pass-through design already handles a later version adding fields, so a later version only bumps
`version` for a change this one cannot pass through safely (a renamed field, a different unit). Today such a
document is merged as if it were version 1, its unknown-to-us songs' fields applied or stripped by this version's
rules, and written back.

## Fix

In `SyncedPreferencesSync.synchronize`, decide what the remote document is before merging:

1. **Missing** (`remote == null`) or **not a document this version can read** — `decode` returned null, or the
   object has no `songs` that is a `JsonObject` — merge against `remote = previous` (the cloud folder is
   taken as unchanged since the last run). `merge(previous, local, previous)` is `local`, so this device's values
   stay and are uploaded, replacing the bad file (with `expectedRevision = remote?.revision`, as now). With
   `previous == null` this is `merge(null, local, null)` = `local`, the same as today's first run.
   Every document this version writes has a `songs` object (`localDocument` always adds one), so a missing `songs`
   counts as unreadable too — which also repairs the bare `{}` this bug has already uploaded for some users. Put the
   check in `SyncedPreferencesDocument` as `fun isReadable(document: JsonObject) = document[SONGS] is JsonObject`, so
   it is unit-testable; leave `decode` returning any JSON object, because step 2 must see a newer document whatever
   shape it has.
2. **Newer format** — checked first, on the decoded object, before step 1's `isReadable`: the document's `version`
   is a number (a non-string `JsonPrimitive` whose `intOrNull` is set) greater than `FORMAT_VERSION`: apply nothing,
   upload nothing, write nothing into the preferences (so `writtenBySync` is not touched), and
   `return previous ?: JsonObject(emptyMap())` — not null: null reports `havePreferencesFailed`, whose message says
   "Campfire will try again next time", which no retry can make true here, and it would keep every run from counting
   as successful until the app is updated. A bare `return previous` is null on the first run with this account
   (no base yet), hence the empty object, which every later run treats exactly as a null base (it names no song, so
   the merge only adds) and which this path keeps returning unchanged. The accepted trade-off: while the folder holds
   a newer format the overrides do not travel to or from this device at all, and the run still counts as successful;
   the newer version is the one that bumped the format, so it is the one that has to keep this one's values. `println`
   that the document was written by a newer version. Expose `FORMAT_VERSION` (internal) or add
   `fun isNewerFormat(document: JsonObject)` to the document object.
3. In the `Conflict` branch, `previous = remoteDocument ?: JsonObject(emptyMap())` must not turn an unreadable
   remote into an empty base: use the same "effective remote" from step 1, i.e. `previous = effectiveRemote` where
   `effectiveRemote` is `remoteDocument` when readable and `previous` otherwise.
4. Keep `if (merged == remoteDocument) return merged` comparing against the real decoded document, so an unreadable
   or missing one is always overwritten.

Update the comment above `remoteDocument` and `decode`'s KDoc ("replaced, keeping this device's values, as if the
folder had not changed it"), `data/repository/implementation/CLAUDE.md` (the `preferences.json` paragraph: a missing
or unreadable document is replaced with this device's values, never read as a removal; a newer format is left alone),
and the root `CLAUDE.md` Sync bullet the same way, in a clause.

## Tests

In `SyncedPreferencesTest` ("The run's step"), each with `base = document("""{"a.cho":{"capo":2}}""")`, preferences
`capos = mapOf("a.cho" to 2)`, `library("a.cho")`:

- `a document deleted from the folder removes nothing and is written again` — no remote document → capos still
  `{a.cho=2}`, and `remoteDocumentOf(provider)` reads back `capos {a.cho=2}`.
- `a document that does not parse removes nothing and is replaced` — remote bytes
  `{"version":1,"songs":{"a.cho":{"capo":2},}}` → same expectations.
- `a document whose songs are not an object removes nothing` — `{"version":1,"songs":[]}` → same.
- `a document of a newer format is left alone` — remote `{"version":2,"songs":{}}` with revision `r1` → capos still
  `{a.cho=2}`, the stored document's revision still `r1` (set `provider.onUploadDocument` to throw), and the return
  value equals the base.
- `a document of a newer format with no base yet is not a failure` — same remote, `base = null` → the return value is
  not null (an empty object) and nothing is uploaded.

## Manual check

Two devices connected to one Dropbox account, a library song with a transposition set from the library on both.
Delete `Apps/Campfire Sync/preferences.json` in the Dropbox web UI, Sync now on one device: the transposition is still
there and the file reappears. Repeat with the file edited to add a trailing comma. Then edit `version` to 2: Sync now
leaves the file untouched and the transposition in place.
