# Stop the song details screen from following the keyboard of a dialog opened over it

**Kind:** ui-performance  ·  **Severity:** medium  ·  **Platforms:** ios (android wherever the activity window receives a dialog's IME insets)
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt,
presentation/CLAUDE.md

## Problem
The song details screen is handed the keyboard-aware padding (`songDetailsContentPadding`, CampfireApp.kt:545-552,
a `KeyboardAwarePadding` whose `calculateBottomPadding()` reads the animated `WindowInsets.ime`, 417), and it takes the
bottom apart **while composing**, which `PaddingValuesSides`' KDoc (components/PaddingValuesSides.kt:18-25) names as
exactly what makes a screen recompose on every frame the keyboard moves:

- `SongDetailsPage`, SongDetailsScreen.kt:680 — `val bottomPadding = contentPadding.calculateBottomPadding() + 32.dp`,
  which also feeds `availableHeight = maxHeight - topPadding - bottomPadding` (702), the height `SongSectionsLayout`
  picks the column count by.
- `SongPagerControls`, SongDetailsScreen.kt:526 — `padding(bottom = contentPadding.calculateBottomPadding())`.

Nothing on the details screen is typed into, but three dialogs opened from its header open with the caret in a field
and bring the keyboard up on a touch screen: "Add tag", "Add link" and the language picker
(`rememberFirstFieldFocusRequester`, Dialogs.kt:705-710). On iOS the Compose dialogs share the scene's insets, so while
such a dialog is up:

1. the page on screen (and the two composed beside it) recomposes on every frame of the keyboard's slide in and out;
2. its `availableHeight` shrinks by the keyboard's height, `SectionGridKey.availableHeight` changes on every frame
   and the grid is searched again (SongLyrics.kt:1296-1352); on an iPad a song that fitted in two columns no longer
   does and **the lyrics behind the dialog reflow into more columns** — and since `rememberContinuousChange` only
   watches the width and the text size (SongDetailsScreen.kt:689), the sections spring there with `animateBounds`,
   then spring back when the dialog closes;
3. for a song read from a setlist, the pager bar jumps up above the keyboard behind the dialog.

The cost is paid twice per dialog (keyboard in, keyboard out), and the reflow is visible behind the scrim.

## Fix
The details screen has nothing to keep above a keyboard, so give it a padding that does not follow one:

1. In `CampfireContent` (CampfireApp.kt) keep the keyboard-aware padding for the editor only (rename it
   `songEditorContentPadding`) and hand `SongDetailsScreen` a plain padding of the system bars:
   `PaddingValues(start = systemBars.calculateStartPadding(layoutDirection), end = systemBars.calculateEndPadding(layoutDirection), bottom = systemBars.calculateBottomPadding())`
   (the values `songDetailsContentPadding` is built from today, minus the IME).
2. Nothing in `SongDetailsScreen.kt` has to change for correctness once it no longer reads the IME; optionally make
   `SongPagerControls` use `Modifier.padding(contentPadding.only(start = true, end = true, bottom = true))`-style
   layout-time reads for consistency.
3. presentation/CLAUDE.md, `ui/screens/` bullet: "every screen gets a `contentPadding` … That padding follows the
   keyboard" — say that the song details screen's does not, since nothing on it is typed into (and why: a keyboard
   brought up by a dialog would otherwise reflow the lyrics behind it).

## Verification
Manual, iPad simulator (landscape, a song that fills about two columns): open the song from the library, tap
"Add tag". Before: the lyrics behind the dialog reflow as the keyboard comes up and back as it goes. After: they stay
put. Repeat from a setlist: the pager bar stays at the bottom. On Android, check the same with the emulator's soft
keyboard; nothing should change on a phone in portrait (a single column).

## Conflicts
CampfireApp.kt (the padding block around 530-563) may be touched by other presentation lanes; plan 18 touches the
editor's use of the same padding but not CampfireApp.kt.
