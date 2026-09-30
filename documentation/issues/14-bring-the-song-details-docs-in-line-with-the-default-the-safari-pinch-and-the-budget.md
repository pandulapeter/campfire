# Bring the song details docs in line with the one-row default, the Safari pinch and the layout budget

**Decided (D3, 2026-09-30): the sentence, not the listener.**

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/CLAUDE.md` (lines 19 and 116), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
(the `magnifySongText` KDoc), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/LayoutBudget.kt` (KDoc)

## Problem

Three sentences say what the code does not do (at 9ab7ca54e).

1. `presentation/CLAUDE.md` line 116: "Where Settings' "One row at a time" (`UserPreferences.isOneRowAtATimeEnabled`,
   off by default) is on" — `9ab7ca54e` made it on by default (`UserPreferencesDocument.kt` line 25, and the
   `UserPreferences` KDoc already says so).
2. The `magnifySongText` KDoc in `CampfireViewModel.kt`:

   ```kotlin
    * farther apart the fingers are than at the last report, damped by the same [PINCH_SENSITIVITY]. Only a platform
    * that tells a touchpad pinch apart from a scroll calls it - the macOS desktop app and Safari, which report the
    * gesture itself, and the other browsers, which report a Ctrl + scroll the user is not holding Ctrl for - and it
   ```

   and `presentation/CLAUDE.md` line 19 ("Every browser also makes a touchpad pinch into a Ctrl + wheel"). Safari on
   macOS reports a trackpad pinch as `gesturestart` / `gesturechange` events, not as Ctrl + wheel, and nothing in
   `CampfireWebApp.kt` or `app/web` listens for them (`grep -rn "gesturechange\|gesturestart\|GestureEvent"` finds
   nothing), so in Safari a pinch zooms the whole page. An earlier revision of the comment said Safari "is left to do
   so"; this one dropped that without adding the listener.
3. `LayoutBudget` (3 000 lines or 200 000 characters) is the one place where "every line of any song is reachable
   with the pedal" is not true: past it, `CutSongNotice` is shown and the rest is only in the editor, which
   performance mode removes. It is a conscious trade (the eighth review's plan 06, a 60 000-line file froze a desktop
   for seconds), several times any real song — but nothing in the docs says it bounds the pedal guarantee.

## Fix

1. Line 116: "on by default".
2. Rewrite the KDoc sentence: "the macOS desktop app, which reports the gesture itself, and Chrome, Edge and Firefox,
   which report a Ctrl + scroll the user is not holding Ctrl for; Safari reports a gesture of its own that nothing
   listens for, so there a pinch zooms the page" — and line 19 the same way ("Every browser but Safari …").
   **Alternative, if the user would rather have the feature than the sentence:** a non-passive `gesturestart` /
   `gesturechange` listener in `CampfireWebApp.kt` next to the wheel one, calling `magnifySongText(event.scale / previousScale)`
   and preventing the default while `isSongTextZoomable`, with the ratio step pulled into a pure
   `internal fun scaleRatio(previous: Float, current: Float)` (first event 1, zero guarded) and a test for it. Recommended:
   the sentence now, the listener when a Safari user asks — the app has no Safari-specific code today.
3. Add to the `LayoutBudget` KDoc: "It is also the one bound on the song details screen's promise that every line is
   reachable by stepping: past it the notice stands in for the rest." And a clause in `presentation/CLAUDE.md`'s
   `SongLyrics.kt` paragraph where the budget is mentioned.

## Tests

None; documentation only.

## Manual check

None.
