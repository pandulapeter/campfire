# Seventeenth review

**Reviewed commit:** `1c52e5347` (master). **Angle:** a lean review of what landed since the sixteenth review
(`9b38f8e25..1c52e5347`, 13 commits): mid-song `{tempo}` / `{time}` changes and the pages they force, `{transpose}`
in the editor, the SongbookPro backup import, the new-song template, magazine paging in one column, the setlist
reorder and card sizing, the overscroll fade and the animated metronome icon. Four read-only area reviewers, one
verifier/writer per lane (every finding re-proved at HEAD, pure ones with probe tests since deleted), then two
challengers.

The working tree held the user's own uncommitted work while this was written — `documentation/TO_DO.md`,
`documentation/plans/` and `presentation/.../ui/metronome/MetronomeControls.kt`. No plan touches those files, and
reviewers judged `MetronomeControls.kt` only as committed.

## Headlines

- **01 / 20** — The editor's Tempo and Time shortcuts with the caret on a song's first line write a directive the
  parser reads past as a second header line: the user types 90 and nothing changes. Where the change does land before
  the first line (inside an opening `{start_of_verse}`, or written by hand), the song details screen gives the
  metadata card a page of its own and plays the opening tempo on it.
- **10** — A SongbookPro value with a line break (a multi-line copyright, a two-line author) or a brace breaks the
  header the import writes: the artist is lost, `{copyright: …` fragments show as lyrics, a folder name can retitle
  the song.
- **11 / 12** — An unreadable SongbookPro backup is imported as a "song" of raw JSON; an `.odt` / `.xlsx` / `.epub`
  picked with songs is now unpacked and reported as `content.xml`, `styles.xml`, … instead of by its own name.
- **02–05** — Round-trip slips in the new tempo/time handling: Prettify drops a blank line after a change, a change
  straight back to the value in force still forces a page, the serializer can write `{tempo: null}`, and Prettify on
  a multi-song text can copy a tempo into the next song's header.

## Index

| # | Fix | Severity | Lane |
|---|---|---|---|
| 01 | Select the header's value instead of inserting a change above the body's first line | medium | A |
| 02 | Keep the blank line after a change that ends a running paragraph when prettifying | low | A |
| 03 | Drop a merged change that returns to the values in force before it | low | A |
| 04 | Fill a change's missing side with the song's own value named later; never serialize `null` | low | A |
| 05 | Count songs in `hoistedTimings` the way `prettify` does | low | A |
| 10 | Flatten every SongbookPro header value onto one line without braces | medium | B |
| 11 | Report an unreadable SongbookPro backup as one unread file | medium-low | B |
| 12 | Report a zip of an unknown type that holds nothing importable by its own name | low-medium | B |
| 13 | Resolve a setlist's songs within the archive it arrived in (**D1**) | low | B |
| 14 | Take a SongbookPro time signature only where `ChordProTime` reads one | low | B |
| 15 | Title a nameless, undated SongbookPro set without English | low | B |
| 20 | Start no page at a change written before the song's first line | medium | D |
| 21 | Scale later tempos by the clamped opening tempo, and clamp the change too | low | D |
| 22 | Keep a songbook of > 200 sections that changes tempo in one column (**D2**) | low | D |
| 23 | Reset the list pull when its short-content bounce leaves | low | D |
| 24 | Document the setlist column width as 416dp (**D3**) | low | D |

## Lanes

| Lane | Area | Plans, in execution order | Files owned |
|---|---|---|---|
| A | `:chordpro` | 01, 02, 03, 04, 05 | `ChordProHeader.kt`, `ChordProParser.kt`, `ChordProPrettifier.kt`, `ChordProSerializer.kt`, `model/ChordProBlock.kt` (KDoc), their tests, `chordpro/CLAUDE.md` |
| B | SongbookPro import, archives, import planning | 10, 14, 15, 11, 12, 13 | `backup/SongbookProBackup.kt`, `ArchiveLocalSourceImpl.kt`, `PrepareImportUseCaseImpl.kt`, `ImportPlanner.kt`, `ImportFilesUseCaseImpl.kt`, `data/model/.../ImportPlan.kt`, their tests, `data/source/local/implementation/CLAUDE.md`, `domain/implementation/CLAUDE.md` |
| D | `:presentation` | 20, 21, 22, 23, 24 | `songDetails/SongLyrics.kt`, `RowSnapping.kt` (KDoc), `metronome/SongTempo.kt`, `platform/ContentOverscroll.kt`, their tests, `presentation/CLAUDE.md` |

**Order constraints.** A: 03 before 04. B: 10 → 14 → 15 → 11 all edit `SongbookProBackup.kt` (10 and 14 the same
`directive("time")` area; 11 changes `read`'s return type last); 12 before 13 (both change the `files.forEachIndexed`
loop and `sort` in `PrepareImportUseCaseImpl`). D: 20 before 22 (22's check is `units.timingStarts.size > 1` as 20
leaves it). **Across lanes:** 04 relies on 21 *as amended* (the change's own tempo clamped too); if 21 is cut back to
clamping only the opening tempo, 04 gives a wrong tempo for out-of-range files with an override.

**Merge order: A, B, D.** A changes the model the presentation reads (04 fills a `Timing`'s null side, 03 drops a
no-op one), so D's tests run on top of it; B is independent; D touches the most shared docs.

**Shared files.** Root `CLAUDE.md`: 12 (the "Other apps' libraries" bullet), 20 and 22 (the Metronome bullet), 24
(the short-window bullet). `presentation/CLAUDE.md`: 20, 22, 24. `chordpro/CLAUDE.md`: 01, 02, 03, 04 — lane A
only. Each plan quotes the sentence it changes; change only that sentence, and merge conflicting paragraphs as a
word-level three-way merge, keeping every sentence of both sides.

## Decisions

- **D1 — plan 13** (setlists from two backups picked together pointing at each other's song). The fix threads an
  origin through four files and a public model for a case that needs two backups (or a backup and a loose file of the
  same title) in one pick. Challenger recommended dropping it. **Answer (2026-10-06): keep it.**
- **D2 — plan 22**: one column only for songbooks over 200 sections **that change tempo or time** (recommended;
  others keep today's layout, the docs reworded to match) or for **every** songbook over 200 sections (the docs'
  current literal wording). **Answer (2026-10-06): only those that change tempo or time.**
- **D3 — plan 24**: the docs follow the code's **416dp** (recommended), or the constant becomes **440dp** (a list
  1248–1320dp wide drops from three columns to two). **Answer (2026-10-06): 416dp, the docs follow the code.**

Defaults taken without asking: 01 option A (caret at or before the first content line — lyrics, tab, grid, a
`{chorus}` recall — selects the header's value); 04 option A (the parser fills the null side; the click already
played a null tempo at that value, and a null time now clicks the song's own signature rather than 4/4); 10 encodes a
link with spaces rather than dropping it; 12 counts an entry only if it was read, and accepts that a `.jar` / `.apk`
holding a `.txt` is still listed entry by entry; 15's nameless set is titled `#<Id>` (file `7.setlist.json`).

## Checked and found solid

`ChordProTempo` / `ChordProTime` validation (`{tempo: 0}`, `{time: 0/4}`, `{time: 7/0}`, three-digit beats);
changes inside recalled choruses; merging of adjacent tempo + time; modulation keys per stretch; `ChordSheetConverter`
header-vs-change labels; no quadratic paths in the new parser/prettifier code. SongbookPro: budget given back by an
unknown non-zip file, nesting depth, 4M nested `[` and 2M empty songs, lenient value types, duplicate ids, BOM and
code pages, re-importing a backup is idempotent; the new song template's empty `{artist: }`, `{capo: 0}`,
`{tempo: 120}`, `{time: 4/4}` change no name, duplicate check or sync. Song details: `flowIntoPages` terminates and
cannot overflow, stretch concatenation and re-indexing, steps through pages taller than the screen, the click follows
the headed-for page and never a dragged one, Metronome off forces nothing, `withoutEmptyTimings`, `PrintLayout`
keeping a change with what follows it. Editor: `insertChangeable` CRLF/CR, empty header lines, one undo step per
press. Metronome icon and `BeatRow` (no animation while nothing plays, no out-of-range on a shrinking signature),
`MetronomeContext` stretch restarts, the setlist reorder's header index and padding reset, EN/HU string parity and
`textResource` use.

## Dropped after verification

- **SongbookPro nameless song titled "untitled"** (half of 15): no `{title}` is written; "untitled" is only the batch
  name, and the import's own fallback (`LibraryFiles.FALLBACK_NAME`) is the same word, so nothing the user sees
  changes.
- **Metronome icon recomposes every frame for ~300 ms per beat** (`beat.pulse` read in composition): the old button
  did the same; not filed.

## Challenge

Two fresh challengers read every plan against the documented behaviour, every caller and the other plans. **Sound:**
02, 05, 14, 15, 20, 23, 24. **Amended:** 01 (a `{chorus}` recall counts as a line, matching 20), 03 (`rejoin()`
restores trimmed blank lines and accepts a blank-only continuation), 04 (the time fill also fixes a 4/4 click in a
3/4 song; lands after 03), 10 (escaped line-separator regex; links through `ChordProLinks.usableUrl`), 11 (a bare
version number on `dataFile.txt`'s first line also marks a backup), 12 (an entry counts only if read, so 11's output
is reported by the picked name), 13 (source-compatible signatures, origin passed back through `replanSetlists`, appended
last), 21 (the change's own tempo clamped too), 22 (check is `timingStarts.size > 1` after 20). **Dropped:** none.

## Manual checks owed

- 01 Editor: caret on the first lyric line → Tempo selects the header's value; on the second line it inserts a change.
- 20 Demo "House of the Rising Sun" with `{c: Slowly}` + `{tempo: 60}` before the picking pattern, at 800×600 and
  phone width: no page of the controls alone; the click plays 60 from the first page.
- 10 / 11 A real SongbookPro backup with a multi-line copyright and author; the same backup with `dataFile.txt`
  replaced by `{}`.
- 12 Desktop: drag an `.odt` and a `.cho` in together; the `.odt` is reported once, by its name.
- 04 A song naming its tempo below a `{time}` change: the timing line names it; a 3/4 change at the bottom clicks
  three beats a bar after the earlier change.
- 23 Android and iOS: short setlist in reorder mode, pull, tap Done, make the list short again — no faded top card.
- 22 Desktop maximised: a > 200-section file with one mid-file `{tempo: 60}` stays one column and the click switches
  at the change.
- 02, 03, 13, 21 as their plans describe.
