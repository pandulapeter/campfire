# Replace the Linux-only golden renders with assertions that run on every OS

**Kind:** testing / structure · **Severity:** medium · **Platforms:** all (the tests run on the desktop JVM) · **Priority:** P1 · **Effort:** L
**Files:** `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/screenshots/GoldenRendersTest.kt` (deleted),
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/screenshots/Goldens.kt` (deleted),
`tools/screenshots/src/test/resources/goldens/*.png` (17, deleted), `tools/screenshots/build.gradle.kts`,
`gradle.properties`, `.github/workflows/tests.yml`, `tools/screenshots/CLAUDE.md`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/screenshots/TextFit.kt`,
`…/presentation/ui/dialogs/sheet/SheetKeyboardBudgetTest.kt`, `…/presentation/ui/chrome/ToolsItemTest.kt`,
new `…/presentation/ui/screens/songs/SongCardFitTest.kt`, new `…/presentation/ui/screens/songDetails/SongDetailsFitTest.kt`,
new `…/presentation/ui/screens/songEditor/EditorRoomTest.kt`, new `…/presentation/ui/theme/DarkThemeContrastTest.kt`,
new `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/screenshots/Contrast.kt`
(all `…` = `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire`)

**Challenged:** amended — `captureWindow()` is kept and used by the contrast test (`onRoot()` misses a sheet's dialog
layer and fails with several roots); step 1 reduced to the goldens' own case (New song) and drops `SheetCase.knownFailure`;
step 4(a) and step 5 made concrete for screens without a bottom bar and for the golden's no-keyboard state; shared-file
notes for plans 12 and 20.

Owner decision (2026-11-02): the golden-image test is replaced, not repaired.

## Problem

`Goldens.kt` compares only on Linux and refuses to record anywhere else:

```kotlin
private val isComparing = System.getProperty("os.name").startsWith("Linux")

init {
    check(!isUpdating || isComparing) { "The goldens are recorded on Linux; on another system, take them from a CI run's goldens/actual." }
```

So the owner, who works on a Mac, learns of a golden failure only from CI, and fixes it by copying a CI artifact over
`src/test/resources/goldens`. 24 commits since 2026-10-14 touched the 17 PNGs, nearly all of them only to re-record an
intended change (`1effef75b Update the onboarding golden render for the removed song layout step.`, `e30c3578a Update
the song details goldens for chord degrees being off by default.`, …). Of the last 10 failed `tests.yml` runs, 4 failed
on `GoldenRendersTest` (41302048219, 41300487092, 40946591811, 40774567389; 3 others have no log left). A golden says only
that pixels moved, never what broke, and every intended UI change costs a round trip through CI.

Each case already states the bug it would have caught (`GoldenCase.reason`). Those reasons are assertions waiting to be
written; several already are:

| Golden case | Reason (quoted) | Covered today by |
|---|---|---|
| `new-song-keyboard` | "A sheet's fields left under the keyboard…" | `SheetKeyboardBudgetTest` ("New song") |
| `song-metadata-keyboard` | "The Edit song details form's Save and fields kept above the keyboard…" | `SheetKeyboardBudgetTest` ("Edit song details") |
| `setlist-details-keyboard` | "The setlist sheet's title field under the keyboard on the smallest phone." | `SheetKeyboardBudgetTest` ("Edit setlist") |
| `cover-search-keyboard` | "The cover search's field and results under the keyboard…" | `SheetKeyboardBudgetTest` ("Cover search") |
| `onboarding-last-page` | "Start playing broken onto two lines, then the last dot touching it…" | `OnboardingFooterTest` |
| `new-song-keyboard-hungarian-130/-200` | "The header's Create pushed off the sheet…" / "A pinned header taller than the room the keyboard leaves…" | nothing (only the default appearance is checked) |
| `songs-rail` | "Settings out of reach on a phone on its side…" | `ToolsItemTest` checks a 800 × 400/480/500 rail, not `PHONE_ON_ITS_SIDE` |
| `songs` | "Cards whose title, artist, key, and tags cut each other off at the smallest phone's width." | nothing |
| `song-details` | "The song's controls and chords crowding a phone's first screen of the song." | nothing |
| `song-details-hungarian-130/-200` | "Labels broken in the middle of a word at a large text size…" / "Steppers that no longer fit their labels…" | nothing |
| `editor` | "The editor's controls taking the lines a phone has for the song's text." | nothing |
| `songs-dark`, `song-details-dark`, `editor-dark`, `new-song-keyboard-dark` | a dark-theme color lost or unreadable | nothing |

## Fix

Write the missing assertions first, in the style of `SheetKeyboardBudgetTest` and `OnboardingFooterTest` (semantics
found by text read from resources, bounds in dp at density one, `TextFit.kt`'s `isWhole` / `breaksAWord`, pixels only
where semantics cannot tell), each in the package of the screen it covers, each test's KDoc naming the bug it
guards. Then delete the goldens and everything that exists only for them.

1. **Sheets at large text** — extend `SheetKeyboardBudgetTest`: `SheetCase` gains an `appearance` passed to
   `runAppTest`, and the "New song" case (the goldens' case; not every sheet, each is an app start) is added twice more,
   in `TestAppearance(language = AppLanguage.HUNGARIAN, textScale = 1.3f)` and `textScale = 2f`, each also asserting
   the header's Create label `isWhole` (`textLayout(hasText(string(Res.string.create)) and hasAnyAncestor(isDialog()))`).
   The existing `outsideOf(room, …)` already says "pinned header taller than the room" when the focused field or the
   action ends under the keyboard. Remove the unused `SheetCase.knownFailure` and its skip, so no case can be parked
   (no exception list). If a large-text case fails on today's code, that is a real defect: fix it first in its own
   commit (skill §2), or stop and ask.
2. **Rail on a phone on its side** — add to `ToolsItemTest`: in `TestWindow.PHONE_ON_ITS_SIDE`, Settings is displayed
   and its bounds end inside the window (as the existing large-text case asserts for its rail).
3. **Song cards** (`SongCardFitTest`, `ui/screens/songs`) — at `TestWindow.PHONE`, in every app language
   (`APP_LANGUAGES`) at text scale 1 and 1.3: for each visible card, the bounds (unmerged tree) of its title, artist,
   key, and tag texts lie inside the card's bounds and do not overlap one another. A long title may still ellipsize;
   what the bug was is two pieces drawn over each other.
4. **Song details first screen and labels** (`SongDetailsFitTest`, `ui/screens/songDetails`) — `openSong()` at
   `TestWindow.PHONE`. Amended: the phone's first screen is the controls and the Chords section, and the first lyric
   comes after the Scales, Picking pattern, and Intro sections, so a lyric on it is not what the golden showed. (a) Every
   chord the demo song's file plays (`[Am]`, `[C]`, …) has its diagram row (found by `song_details_chord_diagram`'s
   start) displayed on the first screen, inside the song's viewport and clear of the Next page button, and no text or
   icon of the controls (the smallest node holding the instrument pill, Transpose down, and Show metronome), the Chords
   title, or a row of diagrams overlaps another; (b) in every app language at text scale 1.3 and 2, no text of the
   controls or the Chords title `breaksAWord`, and every one is whole, except the instrument pill's two names, which
   `InstrumentPill` cuts short by design where the line is too narrow for both (Spanish and Italian at 2). A stepper is
   exactly the controls' height, so its value's line height may outgrow it by its undrawn leading; "whole" there
   measures the letters without it. A part cleared of semantics (a stepper's room for its widest value, its silent drawn
   value) is skipped; the field under it says the value. Find the controls by their resource strings, not by typed text.
5. **Editor room** (`EditorRoomTest`, `ui/screens/songEditor`) — `openEditor()` at `TestWindow.PHONE`, first as the
   golden drew it (no keyboard), then with the field focused and `keyboardHeight = KEYBOARD_HEIGHT`: the text field's
   visible bounds (clipped to the window less the keyboard) are at least five of its own text lines tall (line height
   from the field's `textLayout`). Five is the reason's "lines a phone has for the song's text"; say so in the KDoc.
6. **Dark theme** (`DarkThemeContrastTest`, `ui/theme`, helper `screenshots/Contrast.kt`) — in
   `TestAppearance(isDark = true)`, on the songs list, song details (`openSong()`), the editor (`openEditor()`, whose
   demo text has directives, chords, and comments), and the New song sheet: capture `captureWindow()` (`AppTest.kt`, the
   whole scene: `onRoot()` would miss the sheet, a dialog layer of its own, and fails where there are two roots) once
   settled (`waitForIdle`), and for each text node checked (card title, artist, key, tag; lyric line, chord; the editor field; the
   sheet's header and fields) take the node's bounds' most frequent pixel as its background and the pixel farthest from
   it in relative luminance as its ink, and assert a WCAG contrast ratio of at least 3:1. Anti-aliasing differs per OS,
   the extreme ink pixel of a glyph does not, so this holds on every system. Chord diagrams: the same check over the
   first diagram's bounds. If a pixel check proves noisy on one OS, the fallback is a `:presentation` desktop unit test
   of the color pairs themselves (each `ChordProOutputTransformation` span color and the song text colors against
   the dark scheme's surface), not a tolerance.
7. **Delete** `GoldenRendersTest.kt`, `Goldens.kt`, `src/test/resources/goldens/` (`captureWindow()` stays: step 6
   uses it); in `tools/screenshots/build.gradle.kts` the
   `isUpdatingGoldens` val, its `outputs.upToDateWhen`, and the three `campfire.screenshots.golden*` /
   `updateGoldens` system properties; `campfire.updateGoldens` and its "Golden renders" heading in
   `gradle.properties`; in `.github/workflows/tests.yml` the comment above "Upload failed unit test reports" that
   speaks of goldens and the `tools/screenshots/build/goldens/**` path (the step stays for the test reports); the
   "Golden renders" section of `tools/screenshots/CLAUDE.md` (and the "pixels where semantics cannot tell" line stays,
   it is `PedalCoverageTest`'s); the sentence "A golden only says a sheet changed; this says it broke, and runs on every
   system." in `SheetKeyboardBudgetTest`'s KDoc. Grep the repository for `golden` afterwards: what remains is
   `DocumentGoldenTest` (document conversion, unrelated), `ClickSynthesizerTest`'s checksums, `RandomPicks`'
   `GOLDEN_GAMMA`, and the `build/goldens` sample string in `.github/scripts/test_check_doc_names.py` (test data of the
   doc-name checker; leave it).

Each step is one commit; steps 1–6 land before step 7 so no reason is ever unguarded.

Shared files: plan 12 edits `AppTest.kt` (the harness) and plan 20 `tools/screenshots/build.gradle.kts` and
`tests.yml`; this plan touches only the golden lines of each, so merging is mechanical.

## Tests

Steps 1–6 are the tests. Run `./gradlew :tools:screenshots:test` on macOS before step 7 and let CI (Linux) confirm the
same set; a test that passes on one and fails on the other is fixed (or its check reworked) before landing, never
skipped by OS.
