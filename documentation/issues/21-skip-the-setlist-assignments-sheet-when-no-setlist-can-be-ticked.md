# Skip the Setlist assignments sheet and ask for a new setlist's name when no setlist can be ticked, not only when there are none

**Challenged:** sound

**Kind:** ux  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`,
a new pure helper (recommended: next to `ChecklistOrder` in
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ChecklistOrder.kt`, or a small
`ui/dialogs/SetlistPickerContents.kt`), a new test
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SetlistPickerContentsTest.kt`,
`presentation/CLAUDE.md` (the setlist picker sentences under `ui/dialogs/Dialogs.kt`)

## Problem

`SetlistPicker` (the **Setlist assignments** sheet, opened from a song's menu) documents:

> A library that holds no setlists at all skips the sheet and asks for the name of the first one straight away, since
> a sheet offering nothing to tick is one tap in the way of the only thing that can be done there.

But the decision looks at every setlist, archived ones included:

```kotlin
val isCreatingFirstSetlist = rememberSaveable { setlists.isEmpty() }
var isNamingNewSetlist by rememberSaveable { mutableStateOf(isCreatingFirstSetlist) }
val closeNamingDialog = { if (isCreatingFirstSetlist) viewModel.dismissSheet(dialog) else isNamingNewSetlist = false }
if (!isCreatingFirstSetlist) {
    CampfireBottomSheet( … ) { … }
}
```

while the rows the sheet lists leave archived setlists out unless the song is in them (`setlistOrder.heldKeys` starts
as the checked keys, `ChecklistOrder(checkedKeys)`):

```kotlin
val pickableSetlists = remember(setlists, setlistOrder) {
    setlists.filter { setlist -> !setlist.isArchived || setlist.fileName in setlistOrder.heldKeys }
}
```

So in a library whose setlists are all archived (the natural state after a season of gigs has been put away), choosing
**Setlist assignments** on a song that is in none of them opens a sheet holding a search field and a single "New
setlist" row — exactly the "sheet offering nothing to tick" the KDoc says is skipped. The user has to tap "New setlist"
to reach the only thing they can do.

A song that *is* in an archived setlist still gets the sheet, which then shows that setlist checked and disabled; that
is information worth showing and should stay as it is.

## Fix

Decide on what the sheet would list as it opens, not on the raw list:

1. Add a pure function, e.g.

   ```kotlin
   /** Whether the setlist picker for [songFileName] would list any setlist at all as it opens: an archived one only counts while it holds the song. */
   internal fun hasListableSetlist(setlists: List<Setlist>, songFileName: String) =
       setlists.any { setlist -> !setlist.isArchived || setlist.entries.any { it.songFileName == songFileName } }
   ```

   (It mirrors `pickableSetlists` at the moment the sheet opens, when `heldKeys` is exactly the checked keys.) Use the
   model type `viewModel.setlists` holds (check its element type in `CampfireViewModel`).

2. In `SetlistPicker`:

   ```kotlin
   val isCreatingFirstSetlist = rememberSaveable { !hasListableSetlist(setlists, dialog.song.fileName) }
   ```

   Keep it `rememberSaveable` and decided once, for the reason the KDoc gives (creating the setlist fills the list, and
   the sheet must not slide in behind a naming sheet on its way out). Consider renaming it to
   `isSkippingToNewSetlist`, since it is no longer only about the first setlist; `closeNamingDialog` keeps working
   unchanged (closing the naming sheet dismisses the whole picker, as nothing is behind it).

3. Update the KDoc paragraph ("A library that holds no setlists at all skips the sheet …") to say "a library with no
   setlist the song could be put in — none at all, or only archived ones it is not in — skips the sheet …", and add the
   same to the setlist picker's sentences in `presentation/CLAUDE.md` if they describe the skip (search for "setlist
   picker" there; today they do not mention it, so a one-clause addition is enough).

No strings change.

## Tests

`SetlistPickerContentsTest` (commonTest, run with `./gradlew :presentation:desktopTest`):

- no setlists → `false`;
- only archived setlists, the song in none of them → `false`;
- only archived setlists, the song in one → `true`;
- one unarchived setlist without the song → `true`.

## Manual check

In a library with two setlists, archive both (Setlists screen → each setlist's menu → **Archive setlist**). On the Songs screen,
open a song card's menu → **Setlist assignments** for a song in neither: the "New setlist" naming sheet opens directly;
closing it returns to the Songs screen with nothing behind it; creating one puts the song in it. Then do the same for a
song that is in one of the archived setlists: the assignments sheet opens as before, showing that setlist checked and
disabled.
