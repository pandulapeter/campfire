<!--
This file is part of Campfire.
Copyright (c) Pandula Péter 2017-2026.
https://github.com/pandulapeter/campfire

This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
https://mozilla.org/MPL/2.0/.
-->

# Give each page of the Preferences sheet its own scroll position

**Challenged:** amended — the per-page states are `rememberScrollState()`s (saveable, as the one shared state is today)
rather than a `remember`ed map of `ScrollState(0)`, which would lose the offset on an Android rotation; the modifier
order and the checks on the sheet's measurement, the wheel guard, and the edge fade are spelled out.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/songDisplay/PreferencesSheet.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/preferences/PreferencesContent.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/preferences/CLAUDE.md` (if it names the scroll),
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/` (a Preferences sheet UI test, if one exists, else a new one beside it)

## Problem

`PreferencesSheet.kt` keeps one scroll state for all four pages:

```kotlin
val scrollState = rememberScrollState()
...
PreferencesContent(
    modifier = Modifier
        .fadingVerticalEdges(scrollState, backgroundColor)
        .bounceVerticalScroll(scrollState)
        .padding(bottom = 8.dp),
```

and `PreferencesContent` swaps the pages inside that one scroll with `AnimatedContent`. `onPageSelected`'s else branch
(`selectedPage = page`) never resets it. On a 360×640 phone: open Preferences from song details, scroll to the bottom
of the long Chords page, press General or Metronome: the new page appears at the old offset (clamped), its middle or
end rather than its top. The page buttons are pinned (07d146e3e) precisely so another page is picked from anywhere down
this one, so this is the common case. Settings' tabs keep a scroll position each.

## Fix

Move the scroll inside each page. `PreferencesContent` is used only by `PreferencesSheet` (Settings' tabs compose the
page components themselves), so its signature may change freely:

- The sheet owns one state per page: `val scrollStates = PreferencesPage.entries.associateWith { rememberScrollState() }`
  (a fixed number of calls in a fixed order, so it is legal in composition, and each is saveable as today's single
  state is). The repeat-press branch becomes `scrollStates.getValue(page).animateScrollTo(0)`.
- `PreferencesContent` takes `scrollStates: Map<PreferencesPage, ScrollState>` and `fadeColor` (or reads
  `sheetContainerColor()` itself) instead of an outer scroll modifier; inside `AnimatedContent`'s content, the page's
  `Column` gets `Modifier.fillMaxWidth().fadingVerticalEdges(state, backgroundColor).bounceVerticalScroll(state).padding(bottom = 8.dp)`
  in that order (the bottom padding stays inside the scroll, as now), `state = scrollStates.getValue(page)`.

Each page then opens at its own last offset (top the first time), and the outgoing page slides away where it was. Do
not reset a shared state with `scrollTo(0)` on change (the outgoing page would jump mid-slide).

Checked, no change needed:
- Height: `AnimatedContent` now gets the bounded height of `PreferencesFrame`'s `Box(weight(1f, fill = false))` instead
  of the scroll's infinite one, and each page's `verticalScroll` reports `min(content, max)`, the same height the outer
  scroll reported; `SizeTransform { _, _ -> snap() }` keeps the size the target page's at once, so
  `animateSheetContentHeight` and `stayOnSheetBottom` see the same sizes as before. Keep the snap and its default clip
  (the outgoing taller page is clipped as now).
- `keepSheetFromWheelScrolls` (2795c968c) sits on the sheet's outer `Column`, an ancestor of the new scroll containers
  as of the old one, so it stays their nested scroll parent.
- `fadingVerticalEdges` per page means each sliding page carries its own fade, right for both during the transition.
- Nothing is typed into the Preferences (`ui/preferences/CLAUDE.md`), so the short-window keyboard column does not apply.

`ui/preferences/CLAUDE.md`'s layout bullet may add "each page keeps its own scroll position, as Settings' tabs do".

## Tests

A UI test in `tools/screenshots`: open the Preferences sheet on a 360×640 window, scroll the Chords page to its end,
select General, assert General's first row is displayed; then select Chords again and assert it is still scrolled
(its first row not displayed). See it fail without the fix (lane report only).
