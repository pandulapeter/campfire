<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# The 44th review

Reviewed at `c12660487` on `master` (the user's `5bdb67e08` landed during the review; fixes start after it). Angle:
regression of the 52 commits since the 43rd sweep (`4006f4532..c12660487`): b/i/u markup, the export screen and its
PDF options, tabs drawn as lines, import review tagging and joining, cover cleanup, log sharing, the change feed, and
the fast scroller. Lean: four area reviewers and a docs check, no live run.

## Headlines

- The web build was broken at HEAD (`String(CharArray)` in the joined tab lines), fixed during the review.
- A batch joining the import review while "Replace N files?" is open can overwrite files the user never confirmed (01).
- The editor's Bold wraps tab, grid, and directive lines in tags, breaking staffs, grids, titles, and comments (02).
- The new hand-signed iOS CI could pick an Xcode-made profile, which manual signing refuses: the 5.0.2 upload would
  fail. Fixed during the review; unproven until the next `publish-ios` run.

## Fixed during the review

| Commit | Message |
| --- | --- |
| `54efc5af0` | Keep the editor from taking the focus and raising the keyboard when it comes back after the app was recreated with a word selected. |
| `b8715ec9d` | Keep the iOS release build from picking a provisioning profile Xcode made, which signing by hand refuses. |
| `382507a96` | Fix the web build after the joined tab lines. |
| `ac30f05c9` | Let the browser keep Ctrl + Shift + B, I, and U, its developer tools among them, while the web app's editor is open. |
| `ab8faf718` | Stop the web app rewriting its whole earlier log every second while it logs. |

A fresh agent read `5bdb67e08..ab8faf718` against the findings: nothing broken.

## Index

| # | Plan | Severity | Lane |
| --- | --- | --- | --- |
| 01 | Hold a joining batch while the Replace confirmation is open; live count in the import tag sheet | medium | I |
| 02 | Keep editor formatting to lyrics and comment values (two commits) | medium | E |
| 03 | Print each formatted fragment as one bidirectional text | low | P |
| 05 | Flush the process log once per line | low | L |
| 06 | Keep markup's bold out of the PDF text layer | low | P |

## Lanes

| Lane | Area | Plans, in order | Files owned |
| --- | --- | --- | --- |
| L | process log | 05 | `ProcessLogWriter.kt`, `ProcessLog.{jvmShared,ios,wasmJs}.kt`, `ProcessLogWriterTest.kt`, `ui/platform/CLAUDE.md` |
| E | editor formatting | 02 | `:chordpro` `edit/` (`ChordProMarkupLines.kt` new, `ChordProLiteralLines.kt`, tests, `CLAUDE.md`), `EditorFormatting.kt`, its test, `ui/screens/songEditor/CLAUDE.md` |
| P | print | 03, 06 | `ui/print/` (`PrintRows.kt`, `PrintDocument.kt`, `PrintLayout.kt`, `PrintRenderer.kt`, `PrintLyricPieces.kt`, `PrintPdfWriter.kt`, tests, `CLAUDE.md`), `ChordSheetConverterTest.kt` |
| I | import review | 01 | `ImportController.kt`, `CampfireDialogs.kt`, `DialogType.kt`, `ImportTagsSheet.kt`, `ImportReportScreen.kt`, `review/ImportReviewItems.kt`, `ImportControllerQueueTest.kt`, `ui/screens/importReport/CLAUDE.md`, `ui/dialogs/CLAUDE.md` |

**Merge order:** L, E, P, I — the small, self-contained lanes first; I last, as it touches the most shared UI files
(dialogs). **Shared files:** none; each plan's **Files:** line is its lane's boundary.

## Decisions

Taken 2026-10-25:

- **04** (a 30-minute grace and a timed prune for stray cover copies) is **dropped**: a cover picked into an unsaved
  form may be downloaded again at Save, or show no cover until online. Not to re-raise.
- **05** is **kept** (flush once per line).

## Checked and found solid

Dependencies (version bumps only, credits unversioned, Koin pairing unchanged); metronome sound change and subdivision
vibration; web tuner crossfade and suspend; log privacy (no tokens, codes, or `rlkey` URLs logged) and bounds;
change feed lifecycle and backoff; first-meeting sync conflicts; shared links feed; folded sections per song; import
tags and `withAdded`; select-all on focus; keyboard opening rules; formatting shortcuts; fast scroller and scroll to
top; removed filters panel; export options; strings in both languages; store listings' limits and wording;
`tools/screenshots` window split; `ChordProMarkup.strip`; tab column transposition; joined tab strokes; paste into
tabs and grids; Prettify and grid margins; PDF option gating. The changed `CLAUDE.md` files hold no false claims.

## Dropped after verification

- Imported prose escaping no `<b>`: `ChordProLiteralText` never promised it (its KDoc names brackets, braces, and `#`),
  and a literal `<b>` in pasted text formatting the line is arguably what it meant.
- The change feed counting an owed Dropbox backoff as held poll time: one extra retry while offline, nothing visible.
- The web log storing the same text under both keys when quota is short: older than this range, quota-only.
- Whole bold lines read back from Campfire's PDF as chords or titles: probed, they read back as lyrics (only a bold
  chord-named word does not, which is 06).
