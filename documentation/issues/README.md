# Thirteenth review

**Commit:** `800ebde0b` on `master`, clean tree. **Range:** the 76 commits since the twelfth review
(`f8634ddb6..800ebde0b`): the dialogs-to-bottom-sheets rework and keyboard handling, input validation, setlist
reorder mode, archiving and durations, section auto-numbering, the editor and Songs list changes, cover thumbnails,
the overscroll effect, web history and the release skill.

**Angle:** correctness of the new code. Four Opus area reviewers (sheets and input; setlists; song details, editor and
Songs list; shell, web, docs and CI) and one live run of the Android debug build on the smallest supported screen
(360×640dp, real adb touches, a 3 000-event monkey run). Each finding was verified against HEAD by a writer per lane,
and every plan was then challenged by a fresh agent that did not write it.

## Headlines

Nothing loses library data. The ones that matter:

- **02** — on a phone held upright, Next through the New song form (and any sheet field below the fold) leaves the
  focused field under the keyboard: the keyboard is padding *inside* the sheet's scroll, so the field counts as
  visible. Seen live.
- **38** — Show all / Show fewer in the song filters jump instead of animating (collapse not at all, expand cuts
  rows in and drops the toggle). Requested by the user from the live run.
- **30** — a recalled chorus whose chorus opens with a comment loses its label and number on screen and in the PDF.
- **32** — the editor preview's cover never waits for typing to pause: it asks for a download of every half-typed
  address (which then gets blacklisted), and never crossfades.
- **34** — while typing in a short window the editor's last lines can sit a navigation bar's height behind the
  keyboard (drop-if condition).
- **45** — the prepare-release skill takes `v1.0.0` (2019) as the last tag, so release notes would cover the whole
  history.
- **01** — every song card's overflow menu recomposes on every frame of the keyboard animation.

## Index

| # | Plan | Severity | Lane |
|---|---|---|---|
| 01 | Read the overflow menu's height limit at measure time | medium | A |
| 02 | Pad every bottom sheet above the keyboard outside its scrolling content | medium | A |
| 03 | Ignore a sheet's header actions (and keyboard Done) once it has started closing | low | A |
| 04 | Slide the date picker sheet away on Save | low | A |
| 05 | Dismiss only the first-setlist sheet's own dialog when it closes | low | A |
| 06 | Return to the cover art sheet when Remove cover is cancelled | low | A |
| 07 | Let Manage links save past an empty row | low | A |
| 08 | Keep the duration field within what six digits can show | low | A |
| 09 | Stack Year and Duration where their labels do not fit side by side | low | A |
| 10 | Take a cover address typed without its scheme as https | low | A |
| 11 | Rank Song assignments search results like the Songs screen | low | A |
| 12 | Give the numeric fields a keyboard with a return key on iPhone | low | A |
| 13 | Correct the first-field focus KDoc | low | A |
| 14 | Leave only the calendar in the setlist date picker (user request) | low | A |
| 20 | Push a waiting import report by its request rather than by equality | low | B |
| 21 | Give up a dragged setlist order the library will not take | low | B |
| 22 | Duplicate a setlist from its current entries | low | B |
| 23 | Animate the dot before a setlist row's duration with the note | low | B |
| 24 | Correct the setlist write docs and narrow LIST_ITEM_KEYLINE | low | B |
| 30 | Head a chorus recall by its first section, not its first piece | medium | C |
| 31 | Count labels that name the kind when numbering sections | low | C |
| 32 | Keep the song info section under one key so the preview cover settles | medium | C |
| 33 | Keep the search anchor's tail space until it scrolls out of sight | low | C |
| 34 | End the editor field at the keyboard while typing in a short window | medium | C |
| 35 | Say where the song metadata editors are offered | low | C |
| 36 | Focus the search field only when the search is opened | low | C |
| 37 | Narrow the editor pane segments' padding so Preview fits at 360dp | low | C |
| 38 | Animate Show all and Show fewer in the song filters both ways (user request) | medium | C |
| 45 | Find the last release tag by its numeric name in prepare-release | medium | D |
| 46 | Document setlist reorder in the header menu and the web history | low | D |
| 47 | Record What's new only once it has stayed on screen or was closed | low | D |

## Lanes

| Lane | Area | Plans, in execution order | Files owned |
|---|---|---|---|
| D | release skill, docs, What's new | 45, 46, 47 | `.claude/skills/prepare-release/SKILL.md`, `BrowserRoutes.kt` (KDoc), `app/web/CLAUDE.md`; `Dialogs.kt` WhatsNew branch, `CampfireViewModel.kt` `showWhatsNewOnVersionChange` / `onWhatsNewShown` |
| B | setlists, import report | 20, 22, 23, 24, 21 | `SetlistsScreen.kt`, `ListItems.kt`; `CampfireViewModel.kt` `showImportReport`, `duplicateSetlist`, `reorderSetlist` |
| C | song details, editor, Songs list, filter chips | 30, 31, 32, 33, 34, 35, 36, 37, 38 | `SectionNumbering.kt`, `SongLyrics.kt`, `PrintLayout.kt`, `SongsScreen.kt`, `SongSearchScrollAnchor.kt`, `SongEditorScreen.kt`, `Search.kt`, `SearchState.kt`, `SegmentedChoice.kt`, `CollapsibleChipFlow.kt`, `Controls.kt`, their tests |
| A | bottom sheets, forms, text input | 01, 02, 04, 14, 03, 05, 06, 07, 08, 09, 12, 10, 11, 13 | `Dialogs.kt` (all but the WhatsNew branch), `TextFieldBottomSheet.kt`, `CoverArtSearchSheet.kt`, `SongMetadataDialog.kt`, `SongLinksDialog.kt`, `DurationDigits.kt`, `OverflowMenu.kt`, `SearchIndex.kt`, `SongPickerIndex.kt`, new `NumericImeOptions` expect/actuals, `:chordpro` `ChordProSyntax` / `ChordProLinks` / `ChordProCoverArt`, both `strings.xml`, `chordpro/CLAUDE.md`; `CampfireViewModel.kt` `setVisibleDialog` (plan 06) |

**Merge order: D, B, C, A.** D is small and its view-model edit sits next to plan 06's; B's view-model edits land
before the bigger lanes; A touches the most shared UI files (`Dialogs.kt`, both `strings.xml`, `:chordpro`,
`presentation/CLAUDE.md`) and goes last.

**Shared files.**
- `CampfireViewModel.kt`: D (What's new functions), B (`showImportReport`, `duplicateSetlist`, `reorderSetlist` and its
  KDoc), A (`setVisibleDialog` rule for plan 06). Separate functions; a cherry-pick conflict is resolved keeping both.
- `Dialogs.kt`: D owns only the WhatsNew branch; A owns the rest.
- `presentation/CLAUDE.md`: every lane. Line 71 and line 126 are single very long lines — merge sentence by sentence
  (word-level three-way), never one side's line whole. Plans 21 and 24 both edit the "A drag is answered…" bullet
  (different sentences); 46 and 35 both may touch line 71.
- Root `CLAUDE.md`: 46 (Web bullet), 35 (links bullet), 14 (the bottom-sheet bullet).
- `SongLyrics.kt` / `SectionNumbering.kt` / `PrintLayout.kt`: lane C only, 30 → 31 → 32 in order.
- `CampfireBottomSheet` (in `Dialogs.kt`): 02 owns the column modifier and `isKeyboardVisible`; 03 owns `close` and the
  closing guard; 04 and 14 edit only the date picker block.

## Decisions

Answered by the user on 2026-10-04 — all four took the recommended option:

1. **[Answered: recommended] Plan 02 — keyboard padding outside the sheet's scroll.** This changes a recorded preference that scrolling content
   runs under the bars "and the keyboard" (the navigation bar half is unchanged; the visible difference is a bottom
   fade just above the keyboard on an overflowing sheet). *Recommended:* A, pad above the keyboard outside the scroll
   on every window, as short windows already do. Alternative B: keep it inside and write a keyboard-aware
   bring-into-view.
2. **[Answered: recommended] Plan 31 — labelled sections and numbers.** *Recommended:* A, a label that is just the kind's name ("Verse",
   "Verse 1", in the app's language or the ChordPro directive name) counts as one of the kind and reserves its number;
   a label in the other app language counts only while the app is in that language, so switching the app language can
   renumber (folds never move). Alternative B: stop numbering a kind once any of its sections has such a label.
3. **[Answered: recommended] Plan 47 — when What's new counts as seen.** *Recommended:* after 3 s on screen or when it is closed; the cost is
   that a process ending within 3 s of it appearing (a closed tab, a swiped-away app) shows it again next launch.
   Alternatives: an "update check not answered yet" state on all four platforms, or record only on close.
4. **[Answered: recommended] Plan 06 — what Cancel on Remove cover brings back.** *Recommended:* a fresh cover art sheet with the search re-run
   from the song's fields. Alternative: keep the edited query, selection and typed address, which needs the sheet's
   state held in the view model.

Taken as the plans recommend (no question needed): plan 10 lets typed cover addresses complete `https://` like links
(file reading stays strict); plan 20 is kept as hardening although its scenario is not currently reachable; plan 21
uses an `onNotWritten` callback; plan 36 accepts that a restored process or a screen come back to no longer puts the
caret back in an open search; plan 30 accepts that a fold saved on an affected recall unfolds once; plan 37's
fallback (icon-only segments at compact width) only if "Előnézet" still does not fit; plan 09's 480dp threshold is
checked by the executor in both languages.

## Challenge

Every plan was challenged by a fresh agent (one for A, one for B + D, one for C). Nothing was dropped.
Amended: **02** (folds in the `derivedStateOf` keyboard flag, docs of `retainSheetContentHeight`, iOS pan note),
**03** (keyboard Done in New song and Delete library is also ignored while closing — ✕ then Done could delete the
library), **04** (Save compares with the day the picker opened on, or it greys out during the slide), **09** (one
custom `Layout` instead of an if/else that would drop focus on resize), **12** (iOS opt-in), **21** (identity
comparison of drags), **31** (recall of a bare-name label numbered, 4-digit cap, builds on 30), **33** (one collector
instead of two racing ones), **38** (leaving chips placed in lookahead, first frame without layers, same-height
changes, default springs), **45** (`--exclude` the version being prepared instead of a `HEAD^` fallback), **46**
(three missed sentences). Plan 14 was added afterwards at the user's request and read against 03 and 04.

## Checked and found solid

StepTaps; Reset all filters; the Cover art switch gating every thumbnail; the search anchor's bounds; numbering
consistency between screen, PDF and lyrics-only mode; editor field retention; `ChordProDuration` parse/format and
`setlistTotalDuration` (incl. the `+`); reorder mode lifecycle and archive gating; `ChecklistOrder`; preferences round
trip; export selection; strings parity (495 keys); `CreateSongUseCaseImpl`; close-cancels-draft and parent/child sheet
flows; focus behaviour per form; pinned heights; `CompactKeyboardEffect`; web keyboard insets; BrowserHistory
synchronisation; desktop Escape; `ContentOverscroll`; `EdgeFade`; dependency pairing (Kotlin 2.4.20 / Koin plugin
1.2.1); `upload-artifact@v7` exists. Live on Android at 360×640dp: New song create/discard/double tap, odd titles,
Edit setlist with date and countdown, durations, reorder drag writing the right order, archive / duplicate / delete
with double taps, Song assignments, tags, links, cover search grid, Cover art off, numbering, tap-to-step; the monkey
run had no crash and no ANR.

## Dropped after verification

- Rotation with the keyboard up hiding the focused field (L2): landscape is a short window, which already pads above
  the keyboard; only Compose's missing bring-into-view after rotation remains, with no clean fix.
- Hungarian What's new wording (A7): it is the shipped 4.6.0 message, replaced at the next release — write "oszd meg"
  and "lista" then.
- Material's typed-date strings in the system language (L7): no hook in Material; superseded by plan 14, which removes
  the typed entry.
- Same ⓘ icon for About the song and Edit song details: no better existing drawable.
- Empty `{key: }` in a new song: the intended skeleton since a154cb1ef.
- Welcome / What's new history entries on the web (D5): neither fix changes what Back does before any interaction.
- Collapsed chip count 12 vs 15: not in the evidence frames; plan 38 removes the mid-animation inputs anyway.
- Song cards' tags and the setlist description's tap-to-open having the Show all problem: no expand control / a
  different, working mechanism.
- Setlists caret after the Edit setlist sheet: focus kept after Back, not a keyboard reopening.
- Import report stuck after deleting from the editor (B1's scenario): the editor has no Delete or rename; the defect
  is kept as hardening in plan 20.

## Manual checks owed

Each plan's own Manual check section, plus: 02 on Android and iOS portrait (Next through New song, the pickers, the
cover sheet, Delete library, the bottom fade above the keyboard); 38 in the phone sheet and the wide side panel (Show
all/fewer both ways, the any/every choice, the sheet's height); 34 on the 360×640 AVD with three-button navigation
(its drop-if); 12 on an iPhone simulator with the software keyboard; 47 only from a Play internal testing track; 14
with the app and the phone in different languages; 03 ✕ then keyboard Done in Delete library writes nothing.
