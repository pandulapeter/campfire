# Eleventh review — the five commits since the PDF export review

**Reviewed commit:** `ed54c7da9` (master, clean). **Angle:** a lean correctness review of the new code only —
569c23c4e (chord grids in monospaced columns), b0d4cd782 and 8fa2e1484 (unnamed verses and paragraphs, section
titles), e29a47367 (minor UI fixes) and ed54c7da9 (the PDF export as a full screen). Three Sonnet area reviewers, no
live run; every finding re-read against HEAD before it became a plan.

## Headlines

- **06 (medium)** — with the export screen open, a pedal or the arrow keys still step the song under it, on desktop and
  the web, whenever the preview is not up (loading, failed, nothing ticked).
- **01 (medium)** — a repeat count or note after a grid line's last bar line widens a real bar of the longer lines
  around it; the existing test had baked the padding in.
- Nothing loses data; everything else is low.

## Index

| #  | Plan | Severity | Lane |
|----|------|----------|------|
| 01 | Give the text after a grid line's last bar line a column of its own | medium | A |
| 02 | Measure grid columns in drawn characters rather than UTF-16 units | low | A |
| 03 | Head a bare tab or grid in the PDF the way the viewer does | low | A |
| 04 | Name an unnamed section's fold toggle by its first line | low | A |
| 05 | End a grid (and a tab) in the editor's highlighting wherever the parser does | low | B |
| 06 | Leave the pedal keys to the export screen while it covers a song | medium | C |
| 07 | Ignore Save, Share and option changes on an export screen sliding away | low | C |
| 08 | Hide the covered app from screen readers while the export screen is up | low | C |
| 09 | Call the PDF export a screen in the view model's comments | low | C |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|------|------|-----------------|-------------|
| B | `:chordpro` highlighter | 05 | `ChordProHighlighter.kt`, `ChordProHighlighterTest.kt` |
| A | song viewer and PDF layout | 01, 02, 03, 04 | `GridColumns.kt`, `GridColumnsTest.kt`, `SongLyrics.kt`, `PrintLayout.kt`, `PrintLayoutTest.kt`, `RenderSectionsTest.kt` |
| C | export screen | 07, 09, 06, 08 | `PrintExportScreen.kt`, `CampfireViewModel.kt`, `CampfireApp.kt`, `SongKeyboardShortcuts.kt` |

**Merge order: B, A, C** — B is independent; C last because it touches the app-wide UI files (`CampfireApp.kt`,
`CampfireViewModel.kt`). Lane C runs 07 before 09 because 09's reworded comment describes 07's guard.

**Shared files:** `presentation/CLAUDE.md` (A and C may each add or reword sentences — merged word by word, every
sentence from both kept); `PrintLayout.kt` (lane A's, but plan 09 rewords the `PrintSource` KDoc — a different hunk); `strings.xml` in both languages (only lane A, plan 04).

## Decisions

None open.

## Checked and found solid

- Fold keys did not move with the new headings: they are keyed by the file's label or the kind's name plus `#n`,
  counted the same in every mode, and `paragraph#n` cannot collide with `verse#n`.
- The demo song still carries the header its file name is made of.
- Both strings files have every new section string; no ordinals.
- Grid alignment: no index-out-of-range path in `toBarParts`; opening and closing repeats line up; the viewer and the
  PDF agree on what is one run; alignment is remembered per run.
- Highlighter offsets: the word split and the grid tokenizer agree, spans never overlap or run out of range.
- Export screen: settings are saved however it closes; Cancel/progress semantics kept; Back and Escape order (the
  required-update screen still wins); Ctrl/Cmd+F, Ctrl+plus/minus and the wheel zoom gating; insets and FAB clearance;
  `print_key_hint` removed from both string files and unreferenced; the CLAUDE.md files describe the screen.

## Dropped after verification

- A lone "Verse" heading over a chord-only unnamed verse in lyrics-only mode — the same rule as the instrumental
  "Intro" heading, which is intended (singers need to see instrumental parts).
- `x2` inside a bar coloured as a chord in the editor — the viewer draws it as one too; the two agree.
- The viewer's grid wrapping one character earlier than the PDF's — the page and the screen have different widths
  anyway, so no wrap point is shared to disagree about.
- `| | A |` (an empty bar) read as one bar — rare, and aligned consistently.
- An empty box over the app while preferences are still null — preferences load before any export can open.

## Challenge

Ran on all nine plans (two fresh agents, 2026-10-01): 08 sound; 01–07 and 09 amended, none dropped. In substance:
06 now has the covered song **consume** the arrow and page keys (returning false would hand them to Compose's focus
search, which can scroll the covered song) and the export screen take the focus on every platform; 07's view-model
guard is the one that matters (a Save queued during a re-layout fires after the tap) and compares with `==`; 09 keeps
`dismissSheet` (its still-visible check is needed) and covers three more stale comments, one in `PrintLayout.kt`;
05 clears the delegate flag too, as the parser does; 04 passes the name through `FoldChevron` instead of new semantics
modifiers; 02 counts only combining marks out (no surrogate collapsing); 03 drops the chords-off branch (empty
sections already lose their heading); 01's expectations computed exactly.

## Manual checks owed

- 01: a grid ending `x2` on a phone and in the PDF preview.
- 03: export a song with a bare tab; compare headings with the screen.
- 04, 08: TalkBack and VoiceOver on a song with no section directives, and through the export screen.
- 06: desktop and Chrome-on-a-computer, Export to PDF with nothing ticked, Down / Page Down do not move the song.
- 07: Back then an instant tap on Save PDF: no picker.
