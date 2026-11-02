# Gate setlist documents on their format version, and write the add-only field rule down with its fixture

**Kind:** data safety / process · **Severity:** medium · **Platforms:** all · **Priority:** P1 · **Effort:** S
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/model/SetlistDocumentFormat.kt`,
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/model/SetlistDocumentFormatTest.kt`,
`data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SetlistLocalSourceTest.kt`,
`data/source/local/implementation/CLAUDE.md` (Documents section), root `CLAUDE.md` (Conventions),
`data/sync/implementation/CLAUDE.md` (the upgrade-5.2.0 paragraph)

**Challenged:** amended — step 3's release-time fixture step in `prepare-release` cut (process, judged by a release,
not by the change): the fixture now comes with the change that alters a stored document, written by the code before it,
as one clause of step 2's rule. Steps 1–2 sound.

## Problem

A setlist file is read by other versions of the app than the one that wrote it: an export opened elsewhere, a shared
link, and every sync run between devices on different versions. Its format is lenient towards the past and blind to
the future:

- `SetlistDocumentFormat`'s `Json { ignoreUnknownKeys = true; coerceInputValues = true }`, every field defaulted, and
  unknown members kept in `unknownFields` and written back. An *added* field therefore survives an older version
  editing the setlist. A *renamed* field does not: the older version keeps the new name as an unknown field but never
  sees the value under the name it knows. A *retyped* field is worse: `tempo`, `capo`, and `date` have serializers
  that read an unexpected value as null (`OptionalTextSerializer`, "an optional field must not make the setlist
  unreadable"), and the null is left out on the next write, so an older device editing the setlist deletes the newer
  value and sync carries the deletion to every device; any other retyped field fails the whole decode, and the
  setlist vanishes from that device's list.
- Synced preferences guard against exactly this (`SyncedPreferencesDocument.isNewerFormat`: "it only bumps [VERSION] for
  a change that cannot be passed through - which this version must not merge or write over"). Setlists have no such
  key, and the guard has to be in the versions already installed before a later version can rely on it: it cannot be
  added afterwards.
- Upgrades are checked only against 5.2.0's files (`UpgradeFrom520Test`, `SyncPlannerUpgradeFrom520Test`,
  `SharedLinksUpgradeFrom520Test`, fixtures in `data/source/local/implementation/src/desktopTest/resources/upgrade-5.2.0`
  and `data/sync/implementation/src/desktopTest/resources/upgrade-5.2.0`, added 2026-10-29). 5.2.1 and 5.2.2 shipped
  without; `prepare-release` does not mention fixtures, and `data/sync/implementation/CLAUDE.md` leaves it to judgement
  ("A release whose files are worth checking the same way gets a folder of its own, written from a worktree at its
  tag.").

## Fix

1. **Read gate.** In `SetlistDocumentFormat.decode`, after parsing and before `decodeFromJsonElement`, read a top-level
   `"version"` (the name synced preferences use): a JSON number greater than `FORMAT_VERSION = 1` throws
   `IllegalArgumentException("The setlist was written by a newer version of Campfire.")`; missing, non-numeric, or ≤ 1
   is today's format. Nothing writes the key now (files stay byte-identical; a future version that bumps it is the one
   that writes it, and an older version that does read such a file never edits it). The key stays in `unknownFields`
   like any member this version does not declare. Every caller already treats a decode failure as "skipped and left
   alone": `SetlistLocalSourceImpl.loadSetlists` logs and skips it, `parseSetlist` (import) answers null, so the import
   reports it unreadable, `SetlistComparisonImpl.decodedOrNull` answers null, and `SetlistRepositoryImpl.latest` falls
   back on the cache, which never held it. Check `deleteAllSetlists` and the import's replace path still treat such a
   file as they treat a hand-broken one today, and say in the KDoc that the gate only ever hides a file, never writes it.
2. **The rule, written once.** Root `CLAUDE.md`, Conventions (under "Files and names" or a new "Stored formats" line):
   "A stored document (setlist, preferences, synced preferences, sync index, shared links, credentials) only gains
   fields: a rename or retype is a new key, the old one still read; a change older versions cannot pass through bumps
   the document's `version`, which they refuse. A change to one comes with an `upgrade-<version>` fixture of the file as
   the code before it writes it (from a worktree at the last tag, never regenerated) and a test reading it." Point the Documents section of
   `data/source/local/implementation/CLAUDE.md` at it in one clause (it already says "Every field is defaulted").
3. In `data/sync/implementation/CLAUDE.md`, replace "A release whose files are worth checking the same way gets a folder
   of its own, written from a worktree at its tag." with a pointer to the root rule.

## Tests

In `SetlistDocumentFormatTest`: a document with `"version": 2` fails to decode; `"version": 1`, a missing
`version`, and `"version": "2"` decode as today; a decoded version-1 document with `"version": 1` written back keeps it
(it is an unknown member). In `SetlistLocalSourceTest`: a library holding one `"version": 2`
file lists the other setlists, skips that one, and leaves its bytes untouched. The fixture clause is exercised by the next
change to a stored document.
