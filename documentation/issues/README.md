<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Review plans: the last 20 commits, low-end performance, and the v4.9.0 release

Reviewed at `6e9e8e6` (rebased onto `2419e35` during the review), branch `claude/vibrant-clarke-fyhmhn`. Angle: the
functionality of the last ~25 commits (`de879dd..2419e35`), test coverage, performance on low-end devices, and what the
next release needs; plus the whole `:presentation` codebase against Android's Jetpack Compose performance skill
(developer.android.com/agents/skills/jetpack-compose/jetpack-compose-performance).

Nothing was compiled during the review: this environment's network policy blocks `dl.google.com`, so Gradle could not
resolve the Android and Compose dependencies. Every fix was checked by reading (a fresh agent checked every call site against
its callee, and compiled the doubtful Kotlin typing questions as stand-alone snippets); `check_sizes.py` and
`check_license_headers.py` pass. **CI on the pull request is the first build of these commits.**

## Headlines

- **Searching while selecting songs hid every song not yet ticked** (`withoutSectionHeaders` dropped the headerless results
  group). Fixed.
- **A tick while the search is open jumped the list** (the held card was looked up in groups the grid does not show). Fixed.
- **Reorder mode scrolled to the wrong setlist** when a setlist above it had a countdown or a running time but no
  description (`headerIndexOf` still counted only descriptions). Fixed, with a test.
- **The baseline profile never visited Metronome, Tuner or Chords** on a phone (they sit behind the Tools item); the
  generator is fixed, the profile still has to be re-recorded on an emulator before the release.
- **The golden renders are probably stale**: the song list layout changed in several commits after they were last
  updated, and `tests.yml` runs `:tools:screenshots:test`. Regenerate on Linux with `-Pcampfire.updateGoldens=true`.
- Performance: song cards no longer pay a subcomposition and hidden animations for their menu, setlist cards and chord
  diagrams no longer recompose at every scroll start and end, and the print preview no longer recomposes on every frame of
  a pinch. The largest remaining items are plans 01, 02 and 06.

## Fixed during the review

| Commit | Message |
|---|---|
| `4571b2a` | Keep setlist cards from being composed again as every scroll starts and ends. |
| `90e9e19` | Spare song cards the room measurement and hidden button animations of a menu that never lets its actions out. |
| `e62c34e` | Keep the Chords tab's diagrams from being composed again as every scroll starts and ends. |
| `9034299` | Keep the metronome's beat blocks from being composed again on every step of the tempo slider. |
| `3cf50fa` | Draw the onboarding's celebration embers on a layer of their own so they no longer redraw the screens under them. |
| `76a4a3e` | Start no fade for the lines of a song that are already in place as it opens. |
| `6d22382` | Share one icon between all the label pills of a song card. |
| `b63b3d5` | Turn the fold chevrons and the song step buttons' arrows without composing them again on every frame. |
| `28179fc` | Redraw only the page, not the whole print preview, on every frame of a pinch, a pan or a zoom. |
| `c9c0da6` | Work out where a typed-into stepper is only when a press needs it, not on every scroll frame. |
| `4dd45ce` | Let the cover search reuse a cover tile for the next one scrolled in rather than the form or a message. |
| `7f4482d` | Bring the right setlist to the top for reordering when the ones above it show a countdown or a running time. |
| `c40e75d` | Drop the removed Add your own songs row from the Settings notes. |
| `99c1a27` | Cover when a setlist has a summary and setlist names that start like another item's key in tests. |
| `feb8899` | Correct the tuner note's ascent comment for Latin notation. |
| `74ed1c7` | Keep a transposition or a fold written while a song is read from composing the app's root and every screen's entry again. |
| `a7d4490` | Keep the setlist row within the function size limit. |
| `5a428f1` | Keep the search results listed while songs are being selected. |
| `10b6e2a` | Keep the cards in place under a tick while the search is open. |
| `f8d6e6b` | Describe the setlist summary's countdown and running time as they are drawn. |
| `d2e8348` | Correct what the chrome notes promise about screens recomposing during a bar and rail handover. |
| `a9aab07` | Correct the promo video notes on which accent its chips take. |
| `2c75f5f` | Drop the songs notes' copy of the song card's artist rule. |
| `fc3dc84` | Walk the baseline profile through the Metronome, Tuner, and Chords tabs behind a phone's Tools item. |
| `1d11ca9` | Note the promo video's one green chip. |

## Index

| # | Plan | Severity | Lane |
|---|---|---|---|
| 01 | Read the fast scroller column's width and the selection padding during layout | medium | A |
| 02 | Build a song card's menu entries only when its menu opens | medium | A |
| 03 | Release the search card hold when the results change under it (bug) | medium | A |
| 04 | Compose a song card's label row once rather than twice | low | A |
| 05 | Resolve the overflow menu's room once, not in every card | low | A |
| 06 | Keep the song's sections from being composed again on every frame | medium | B |
| 07 | Draw the page-break dividers from the section layout | low | B |
| 08 | Hand the bar and rail handover's room to the screens as layout reads | low | C |
| 09 | Record the startup profile from the cold start alone | low | C |
| 10 | Let the editor's preview give the web page's thread back | medium (web) | D |
| 11 | Re-highlight only the edited lines in the editor | low | D |
| 13 | Key the setlists screen's scroll to the top on the list it arrives with | low | A |
| 14 | Tint the pulsing metronome mark without a filter per frame | low | E |
| 15 | Draw the tuner's in-tune color in the draw phase | low | E |
| 16 | Keep the print renderer's text layouts cached on dense pages | low | D |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|---|---|---|---|
| A | Song list, components, setlists | 03, 01, 02, 04, 05, 13 | `ui/screens/songs/`, `ui/screens/setlists/`, `ui/state/setlists/`, `ui/components/` (list, songCard, menu, actions, labels, `ContentOverscroll.kt`) |
| B | Song view | 06, 07 | `ui/songView/`, `ui/screens/songDetails/SongDetailsPage.kt` |
| C | Chrome and the Android profile | 08, 09 | `ui/chrome/`, `ui/CampfireScreens.kt`, `app/baselineprofile/`, `app/android/src/main/generated/` |
| D | Editor, print | 10, 11, 16 | `ui/screens/songEditor/`, `ui/print/`, `chordpro/.../edit/` (the highlighter, plan 11) |
| E | Metronome and tuner | 14, 15 | `ui/metronome/`, `ui/tuner/` |

**Merge order:** B, E, D, C, A. B, E and D touch nothing another lane does; C changes the padding contract the list
screens read (`KeyboardAwarePadding`), which plan 01 in lane A builds a sibling of, so C goes before A; A touches the most
shared UI files, so it goes last. Plan 10 may reach into `ui/songView/` (`rememberSongLyricsModel`): if it does, lane D runs
after lane B has merged, cut from the main checkout's `HEAD`.

**Shared files:** `ui/screens/CLAUDE.md` (plans 08 and 13) and `ui/components/CLAUDE.md` (plans 01 and 04): each lane edits
only its own paragraph, and the merge keeps both. No plan adds strings.

## Decisions (awaiting the user)

1. **Song cards no longer show a title's subtitle** (`SongListItem`: `songCardTitle(song.titleWithoutSubtitle, index)`), so
   "Wagon Wheel" and its "Live" copy by the same artist read as two identical cards, in the Songs list and in setlists,
   and a screen reader no longer hears the subtitle. Recommended: show the subtitle on the supporting line
   ("Artist · Live").
2. **Card widths**: `ListColumns.kt` now has `CARD_BASE_WIDTH = 336.dp` and `CARD_ARTIST_WIDTH = 8.dp`, while
   `components/songCard/CLAUDE.md` and the `songGridColumnWidth` KDoc say 320dp, 40dp off by artist, and "the default
   desktop window gets two columns", which the new numbers no longer give (an 800dp window less the rail leaves ~680–700dp,
   under the 816dp two columns need). Recommended: say which numbers are intended; the doc and the KDoc are then corrected
   to match, or the artist allowance restored.
3. **The language is applied a composition late** (`ApplyLanguagePreference`'s `LaunchedEffect`), costing one extra full
   composition at cold start for everyone whose app language is not the system's. Writing `currentLanguage` in the body
   (a forward write) would remove it, but breaks the "no side effects in the body" rule. Recommended: leave it.

## Checked and found solid

- Lazy keys and content types on the Songs, Setlists, Chords and import screens; selection as a set of file names; no
  per-item flow collection; search ranking and setlist matching off the main thread; scroll-driven header and fade state
  read in layout and draw.
- The song view's text measurement caches, draw-phase chord rows, glides read in placement, and pinch handling.
- Theme crossfade, keyboard-aware padding, the export slide, Android R8 (full mode, optimize, no broad keeps) and the
  profile installer wiring.
- Tuner and metronome per-frame reads in draw; listening bound to the lifecycle.
- The editor's shared text state, summary cache, debounced preview, and the export pipeline.
- Removed strings and dialogs leave nothing behind; English and Hungarian keys, plurals and placeholders match.
- `tools/promo-video` ships nothing (private package, npm registry only, no binaries, headers present);
  `tools/screenshots` holds no personal paths.

## Not planned (cost judged negligible, or unverified)

Setlist row keys built by joining and splitting strings; each song row reading the keyboard controller; `ListTopFade.strength`
allocating pairs per read; the card cover size and the search icon's back-gesture effect read in composition; the
`songs_key` regex per card; `RelativeTime`'s `produceState` initial value; the fast scroller's `distinctBy`; the
editor's undo / redo enabled state (unverified against Foundation's sources); `PlayStopMark`'s path per frame; the
Chords block's description arriving a frame late; `contentType` on the small lists (licenses, print options, pickers,
checklist headings).

## Dropped after verification

- **12, match the song picker's search off the main thread** (dropped by the challenge): on the web `Dispatchers.Default`
  is the page's one thread, so the platform it was for gains nothing; elsewhere it saves 1–3 ms of a frame while making the
  list lag the query and needing the picker's refresh, reveal and no-results keys rebuilt. Revisit only if the match itself
  measures over ~8 ms per keystroke.
