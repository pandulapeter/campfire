<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# The 50th review: a regression review since the 49th

Reviewed commit **54985be95** (branch `review/50th-regression-40901553239`), range `cfcf08150..54985be95`: the 49th
review's fixes and plans, the Hebrew translation and right-to-left layout, the sheet music work, the screenshot tool
rewrite, the AppImage, and the store listings pipeline, 98 commits. **Angle:** did these commits break anything that
worked at cfcf08150 (callers, documented usages, other platforms, other languages, both reading directions,
persistence and sync formats), and are the 49th review's landed fixes right? Run unattended in GitHub Actions: five
area reviewers (`:chordpro` and document import; song view, print, notation, and `abc`; right-to-left layout and
strings; the editor and other screens; platform shells, CI, and tools), the docs check, a check of the review's own
fixes, and two challengers.

## Headlines

- **Print settings saved before 5.2.0 lose "lyrics only" for sheet music** (06): a user who unticked Chords and grids
  now gets staves and notation text in every PDF.
- **Importing Campfire's own PDF misreads lines**: about one lyric line in ten of a Hebrew or Arabic song comes back as
  a boxed comment (01), and an all-italic lyric under its chords comes back as a comment without them (02).
- **The editor's arrow keys run backwards** on every directive, tab, and `abc` line of a right-to-left song (04).
- **An AppImage hiccup now withholds the Linux .deb** from a release (12).
- The shared iOS run configuration was deleted by accident for the fourth time; restored, and the pre-commit hook now
  refuses its deletion.

## Fixed during the review

| Commit | Message |
|---|---|
| 314369e42 | Restore the iOS run configuration that the ABC notation plans commit removed by accident. |
| 5f542d0fc | Refuse a commit that deletes the shared iOS run configuration. |
| 63be30b10 | Keep the editor's caret on its line when transposing removes one tune's transpose line and writes another's. |
| dc1832ef1 | Put the PDF converter's word pattern documentation back on the pattern it describes. |
| fb24ba0d8 | Fold the import review's Select all button away with its heading's height instead of letting the rows under it jump. |
| 895fbca47 | Say in the store submission scripts what a missing listing leaves behind when it fails the run. |
| c0abb04f6 | Say that the desktop release's bytecode check runs in the full JetBrains Runtime its image is cut from. |
| 4f7751d10 | Draw the fast scroller's thumb at the screen's edge, with its letter bubble growing out of it, in right-to-left languages. |
| d144cd3b4 | Say in the App Store submission script that a missing localization can leave a created version with its build attached. |
| 88ae181a4 | Stop the editor's transposition from failing when it removes a transpose line at the very end of an unfinished tune. |

The check of the fixes found two of them wanting (63be30b10 could index past the last line; 895fbca47 understated the
App Store case); each was fixed by one more commit (88ae181a4, d144cd3b4). Verified with `:chordpro:desktopTest`,
`:presentation:desktopTest`, `:data:formats:desktopTest`, `:tools:screenshots:test`, `:app:desktop:compileKotlin`,
`spotlessCheck`, and the `.github/scripts` unittests.

## Index

| # | Plan | Severity | Lane |
|---|---|---|---|
| 01 | Recognize a right-to-left boxed comment by its right edge, never a lyric by another line's left edge | medium | A |
| 02 | Keep an italic lyric line under a chord row a lyric when importing Campfire's own PDF | medium | A |
| 03 | Drop a songbook's per-song page footers again | low | A |
| 04 | Move the editor's caret by each line's own direction | medium | B |
| 05 | Keep the editor's song direction beside its text | low | B |
| 06 | Read an old print setting without Sheet music as Chords and grids | medium | C |
| 07 | Remember a tune that failed to engrave | low | C |
| 08 | Place a right-to-left song's music on the same side on screen as in the PDF | low | C |
| 09 | Say so when the web browser refuses to share a link | low | D |
| 10 | Mirror the remaining direction-bearing icons | low | D |
| 11 | Give the Romanian plurals their "few" form | low | D |
| 12 | Attach the Linux .deb before the AppImage is built | medium | E |
| 14 | Frame the Hebrew store images with the bundled Hebrew font | low | E |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|---|---|---|---|
| A | PDF and document import | 01, 02, 03 | `chordpro/…/convert/`, `data/formats/…/document/`, `data/formats/CLAUDE.md`, `data/model/…/importing/ExtractedDocument.kt`, `domain/implementation/…/mapper/DocumentMappers.kt` |
| B | Editor | 04, 05 | `presentation/…/ui/screens/songEditor/`, the new helper in `chordpro/…/edit/`, `tools/screenshots/…/songEditor/EditorDirectionTest.kt` |
| C | Print settings, notation, song view | 06, 07, 08 | `data/source/local/implementation` preferences document and mapper, `presentation/…/ui/notation/`, `presentation/…/ui/songView/` (or `ui/print/` under D2's option B) |
| D | Web share, icons, Romanian | 09, 10, 11 | `presentation/src/wasmJsMain/…/FilePicker.wasmJs.kt`, `FilePicker.kt`, `ExportController.kt` and its test, `composeResources/drawable/`, `ui/theme/CLAUDE.md`, `values-ro/strings.xml`, the new `PluralItemsTest.kt` |
| E | CI and the screenshot tool | 12, 14 | `.github/workflows/publish-linux.yml`, `.github/CLAUDE.md`, `tools/screenshots/src/main/…/Listing*.kt`, `Composition*.kt`, `tools/screenshots/CLAUDE.md` |

**Merge order: A, E, C, D, B.** A is pure logic everyone else is independent of; E touches only CI and the framing
code; C, D, and B each may move golden renders under `tools/screenshots`, B (the editor, with the open decision D1 and
the most UI) last.

**Shared files:** golden renders under `tools/screenshots/src/test/resources` (C's 08, D's 10, B's 04): each lane
regenerates only the goldens its change moves, and the merge re-runs `:tools:screenshots:test` after each lane;
`chordpro/CLAUDE.md` (A and B may each add a sentence; merge word by word). Plan 12 changes
`.github/workflows/`, which the unattended workflow's token cannot push: land it from a normal checkout. Plan 13 was dropped: the workflow it changed, `regression-review.yml`, was deleted.

## Decisions (the user took every recommended default)

- **D1 (plan 04): how the editor treats lines in another direction.** Recommended: **B**, arrow keys move by each
  line's own direction (directive, tab, grid, and `abc` lines left to right), the display unchanged; tab rows without
  letters still show reversed in a right-to-left song, documented. Option A (per-line direction marks) cannot work: Skia
  (desktop, iOS, web) resolves one direction for the whole field. C: keep today's behaviour and document it.
- **D2 (plan 08): which side a right-to-left song's music sits on.** Recommended: **A**, the screen follows the PDF (the
  music block at the right edge, still read left to right). B: the PDF follows the screen (left edge).
- **D3: arrow keys and page-turner pedals in a right-to-left interface.** Left goes to the next song in Hebrew, matching
  the mirrored pager (documented in `songDetails/CLAUDE.md`); a pedal pair sending Left/Right then feels reversed.
  Recommended: **keep** as documented. No plan.
- **D4 (plan 14): where a Hebrew store image's chips sit.** Recommended: **keep** the chips at their left-to-right
  corners; plan 14 fixes only the font and the text direction.
- **D5 (plan 06): a missing `showSheetMusic` key.** 5.2.0 omitted the key when the box was ticked (defaults are not
  written), so a missing key is "before 5.2.0" or "5.2.0, ticked". Recommended: **read it as `showChords`**, favouring
  every lyrics-only user upgrading from 5.1.x over a day-old release; the key is always written from the next save.

## Challenge

Two fresh challengers tried to break every plan; each plan carries a `**Challenged:**` line. All fourteen were amended,
none dropped. The ones that changed most: 01 decides the side per song on the page (the print sets every row, an
English one too, in the song's direction); 02 decides by the vertical gap, carrying each line's baseline through the
import, since a comment may follow a chord-only row; 04 now recommends option B, since option A cannot work on Skia; 08's
option A as first written moved nothing; 12 excludes `recordClassDataArchive` from the AppImage's own Gradle call; 13
rebuilds the branch without the workflow commits, since a revert commit would not get the push past GitHub.

## Checked and found solid

- Search and sort folding of Hebrew and Arabic (`ScriptFolding`): idempotent, Latin and Cyrillic untouched.
- `isStrongRtl`, `writingDirectionOf`, retired language codes (`iw`, `in`, `ji`, `mo`), `AbcTranspositions.moved`
  round trips, plain-text import of left-to-right text (the old path, byte for byte).
- Left-to-right layout and the PDF of Latin songs after the right-to-left work: chord placement formulas, cell order,
  inset sides reduce exactly to the old ones.
- Notation threading and its cache (main thread only, cancelled renders never written), the arrival token, auto-scroll
  timing of sheet music, the abc engine's caps, `%%beginjs`, and native load failure.
- Hebrew strings complete, placeholders matching, plurals by CLDR rule; `pluralTextResource` everywhere; bidi isolation
  marks kept out of file names, fields, the clipboard, and the PDF; direction from the first frame on every platform.
- Import review's Select all logic, open-source license sheets, Share Campfire, web file sharing, Settings tab row.
- Store listing scripts, locale matching, the `iw` alias; QuickJS packaging; .deb install without the menu tool;
  credits for Noto Sans Hebrew, QuickJS, and the AppImage runtime; the screenshot tool's flags as the skills use them.

## Dropped after verification

- None of the findings fell apart on verification. Two were folded into plans rather than fixed: the per-song page
  footer (03; its obvious fix would also drop repeated time signatures) and the Romanian plurals (11; each item needs
  judgement). The challenge amended all fourteen plans and dropped none.
