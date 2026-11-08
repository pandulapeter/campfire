<!--
This file is part of Campfire.
Copyright (c) Pandula Péter 2017-2026.
https://github.com/pandulapeter/campfire

This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
https://mozilla.org/MPL/2.0/.
-->

# 55th review: regression of the 78 commits since the 54th

Reviewed at `2795c968c` on `master`; angle: broad regression of `a4c6372cc..2795c968c` (tooltips and keyboard
shortcuts, the Preferences sheet and Settings tabs, songs list animations and random picks, sharing and import fixes,
importer rules, the `libs/` grouping, the translate workflow); lean: three Opus area reviewers, a Sonnet docs check, no
live run.

## Headlines

- On the desktop (and likely iPadOS with a keyboard), typing `?` into any text field opens the Keyboard shortcuts
  sheet mid-typing (01).
- A PDF's small-type chord-only row next to a page number or a count row is dropped as a chord diagram (03).
- No data loss, crash, or store blocker found.

## Fixed during the review

- `77df178e2` Point the root notes' metronome and tuner invariants at their modules under libs.
- `22d4d1a42` Import a section labelled PC or Pre as a pre-chorus rather than a bridge.
- `6647eb92c` Keep a version line such as Extended Mix (1984) under an imported song's title as its subtitle rather than its artist.
- `01b6a0bc2` Leave transposing and Select all out of the keyboard shortcut lists in read only mode, where they do nothing.
- `0f763c132` Give the translate and fix-issues workflows' agent a checkout without the token that can push, handing it to git only to push.
- `57e017b0f` Note that a sort picked in the songs list's selection mode goes to the top.
- `900d2ff2e` Keep importing a chord sheet with spelled-out chord names fast when it has a long run of chord rows.

The importer fixes were scored against the import corpus: no document changed. A regression read of the range found
nothing broken.

## Index

| # | Plan | Severity | Lane |
|---|---|---|---|
| 01 | Open the shortcuts sheet on `?` only while no field is typed in | high | K |
| 02 | Give each Preferences page its own scroll position | low | P |
| 03 | Keep small chord rows beside page numbers out of diagram runs | medium | F |

## Lanes

| Lane | Area | Plans | Files owned |
|---|---|---|---|
| F | PDF reader (`:data:formats`) | 03 | `PdfChordDiagrams.kt`, `PdfLineLayoutTest.kt`, `data/formats/CLAUDE.md` |
| P | Preferences sheet | 02 | `PreferencesSheet.kt`, `PreferencesContent.kt`, `ui/preferences/CLAUDE.md`, its UI test |
| K | App-wide keys | 01 | `CampfireApp.kt`, `OverlayState.kt`, `AppShortcuts.kt`, `AppKeyboardShortcutsTest.kt`, `ui/CLAUDE.md`, `ui/components/CLAUDE.md` |

**Merge order:** F, P, K — F is disjoint from the rest; K last, as it touches the app root and the most shared docs.
No file is shared between lanes; a UI test file that P creates goes in its own file, never into
`AppKeyboardShortcutsTest.kt`.

## Challenge

All three plans were challenged; all amended, none dropped:

- 01: the first fix (act on `?` only while the root itself holds the focus) would have broken `?` on the desktop and web,
  where any click focuses the clicked control; replaced by counting text input sessions through
  `InterceptPlatformTextInput`, with a test that `?` still works after a click.
- 02: per-page scroll states are `rememberScrollState()` (saveable), not plain `ScrollState`s; modifier order spelled out.
- 03: neither the gap nor the horizontal rule keeps the existing diagram fixture; replaced by narrowing what counts as
  a mark (ASCII punctuation and dashes are not marks; a diagram needs a mark row of at least two marks). A small chord
  row over a bare-digit count row (`1 2 3 4`) remains a known limit.

## Decisions

None open.

## Checked and found solid

- Bottom sheet wheel guard, sheet height animation, `CampfireTooltip` (hover, long press, warm window), root vs song
  details focus, screen-switch keys and the editor's refusal, M/T guards, Android and iOS shortcut overlays, Settings
  tab split and old addresses, new strings' parity and Oxford commas.
- Random picks persistence (fields only added, not synced), selection ordering and dividers, search open/close anchor
  snapshots, sort reveal, column widths, dice icon credit, shared setlist already in the library, import message
  plurals, as written / as played control, iPhone shared links, Android 9–12 startup fix (no other API 33+ or Java 10+
  overloads on Android), desktop sign-in listener, Polish plurals, translate workflow inputs, `libs/` grouping check.
- Importer: held-dash chords, Roman numeral detection, Nashville parsing, verse numbers, grid shapes, I/O margin
  labels, spelled chords, chord gaps and rounding, superscripts, gutter headings, paragraphs, running lines, inset
  chorus, KMP-cleanliness.

## Dropped after verification

- None dropped; two findings were judged acceptable behaviour and documented rather than changed: a sort in selection
  mode goes to the top (`57e017b0f`), and an Italian `Io`/`vi` lyric line in a sheet already detected as Roman
  numerals (too niche to change).
- The translate and fix-issues agents can still reach the push token through hooks or git config they control at push
  time; not closable from inside the job, noted only.
