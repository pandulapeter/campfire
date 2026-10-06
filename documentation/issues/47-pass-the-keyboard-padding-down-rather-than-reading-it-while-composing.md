# Pass the keyboard-following padding down rather than reading it while composing, on three screens

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** android, ios, web (where a keyboard slides)
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ImportReportScreen.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SettingsScreen.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SettingsLayout.kt
**Challenged:** amended — the Settings change alone moved the read rather than removing it: every settings page reads
`contentPadding.calculateBottomPadding()` while composing in `SettingsPage` (`SettingsLayout.kt:215-220`), on the narrow
layout too, so that is where the padding has to be passed down; and Settings does see the keyboard slide, under its own
sheet with a field (the library deletion's `DELETE`).

## Problem

The screens' `contentPadding` is `CampfireApp.kt`'s `KeyboardAwarePadding`, which reads the IME inset when asked.
presentation/CLAUDE.md: "the keyboard sliding in lays the lists out again without recomposing the app around them; a
screen narrows it with `PaddingValues.only(…)` rather than by taking it apart while composing, which would bring the
recomposition back." Three places take it apart while composing (8ee010b36):

- `ImportReportScreen.kt:184-185`, `:226`, `:255`:
  ```kotlin
  val bottomPadding = contentPadding.calculateBottomPadding() + FLOATING_BUTTON_CLEARANCE
  val listPadding = PaddingValues(bottom = bottomPadding)
  ...
  modifier = Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding()),
  ...
  .padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
  ```
  The import screen has an always-there search field; tapping it brings the keyboard up, and every frame of the slide
  recomposes the whole screen (the `AnimatedContent`, the `LazyColumn` content lambda, the button).
- `SetlistsScreen.kt:488`, inside each description item:
  `val isCompactHeight = LocalWindowInfo.current.containerDpSize.height - contentPadding.calculateBottomPadding() < SHORT_WINDOW_HEIGHT`
  — every description item recomposes on every frame of the search keyboard.
- `SettingsScreen.kt:297` (wide layout):
  `contentPadding = PaddingValues(end = endPadding, bottom = contentPadding.calculateBottomPadding())`. Settings has no
  text field of its own, but its library deletion sheet has one (`DELETE`), and the keyboard
  it brings up moves the shell's padding under it. Fixing this line alone does not help, though:
  `SettingsLayout.kt:215-220`'s `SettingsPage`, which every settings page goes through on the wide and the narrow
  layout alike, takes the padding apart while composing as well:
  ```kotlin
  .padding(start = contentPadding.calculateStartPadding(layoutDirection), top = PAGE_TOP_PADDING,
      end = contentPadding.calculateEndPadding(layoutDirection), bottom = contentPadding.calculateBottomPadding() + 16.dp)
  ```

## Fix

- ImportReport: `val listPadding = contentPadding.only(bottom = true, extraBottom = FLOATING_BUTTON_CLEARANCE)`
  (`components/PaddingValuesSides.kt`'s `only` keeps the read lazy); the progress box
  `Modifier.fillMaxSize().padding(contentPadding.only(bottom = true))`; the button
  `.padding(contentPadding.only(bottom = true, extraBottom = 16.dp)).padding(end = 16.dp)`. Remove `bottomPadding` if
  nothing else reads it.
- Setlists description: compute the flag in a derived state so the item only recomposes when the threshold is crossed:
  ```kotlin
  val windowInfo = LocalWindowInfo.current
  val isCompactHeight by remember(windowInfo, contentPadding) {
      derivedStateOf { windowInfo.containerDpSize.height - contentPadding.calculateBottomPadding() < SHORT_WINDOW_HEIGHT }
  }
  ```
  (`containerDpSize` is snapshot-backed; check that it is read through state in this Compose version — if it is a plain
  property, key the `remember` on it as well.)
- Settings: `contentPadding = contentPadding.only(bottom = true, extraEnd = endPadding)`; `extraEnd` is added whether or not
  the side is taken (`PaddingValuesSides.calculateRightPadding`), so the end is `endPadding` alone, as today. And in
  `SettingsPage`: `.padding(contentPadding.only(start = true, end = true, bottom = true, extraTop = PAGE_TOP_PADDING,
  extraBottom = 16.dp))`, which is the same four values, read while measuring (`Modifier.padding(PaddingValues)` asks
  its padding in the measure pass). Drop the `layoutDirection` local there if nothing else reads it.

## Tests

None: composition behaviour.

## Manual check

Android, with the layout inspector's recomposition counts (or a temporary `SideEffect` log): open an import's report
screen, tap its search field; the screen's recomposition count no longer climbs with every frame of the keyboard's
slide, and the list's bottom still clears the keyboard and the floating button. Setlists: open search with a setlist
that has a description on a short window; the description still collapses to two lines with the keyboard up.
Settings → Library, open the delete library sheet and tap its field: Settings' recomposition count does not climb with
the keyboard, and the pages still end above the navigation bar / keyboard as before.
