# 53rd review

Reviewed at `a59cc76c3` on `master`. Angle: lean regression of the 10 commits since the 52nd review (capo keeps the key,
ChordPro export as played, PDF songbook splitting and chorus detection, web editor caret), judged as a player. Three
Opus area reviewers and a Sonnet docs check; no live run.

## Headlines

- **Pasted and imported chord sheets lose chorus lines** (plan 03): a chorus whose first lyric is indented, or any
  chorus in a centred PDF, ends after one line, and the rest of it lands outside as verse text. This is a regression from
  `b284feef5`.
- **A one-song PDF can be cut into several songs** (plans 08, 09): two later pages opening with bold part names, or a
  lyric sheet whose chorus repeats on every page. These are regressions from `cdadecb14`.
- One italic backing-vocal line ends a headed section (04). An all-italic song turns into one chorus (07). A written-out
  chorus is read as a recall (05).

## Fixed during the review

- `12290577e` Convert the whole of a chord sheet pasted over a selection in the editor, even the lines it shares with the selection.
- `1fb15e270` Leave a transposition, capo, or tempo hidden by switching off chords or the metronome out of a song exported as played.
- `a9e91059b` Document that a paste over the whole text in the editor is narrowed like a keystroke (found by the
  post-fix check: Select all, then paste, cannot be told from a web keystroke).

## Index

| # | Plan | Severity | Lane |
|---|------|----------|------|
| 01 | Scale mid-song `{tempo}` changes in a song exported as played | low | A |
| 02 | Keep the file's own `{capo}` when the capo is hidden in an as-played export | low | A |
| 03 | Judge a chorus inset by edges two lines share (F1 + F2) | high | B |
| 04 | End a section with no verse before it only where the lyrics return to plain type | medium | B |
| 05 | Recall a chorus only in a song with unheaded verses | medium | B |
| 06 | Take no bold or italic lines before anything sung for a chorus | low | B |
| 07 | Set a chorus apart only from plain verses of two lines | medium | B |
| 08 | Take a title's type from a later title only where it stands out by size | medium | B |
| 09 | Take no page of a repeated chorus for a contents page | medium-high | B |

## Lanes

| Lane | Area | Order | Files owned |
|------|------|-------|-------------|
| A | export as played | 01 → 02 | `ui/playing/SongAsPlayed.kt` and its test, `chordpro/.../edit/ChordProMetadataFields.kt` and its test, `ui/screens/export/CLAUDE.md` |
| B | chord sheet import | 03 → 04 → 05 → 06 → 07 → 08 → 09 | `chordpro/.../convert/` (`ChordSheetConverter.kt`, `ChordSheetLook.kt`, `ChordSheetSongSplit.kt`, `ChordSheetIndexTitles.kt`, `CLAUDE.md`), `ChordSheetConverterTest.kt`, the `data/source/local` document goldens (read only: their `.cho` must not change) |

No file is shared between the lanes. **Merge order: A, then B.** A is small and independent. B is the long one and its
golden tests take longest.

## Decisions

- **D1 (plan 02), decided 2026-11-02: (a).** A keyboard player who keeps the key exports a capoed file nobody changed:
  (a, recommended) keep the file's own `{capo}` where the capo is hidden, so nothing is rewritten unless a shown
  override changed it; (b) keep writing the chords as they sound, labelled as a transposition.
- **D2 (plan 08), decided 2026-11-02: (a).** Where a songbook's first title is not the titles' type, take a later title's
  type only if it is a heading, over 1.2× the body size, or a type the contents list two openings or more of (a,
  recommended). A songbook with body-size bold titles, a larger cover, and *no* contents page is then no longer split.
  (e): look past the first title only where its page holds no chord row.

## Challenge

Ran on every plan before reporting; none dropped. 01 amended: tempo read as the parser reads it (a body-only opening
tempo counts), scaled before `set` so the override is not applied twice, API narrowed to `mapTempoChanges`. 02
amended: full trace of capo readings; the pianist's PDF (sounding, no capo) and the file (stored capo) now differ by
intent. 03 amended: inset judged by every lyric so far against the verses' shared edge, not by two lines sharing an
edge (that broke wrapped and unevenly typed choruses). 04 amended: an italic unheaded chorus now runs through bold lines
to plain type; CLAUDE.md bullet named. 05 amended: a section ended by a return to the verses' look is still recorded,
keeping a real recall. 06 amended: a stanza break counts as a start, keeping a bold chorus that opens a lyrics-only
sheet. 08 amended: body-size titles still count where the contents list them. 07 and 09 sound.

## Checked and found solid

- Capo arithmetic under both capo readings, every surface (header key, cards, stepper labels, diagrams, `{define}`,
  tabs, PDF labels and their round trip), the ask-once question, persistence defaults, strings in all 8 languages.
- The as-played export: nothing applied twice, the capo written once, German notation, ABC `%%transpose`, share links
  and setlist zips stay as stored; the file now leaves out exactly what the PDF does.
- The Settings → About website link (rules, strings, header), the editor's surrogate handling and markup toggles.
- Plain and pasted text still convert as one song; index bounds, empty collections, and cost on 500 pages.
- Docs check: no false claims in the touched `CLAUDE.md` files; `idleTimerDisabled` is the iOS API behind Compose's
  `keepScreenOn` (a stale-name false positive).

## Dropped after verification

- None. L1 (plan 06) holds only when two bold lines are left as lyrics after the title; the reported
  `guessesTitle = false` path cannot carry bold type (its one caller, `JsonSongs`, passes plain text).
