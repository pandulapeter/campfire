# 61st review: 5.7.0 pre-release

Reviewed at `0feb3925f` on `main` (2026-11-26). Angle: a regression check of the 220 commits since 5.6.0 for what would
hurt the release: data loss, crashes, hangs, broken flows, missing translations. Three Opus reviewers (song details and
the dock; import and the page crop; platform shells, strings, and settings) and a Sonnet CLAUDE.md check. The widgets
and app shortcuts were left out (the 60th reviewed them).

## Headlines

- A lyrics-only songbook can lose songs on import: one that names two other songs' first lines is taken for a
  contents page (02).
- A pasted or read tab line with many `(x)` notes froze the web build's import (fixed during the review).
- On Android, a microphone grant that lapses ("Only this time") leaves the tuner saying it was refused (01).

## Fixed during the review

Worked in `.claude/worktrees/review-61`, landed by fast-forward.

- `30631afaa` Update the Metronome tab's notes now that song details' metronome is the dock's tempo chip.
- `97be50504` Pin each Android setlist widget added from a setlist's menu to that setlist, even with another request
  made before the first popup was answered.
- `e0f8d2233` Read a tab line with many notes in parentheses at once on the web too, rather than freezing the import
  (230 ms at 24 notes in V8, doubling per note; linear now).
- `e0af87a50` Read a long line opening a parenthesis it never closes at once in a pasted or imported chord sheet
  (quadratic: 1.3 s at 40k characters).

Regression read: no problems. Noted and accepted: a staff note in a string's margin now counts as one character towards
the 24-character margin cap, which only widens what an already staff-shaped line may carry.

## Index

| # | Plan | Severity | Lane |
|---|------|----------|------|
| 01 | Ask again for the microphone on Android after a grant lapses | medium | P |
| 02 | Keep lyrics-only songs that name other songs' titles | medium | C |
| 04 | Find a PDF diagram column in linear time | low | F |
| 05 | Keep the unanswered song layout while a song is read | low | P |

## Lanes

| Lane | Area | Plans | Files owned |
|------|------|-------|-------------|
| F | `:data:formats` | 04 | `PdfChordDiagrams.kt` and its test |
| C | `:libs:chordsheet` | 02 | `ChordSheetConverter.kt`, its tests, the chordsheet `CLAUDE.md` files |
| P | `:presentation` | 01 → 05 | `MicrophonePermission*.kt`, `FitUnchosenSongLayoutToWindow.kt` and its caller, tuner/preferences `CLAUDE.md`, a UI test |

Merge order: F, C, P. The lanes share no files. P goes last since it touches UI and the `expect`/`actual` set of four
platforms.

## Decisions

- **01:** decided by the user on 2026-11-26: option A, a device-local Android flag for "refused for good" that is never
  backed up and is cleared on any grant.
- **05:** decided by the user on 2026-11-26: fit only while no song is open.

## Checked and found solid

- **Strings:** all 46 keys added or changed since 5.6.0 are in all 11 languages, with matching arguments and no English
  left in a translation. The Android resource strings and the iOS widget `Localizable.strings` match too.
- **Song details:** the dock's tuner placement agrees across `songDetailsToolsOf`, `isTunerInMenu`, `DockRows`, and the
  side panel in every switch combination traced. Also solid: the U key, fold by long press or right click, the
  Preferences rows hidden for switched-off Features, the key chip's room and RTL handling, and the wide-window column
  counts.
- **Page model plumbing:** marks are used only when the line counts agree, both `.bin` files are version 2 with 22
  features, and `PAGE_SNAPSHOT_SCRIPT` cleans up in `finally` and makes no request.
- **Other converter changes:** capo picker removal, `ChordSheetDefinitions`, and `withoutShapeMark` are fine.
- **Platform shells:** the Android manifest, the widget pinning receiver, the iOS widget files and share inbox, the web
  routes, and the `.githooks` deferred-Gradle path are fine.

## Challenge

Two fresh Opus agents tried to break every plan.

- **01, amended.** Android no longer reads or writes `hasTurnedOnMicrophone`, which would otherwise hide the never-asked
  notice. A dismissed system dialog is not counted as refused for good: the rationale is compared before and after the
  request.
- **02, amended.** An entry is a line that starts or ends with a title, and entries must be at least half of the lines.
  Titles are looked up through a hash map. The timing test is now one that fails before the fix.
- **03, dropped.** See below.
- **04, amended.** At most the 8 gaps nearest each edge are tried once a page has more than 16. Results are the same on
  every real page.
- **05, amended.** No fit while any song is on the back stack (the editor opens over its song) or while a transition
  runs. A song open from launch keeps its stored layout.

## Dropped after verification

- **03 (volta marks taken for verse numbers):** dropped by the challenge. Its own drop condition holds: the import
  corpus has no ending written as `1.` alone, a 1, 2, 1, 2 run is already refused, and the fix would stop splitting PDF
  stanzas set without blank lines.
- The Polish and Czech plurals have no `many`. This predates the range, so it is out of scope.
