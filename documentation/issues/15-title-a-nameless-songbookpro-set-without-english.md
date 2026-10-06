# Title a SongbookPro set that has neither name nor date without writing English into the setlist

**Kind:** bug · **Severity:** low · **Platforms:** all
**Challenged:** sound
**Files:**
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/backup/SongbookProBackup.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/backup/SongbookProBackupTest.kt`

## Problem

A SongbookPro set without a name and without a readable date is titled in English, and the title is written into
the setlist document, so a Hungarian reader sees "Set 7" in their setlists for good (`SongbookProBackup.kt:83-84` at
1c52e5347):

```kotlin
// SongbookPro names a set by its date as often as not, and a setlist needs a title for its file to be named by.
val title = details.text("name") ?: date ?: details.int("Id")?.let { "Set $it" } ?: "Set"
```

Probed at 1c52e5347: a set with `"name": ""` and no date becomes `Set 7.setlist.json` titled `Set 7`, one without an
`Id` either `Set.setlist.json` titled `Set`. Nothing in the data layer can be localized (it has no string resources,
and the file is the user's own data, synced as written).

Checked and **not** part of this plan: the nameless *song* fallback, `uniqueName(base = title ?: "untitled", …)`
(`SongbookProBackup.kt:72`). The song file gets no `{title}` line in that case, and `"untitled"` only becomes the
batch file name; the import then derives the library name from it exactly as for any other file without a title
(`PrepareImportUseCaseImpl`'s `fallbackTitle` → `SongLocalSource.importFileName` → `songFileName`, whose own empty-name
fallback is `LibraryFiles.FALLBACK_NAME = "untitled"` too). An empty base would end up as `untitled.cho` all the same,
so changing it would change nothing the user sees.

## Fix

Fall back to something that is not a word:

```kotlin
val title = details.text("name") ?: date ?: "#${details.int("Id") ?: (index + 1)}"
```

using the set's position in the `sets` array (`mapIndexedNotNull`) where it has no `Id`, so two such sets are told
apart by their titles rather than only by the batch's ` 2` suffix. `uniqueName` keeps the batch file name unique as
before; the stored file name is normalized from the title by the import (`#7` → `7.setlist.json`), which is fine.
Rewrite the comment above it to say why it is a number: the data layer cannot speak the reader's language, and the
title is written into the file.

Alternative: leave the title empty and let the presentation show a localized placeholder for a setlist without a
title. Rejected: no screen handles an empty setlist title today (the sheet that names a setlist requires one), so it
would mean a new state across the Setlists screen, the setlist header, the export and the search.

## Tests

In `SongbookProBackupTest`: a set `{"details": {"Id": 7, "name": "", "Deleted": 0}, "contents": []}` decodes to a
document titled `#7`; one with no `Id` and no name, second in the array, is titled `#2`; a set with only a date is
still titled by its date (the existing `setsBecomeSetlistsOfTheSongsInOrderWithTheCapoWhereItDiffers` covers that).

## Manual check

None; a set without a name and a date is rare in real backups, and the tests cover the title.
