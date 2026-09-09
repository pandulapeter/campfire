# Step 06: the ChordPro viewer

**Goal:** the song details screen renders the `:chordpro` model instead of its own line parser: labelled
environments, chorus recall, comments, tabs, grids, a metadata header, and key-aware transposition. The existing
layout machinery (columns, sticky headers, horizontal flow, font scale, lyrics-only mode, pager, pinch to zoom) stays.

**Depends on:** 05.

## 1. Wire the model in

- `presentation/build.gradle.kts`: add `implementation(project(":chordpro"))` to `commonMain` (the model types are
  used directly by the composables; the parse/transpose calls go through the use cases from step 05).
- `CampfireViewModel`: replace `loadSongDetails(song)` / `transpose(rawData, …)` with
  - `suspend fun getRenderedSong(fileName: String, setlistFileName: String?): ChordProSong?` = load content →
    `ParseChordProUseCase` → `TransposeChordProUseCase(song, song.metadata.transpose + userTransposition)`.
    Expose it as a `StateFlow<Map<String, ChordProSong>>` cache keyed by file name + transposition, or simply
    compute it in the screen with `produceState`; pick the simplest thing that does not re-parse on every
    recomposition. Parsed songs must be dropped from the cache when the content changes (editor, step 09) or on rescan.
- Delete `parseSongLines`, `SongLine`, `SongSection`, `parseSectionHeader`, `parseLyrics`, `groupIntoSections`,
  `withoutChords` from `SongLyrics.kt`; the `song_details_section_*` strings and `SongSection.title()` go too
  (labels now come from the file; see §3 for the legacy mapping).

## 2. Rendering rules

`SongLyrics(song: ChordProSong, …)` (same parameters as today otherwise) turns `song.blocks` into the list of
"sections" fed to the existing `SongSectionsLayout`:

| Block | Rendering |
| --- | --- |
| `Section(Verse / Custom / Paragraph)` | Header = label (none for `Paragraph`; for `Custom` without label, capitalise the name, e.g. "Intro"). Lines as today: chords above lyrics, aligned by position. |
| `Section(Chorus)` | On the raised card, as today. Header = label or "Chorus" (string `song_details_section_chorus`, keep that one string). |
| `Section(Bridge)` | Like a verse; header = label or "Bridge" (keep `song_details_section_bridge`). |
| `Section(Tab)` | Header = label or "Tab" (new string). Lines in `FontFamily.Monospace`, `softWrap = false`, horizontally scrollable, chords never transposed. Hidden in lyrics-only mode. |
| `Section(Grid)` | Header = label or "Grid" (new string). Each line is a `Row` of tokens: bars in the outline colour, chords in the chord style, beats as "·", repeats as "%", trailing text in the lyrics style. Hidden in lyrics-only mode. |
| `ChorusRecall` | Renders the *most recent* `Section(Chorus)` again, on the card, header = recall label or the chorus' label or "Chorus". If no chorus precedes it, render a header-only card. |
| `Comment(PLAIN)` | A line in `bodyMedium`, secondary colour. `ITALIC`: same, italic. `BOX`: same inside an outlined rounded box. Comments are their own layout section, so they can sit between columns freely. |
| `Break` | Ignored (the column layout makes its own breaks). |
| `Lyrics.Chord(isAnnotation)` | Rendered in the chord row but with the lyrics colour and italic, never transposed. |

Lyrics-only mode (`shouldShowChords = false`): drop chords and annotations, drop lines that become blank, drop tab
and grid sections entirely, keep comments.

Empty song (no blocks): show the existing "no data" placeholder with the new hint "This file has no content yet."
(Hungarian: "Ez a fájl még üres.") and, from step 09 on, an "Edit" action.

## 3. Metadata header

At the top of the song (before the first section, part of the scrolling content, not sticky): title in
`headlineSmall`, artist below in `bodyLarge`, then a single wrapped row of small "chips" (plain text with a dot
separator is fine) for: key (`Key: Am`, after transposition, using `ChordProTransposer.transposeChord` on the
metadata key), capo, tempo, time. Use string resources `song_details_key`, `song_details_capo`,
`song_details_tempo`, `song_details_time` with `%1$s` arguments. `subtitle` is shown under the artist if it differs
from it. Custom metadata is not shown.

The top app bar keeps showing the title as today.

## 4. Transposition controls

The existing transpose up/down/reset controls stay; the displayed value is the user transposition (not including
`{transpose}` from the file). When a key is present, show the resulting key next to the value ("+2 · Bm"). Flats vs
sharps follow `ChordProTransposer.prefersFlats`.

## 5. Strings to add (both languages)

| name | en | hu |
| --- | --- | --- |
| `song_details_section_tab` | Tab | Tab |
| `song_details_section_grid` | Grid | Rács |
| `song_details_key` | Key: %1$s | Hangnem: %1$s |
| `song_details_capo` | Capo %1$d | Capo %1$d |
| `song_details_tempo` | %1$s BPM | %1$s BPM |
| `song_details_time` | %1$s | %1$s |
| `song_details_empty` | This file has no content yet. | Ez a fájl még üres. |

Delete `song_details_section_intro`, `_verse`, `_pre_chorus`, `_solo`, `_outro` (labels come from the file now).

## 6. Test fixtures

Three sample files already exist under `docs/rewrite-plan/samples/`: `simple.cho` (title, artist, key, two verses,
a chorus and a `{chorus}` recall), `everything.cho` (every block type: tab, grid, all three comment styles, an
annotation, a slash chord, `N.C.`, custom `intro`/`solo`/`outro` environments, implicit paragraphs, `{capo}`,
`{tempo}`, `{time}`, `{meta}`, an `x_` directive, `{column_break}`) and `legacy.cho` in the Campfire 3 dialect
(`{c: Verse 1}` headings, no environments). Use them for the manual checks below and in later steps; extend them if
you find a construct they miss.

## Verify

- Full build for all four platforms.
- Desktop and Android with the three samples copied into the library: environments get their labels, the chorus is
  on a card and the recall repeats it, the tab is monospaced and not transposed, the grid shows bars/beats, comments
  render in three styles, the legacy file looks like it does in Campfire 3, the header shows key/capo/tempo, lyrics-only
  mode hides chords/tabs/grids, transposing by +1 from a song in `E` shows `F` and flats (`Bb`, not `A#`).
- Sticky headers, column balancing, horizontal flow and pinch-to-zoom still behave (compare against the 3.x build if
  unsure).

## Execution notes

_(filled in by the executing agent)_
