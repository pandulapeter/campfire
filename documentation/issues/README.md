# 48th review: regression of the 36 commits after the 47th

Reviewed at `e7656f28d` on `master` (`56b49ba78..e7656f28d`: the continuous song layout with its per-song speed, the
PDF import rework, lyric markup, key-change scale suggestions, the natural sign in the fonts, and Romanian, German, and
Spanish). The user's French commit (`a5987514b`) landed during the review; the plans are written against the HEAD after
it. Full budget: five Opus area reviewers, a Sonnet check of the changed `CLAUDE.md` files (no false claims), and one
live run rendering the app offscreen at 360 × 640 dp, text scale 1.0 and 1.3, in en, ro, de, and es.

## Headlines

- **A chord written with markup (`[<b>G</b>]`) is never transposed or converted** (01): the editor's Transpose writes a
  wrong chord into the file.
- **A song scrolling by itself glides back to where Play was pressed** whenever it is laid out again (a fold, a pinch, a
  transposition, a resize) (03).
- **The Tuner's instrument pill shows no tuning on a 360 dp phone** at the default text size, in every language (06).
- **The Play and Mac App Store descriptions are over 4,000 characters** since the language line grew (07): the next
  listing update is refused.
- Longer languages break words in the onboarding's buttons, cut the navigation labels at large text, and crowd the
  import review's rows (08–12).

## Fixed during the review

`e7656f28d..ea6391720` (French, `a5987514b`, is the user's own):

- `62cb0a3a3` Stop a PDF font map of skipped entries from running without limit, failing the import instead.
- `a51f3c58f` Read a lyric line full of ampersands in time proportional to its length.
- `7eeea2a33` Take bold, italic, or underline off only a pair of tags that enclose each other, never two side by side.
- `ea4839544` Test that a song's scroll speed follows its renames and deletions.
- `894a68cc2` Point the song and library deletion docs at everything the preferences keep per song.
- `c25d0c88d` Fade the import screen's button out while the import loads or runs, rather than shrinking its shadow.
- `3e66558f7` Play a scroll speed synced from another device after a drag that ended on the stored speed.
- `9514c097c` Group a song's chord diagrams by key whether or not degrees are shown, in the PDF as on the screen.
- `ca2271243` Keep the scroll speed a drag reached when the song stops playing before the drag ends.
- `33b3f66f2` Check each language in the store listings' language line, not only the last one named.
- `a3c1fabc3` Pair bold, italic, and underline tags spelled two ways by the style they set, as the song view reads them.
- `ea6391720` Say in the song view notes that the chord diagrams' key groups also need the scale walk.

A fresh read of the range found no fix breaking a caller; its two loose ends are the last two commits.

## Index

| # | Plan | Severity | Lane |
|---|------|----------|------|
| 01 | Transpose and convert chords written with markup | medium | C |
| 02 | Split a year off only a credit, not an artist name | low | C |
| 03 | Keep the reader in place while a song scrolls by itself | medium | A |
| 04 | Add up quick nudges of a scrolling song | low | A |
| 05 | Glide the key headings with their chord diagrams | low | S |
| 06 | Show the tuning in the Tuner's instrument pill on a phone | medium | I |
| 07 | Bring the Play and Mac App Store descriptions under 4,000 characters | medium | L |
| 08 | Shorten the translations cut off on a phone | low | L |
| 09 | Name German scales the way German musicians do | low | L |
| 10 | Keep the onboarding buttons from breaking words | medium | L |
| 11 | Fit the navigation labels at large text | low | L |
| 12 | Keep the import review rows readable in longer languages | low | L |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|------|------|-----------------|-------------|
| C | `:chordpro` | 01, 02 | `chordpro/` (markup, rewriter, chords, converter, their tests and `CLAUDE.md`s) |
| S | song view | 05 | `ui/songView/SongChordsSection.kt` |
| I | instrument pill | 06 | `ui/instrument/`, `ui/tuner/CLAUDE.md`, `ui/screens/tuner/TunerScreen.kt`, their tests |
| A | continuous layout | 03, 04 | `songDetails/stepping/RowSnapFlingBehavior.kt`, `songDetails/autoScroll/SongAutoScroller.kt`, both `CLAUDE.md`s, `AutoScrollTest.kt` (new), `AppTest.kt` |
| L | languages and fit | 07, 09, 10, 11, 08, 12 | every `strings.xml`, store listings, `.github/scripts/check_store_listings.py` (new), `.githooks/pre-commit`, `tests.yml`, the skills, root `CLAUDE.md`, onboarding, `ui/components/ShrinkToFit.kt` (new), `CheckboxListItem.kt`, chrome, import review, `TextFit.kt` (new), `AppTest.kt` |

**Merge order: C, S, I, A, L.** C, S, and I touch nothing another lane does. A and L both add a parameter to
`runAppTest` in `tools/screenshots/.../AppTest.kt`, so **lane L is cut from the main checkout's `HEAD` after lane A has
merged**, not from `START`. L goes last as the lane with the most shared UI files and strings. Within L, 10 creates
`TextFit.kt`, which 08, 11, and 12 use, and 11 creates `ShrinkToFit`, which 08 applies to `SegmentedChoice` (decision 7); 06 keeps a private copy of what it needs.

Shared-file rules: only lane L edits `strings.xml` and the root `CLAUDE.md`. `AppTest.kt` is edited by A (a
`songLayout` parameter) and L (a first run in `appearance.language`); L, cut after A, keeps both.

The user was editing `ui/screens/songDetails/autoScroll/AutoScrollControls.kt` while the review ran; no plan touches it,
but the tree must be clean before execution.

## Decisions

All answered by the user on 2026-10-27, each with the recommendation:

1. **02**: an `Artist:` value still gives a year where a comma sets it apart (`Artist: David Bowie, 1972`), or never.
   Recommended: yes, after a comma.
2. **03**: while playing, a relayout stands the song still for the 250 ms settle wait and the glide back, then plays on
   from the reader's place. Recommended: accept that pause.
3. **06**: the pill shares its line only where it fits whole. On a 360 dp phone at the default text size the Tuner's
   pill takes its own line (the reference pitch beside it, as at 1.3), one more pinned line, and the Chords and Scales
   tabs' capo moves under the pill in every language, English included (a line that scrolls, no pinned height).
   Recommended: yes, one rule for every tool's line; the alternative keeps the capo's cut-but-shared line with a real
   threshold.
4. **07**: what to cut from the store descriptions. Recommended: the "Long songs fit on fewer pages…" bullet, from all
   four listings.
5. **10**: German "Überspringen" still does not fit beside the dots at 1.0. Recommended: keep the word, the dots taking
   their own line on German phones.
6. **12**: the import review's Keep both / Replace choice always under the row's text. Recommended: always under, rather
   than beside it on wide windows.
7. **08**: the Romanian theme labels. "Închisă" alone on a segment also reads as "closed" (off). Recommended:
   "Deschisă / Întunecată", with 11's `ShrinkToFit` also applied to `SegmentedChoice`'s labels (which also fixes English
   "As written" and "Sharps (♯)", cut at 1.3; lane L then runs 11 before 08). The alternative is strings only,
   "Deschisă / Închisă".

## The challenge

Run on all twelve plans by three fresh agents (lanes C + A, S + I, L). Sound: 01, 02, 04, 10, 11. Amended: 03 (the
same-pass layout path sets the flag too; the UI test runs a frozen clock, since `play()`'s frame loop never lets an
auto-advancing one go idle), 05 (tests: the goldens hold no song with two keys, so they prove nothing here), 06 (the
same rule moves the Chords and Scales tabs' capo under the pill on every 360 dp phone; a new `ToolCapoLineTest`), 07
(`tests.yml`'s documentation-only ignore is kept, its comment rewritten; the hook and a release's run check listings),
08 (Romanian accidentals wording; the theme pair became decision 7), 09 (the family names keep "Modi von harmonisch
Moll"; the lost search for "harmonisch moll" is accepted), 12 (Select all keeps its box on its label's line; the
summary check shares the rows test's runs). Dropped: none.

## Checked and found solid

- Preferences: `autoScrollSpeeds`, `songLayout`, and `isSongLayoutChosen` decode with defaults from an old document,
  drop only a bad entry, follow renames and deletions, sync add-only on upgrade; `AppLanguage` and `SongLayout` are not
  synced.
- PDF running headers and footers: whole-width rows, one-page PDFs, page counts against time signatures.
- `ChordProMarkup.strip`, `MarkupAttributes`, row marks, `ChordSheetPageSplit`, `ChordSheetMarginLabels`,
  `ChordSheetHeaderParts`, `ChordSheetDefinitions`, `SongKeys.keysOfChords`.
- Auto scroll lifecycle (page change, layout switch, covered, `ON_STOP`, hidden web tab), the play loop's allocations,
  speed writes, asking once.
- `PageEdgeScroll` (nested scroll only, no wheel hijacking), the key-grouped diagrams in the PDF, `EditorFormatting`,
  the import queue, `Messages.importCounts`.
- Translations: every key present, placeholders matching, idiomatic musical terms, the language picker, locale
  fallbacks, the iOS project and share extension, the web loading screen, the fonts' ♭ ♮ ♯.

## Dropped after verification

- Romanian has no `few` plural (20 and above reads "25 zile"): known, documented by the add-language skill; the
  generated tables fall back to `other`.
- Romanian "Double drop %1$s" in English: ro, de, and fr keep "Drop" and "Open" as guitar jargon.
- German "Stimmgerät" cut in the Tools button group at 1.3, and the accidentals row at 1.3: English is cut there too, so
  no translation fixes it (plan 08 notes them; 11's `ShrinkToFit` could follow).
- A setlist page index reused by another song starting it playing by itself: not traced, reported as unsure.
- Unsure, not filed: the import's chord-fill removal on ordinary sheets, `ChordProLiteralText` not escaping `<tag>` in
  imported prose, the pages slot recomposing per measure.

## Measurements

- The live run's renders: en/ro/de/es at 360 × 640 dp, text scale 1.0 and 1.3, 22 screens each; no missing glyphs, no
  untranslated UI text. On Skiko (desktop, iOS, web) `isLineEllipsized` is always false, so `TextAutoSize` with an
  ellipsis never shrinks there and tests asserting it cannot fail (11, 10).
- Store descriptions: Play 4,023, Mac App Store 4,029 characters of 4,000.
