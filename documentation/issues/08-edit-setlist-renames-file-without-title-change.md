# Only move a setlist's file when its title changed, not on every edit

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SetlistLocalSourceImpl.kt, data/source/local/implementation/src/desktopTest (or commonTest) for a test, data/source/local/implementation/CLAUDE.md

## Problem
`SetlistLocalSourceImpl.renameSetlist` decides whether to move the file only by comparing the current file name with
the name the (possibly unchanged) title normalizes to:

```kotlin
// SetlistLocalSourceImpl.kt:103-116
val desired = setlistFileName(title)
if (setlist.fileName.isNamed(desired)) {
    return saveSetlist(renamed)
}
val fileName = fileStorage.uniqueName(StorageDirectory.SETLISTS, desired, currentName = setlist.fileName)
fileStorage.moveFile(...)
```

`EditSetlistUseCaseImpl` calls it for every save of the edit dialog (`CampfireViewModel.editSetlist`, line 2216),
including one that only changes the description, or one where nothing changed at all. So a setlist whose file is not
named by today's rule — `My Set.setlist.json` written by hand, by an older version, or arriving through sync from
one — is moved to `my_set.setlist.json` the first time its description is edited or its edit dialog is saved
untouched.

Root CLAUDE.md says the opposite: "Nothing is migrated … keeps its spelling until **Update file name** (or, for a
setlist, a new title) moves it for a reason that is part of the name." The move is not free either: it reaches sync
as a deletion plus a new file on every device (root CLAUDE.md, "A rename reaches sync as a deletion and a new file"),
and the web address `setlist/{setlist}/…` of an open setlist changes under the user.

## Fix
1. In `renameSetlist`, move only when the title's own name changed:
   ```kotlin
   val desired = setlistFileName(title)
   val isTitleNameUnchanged = setlistFileName(setlist.title) == desired
   if (isTitleNameUnchanged || setlist.fileName.isNamed(desired)) return saveSetlist(renamed)
   ```
   (`setlist` here is the one `SetlistRepositoryImpl.latest` read from the file, so `setlist.title` is the stored
   title.) A title change that normalizes to the same name — capitals, punctuation — still moves nothing, as today.
2. Update the KDoc of `renameSetlist` and the `EditSetlistUseCase` line in `domain/implementation/CLAUDE.md` /
   the "A setlist's file follows its title" sentence in root CLAUDE.md only if the wording needs "when the title
   changes" made explicit.
3. Test (desktopTest in `:data:source:local:implementation`, over `JvmFileStorage` on a temp dir like the existing
   storage tests): write `My Set.setlist.json` with title "My Set"; `renameSetlist(setlist, "My Set")` with a new
   description keeps the file name and writes the description; `renameSetlist(setlist, "Other")` moves it to
   `other.setlist.json`.

## Verification
`./gradlew :data:source:local:implementation:desktopTest`. Manual (desktop): put `My Set.setlist.json` into
`library/setlists`, rescan, edit only its description, save — the file keeps its name.

## Conflicts
none known
