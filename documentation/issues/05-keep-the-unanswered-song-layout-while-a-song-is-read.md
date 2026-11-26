# Keep the song layout as it is while a song is open, fitting the unanswered layout to the window only elsewhere

**Challenged:** amended — "a song is open" means song details anywhere in the back stack, not on its top (the editor
over a song would otherwise refit the song under it), and fitting resumes only once a pop has settled (else the leaving
card is laid out again as it slides away); the plan now says that an unconfirmed pick lasts while the song is open and is
refitted after, and that a song opened at launch is not fitted.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/preferences/FitUnchosenSongLayoutToWindow.kt`,
its one caller `ui/CampfireContent.kt` (line 94), `ui/dialogs/songDisplay/SongLayoutSheet.kt` (KDoc),
`ui/screens/songDetails/autoScroll/CLAUDE.md` (lines 60–63, where the rule is described), a UI test under
`tools/screenshots/src/test/.../presentation/ui/`

## Problem

`79d5a9f67` (after 5.6.0) keeps the layout picked as the one suiting the window's shape until the layout question is
confirmed:

```kotlin
val isOwed = userPreferences?.isSongLayoutChosen == false && visibleDialog != DialogType.SongLayout
LaunchedEffect(isOwed, fitting) {
    if (isOwed && current != fitting) preferencesController.pickSongLayout(fitting)
}
```

Only Confirm answers the question (`SongLayoutSheet` → `markSongLayoutChosen`); Back or Escape leaves it owed. So
(verified at `0feb3925f`):

1. A reader opens a song, picks Pages in the question on an upright phone, and puts the sheet away with Back: `isOwed`
   turns true and the effect writes the scrolling column back at once, taking back the pick under the song.
2. With the question put away, turning the phone flips the open song between one scrolling column and pages (stopping
   a playing scroll); dragging a desktop window's edge across square flips it and writes `preferences.json` each time.

The question is asked only from `SongDetailsScreen` (`AskForSongLayoutOnce`, not in an import preview), and
`SongLayoutSheet` is the only place showing `DialogType.SongLayout`.

## Fix

Fit only while no song is open, and not while a navigation transition runs.

- In `CampfireContent`, derive `isSongOpen` as `backStack.any { it is CampfireDestination.SongDetails }` (a
  `derivedStateOf` keyed on `backStack`, like `isTopLevelScreenCovered`, so a push does not compose the root again).
  **Any**, not the top: the editor always opens over its song's details (`SongEditorEntries`), and a fit written under it
  would change the song the reader returns to. Import previews are `SongDetails` too; skipping there is harmless.
- Hand `FitUnchosenSongLayoutToWindow` that state and `isNavigationTransitionRunning` (the `MutableState` already in
  `CampfireContent`) as `State`s, read only inside it, so the root is not composed again by either. Fit while
  `isOwed && !isSongOpen && !isTransitionRunning`. Without the transition guard, a pop removes the entry at its start
  and the fit would lay the leaving card out again as it slides away.
- Pushing a song: the stack and `isSongOpen` change in one snapshot, so no write happens after the push; the song shows
  the fit written while the list was on screen (at launch or the last window change), and the question opens with it
  picked.
- Accepted consequences, to be written into the KDoc and the `autoScroll/CLAUDE.md` bullet:
  - A pick put away without Confirm lasts while its song is open; once the song is closed the layout is fitted again,
    and the next song asks again with the fitted one picked.
  - A song open from launch (a restored back stack, a widget, a web address, an opened file) shows the stored layout
    unfitted until the reader leaves it; the question still asks over it. (Today's fit lands a frame or more after
    that song's first frame anyway, so this removes a flip rather than losing a fitted first frame.)
- Replace the KDoc's "never while the question is shown" with "never while a song is open"; the `visibleDialog` input
  and `dialogHost` parameter then go (the question is only shown over a song).

**Drop this plan if** the owner prefers the pick to be taken back on Back (the question is asked again on the next song).

## Tests

`songLayoutFittingWindow` stays pure; the new condition is in the Composable. A UI test in `tools/screenshots` (the
seeding in `AppTest` writes `isSongLayoutChosen = true`, so seed it false): in a portrait window open a song, pick Pages
in the question, press Back, resize to landscape and back: the layout stays Pages. Then close the song: it is the
scrolling column again (refitted).
