# Start no page at a change of tempo or time written before the song's first line, and play it from the first page

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** sound — consistent with plan 01 as amended (a leading empty `{chorus}` recall heading is a `RenderSection.Lines` here and a line of the song there); plan 22 must land after this one and keep `timingStarts.size > 1` as its test.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/RowSnapping.kt` (KDoc of
`SongRows.timingSections` only),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/RenderSectionsTest.kt`,
`presentation/CLAUDE.md`, `CLAUDE.md` (Metronome section)

## Problem

A change of tempo or time signature (`RenderSection.Timing`) starts a stretch of the song, and every stretch starts a
page (`SongLyrics.kt:2077` at 1c52e5347, in `SongUnits.of`):

```kotlin
timingStarts = (listOf(0) + sections.indices.filter { it > 0 && sections[it] is RenderSection.Timing }).distinct().toIntArray(),
```

`SongUnits.of` is handed the sections *after* `withMetadataSection` (`SongLyrics.kt:230`), so on the song details
screen section 0 is always the `RenderSection.Metadata` (the line or the controls of key, capo, tempo and time). A
change written before any line of the song is played therefore makes stretch 0 hold the metadata alone, or the
metadata and a comment. Verified with a probe through `prepareSongLyrics` + `withMetadataSection`:

| Text after the header `{tempo: 120}` | Sections | `timingStarts` |
| --- | --- | --- |
| `{c: Slowly}` `{tempo: 60}` `{sov}` `[G]la` `{eov}` `{tempo: 90}` `{sov}` … | `[metadata, comment, timing 60, lines, timing 90, lines]` | `[0, 2, 4]` |
| `{sov}` `{tempo: 60}` `[G]la` `{eov}` … | `[metadata, timing 60, lines, …]` | `[0, 1]` |
| `{soc}` `{tempo: 60}` `[G]la` `{eoc}` | `[metadata, timing 60, lines]` | `[0, 1]` |

The second shape is what the editor's Tempo shortcut writes with the caret on a verse's first line: it inserts
`{tempo: }` at the start of the caret's line, which is between `{start_of_verse}` and the line (lane A's plan 01 may
change the shortcut for a caret before the body's first line, but a file can carry such a change whatever the editor
does, so this plan stands on its own).

`searchGrid` (`SongLyrics.kt:1608-1620`) lays each stretch out as a song of its own and puts the grids end to end:

```kotlin
val grids = timingStarts.indices.map { index ->
    val from = timingStarts[index]
    val until = timingStarts.getOrElse(index + 1) { sectionCount }
    searchSegment(totalWidth, units.slice(from, until), sectionOffset = from, availableHeightPx = maxRowHeightPx).grid
}
```

so the song opens on a page holding nothing but the metadata (and the comment) — a mostly empty page, which the
layout's rules never accept otherwise — and the reader has to step once before the first line. And since
`timingSections = units.timingStarts.drop(1)` (`SongLyrics.kt:1806`) puts the change at section 2 (or 1), `timingIndexAt`
(`RowSnapping.kt:109`) answers -1 on that first page: the click and the app bar's tempo play the song's opening 120
there, though not one line of the song is played at it, and switch to 60 only on the page that holds the first line.

`withoutEmptyTimings` (`SongLyrics.kt:2244`) already drops a change no line follows; nothing handles one no line
precedes.

For comparison, the PDF (`PrintLayout.kt:297`, `timingRows` kept with what follows) and the editor's preview (never
paged) both show a leading change as a line in place, before the first section — which is what the page should do too.

## Fix

A change that no `RenderSection.Lines` comes before starts no stretch (it is a line of the first page, in place, like
in the PDF and the preview), and it is in force from the song's first page: `SongRows.timingSections` reports it as
section 0, so `timingIndexAt` returns its index from the first stop.

Options:

1. **A pure helper next to `withoutEmptyTimings` (recommended).** In `SongLyrics.kt`:

   ```kotlin
   /**
    * Where the stretches of [sections] start ([starts], 0 and every change some line of the song comes before) and the
    * section each change is in force from ([timingSections], one per RenderSection.Timing in order: its own section,
    * or 0 for a change written before the song's first line, which is played from the first page).
    */
   internal class TimingStretches(val starts: IntArray, val timingSections: List<Int>)

   internal fun timingStretchesOf(sections: List<RenderSection>): TimingStretches
   ```

   One pass: a flag set at the first `RenderSection.Lines`; each `Timing` adds `if (seenLines) index else 0` to
   `timingSections` and, where `seenLines`, `index` to `starts`. `SongUnits.of` takes `starts` as `timingStarts`
   and keeps `timingSections` in a new field, which `onRowsPlaced` passes as `SongRows.timingSections` in place of
   `units.timingStarts.drop(1)`. `SongUnits.isTiming` (used by `isNarrowSection`, `SongLyrics.kt:1456`, so that a change
   is never half of a side-by-side pair) must keep answering true for **every** `Timing` section, the leading one
   included — base it on the section's type (e.g. a `BooleanArray` or the set of real `Timing` indices), not on
   `timingStarts`. The list of `timings` in `SongDetailsScreen.kt:1098` (`model.sections.filterIsInstance<RenderSection.Timing>()`)
   stays index-aligned with `timingSections`, since both have one entry per `Timing` in order; two leading changes (a
   comment between them keeps the parser from merging them) both map to 0 and `indexOfLast { it <= 0 }` picks the later
   one, which is the one in force.

2. **Hoist the leading change in `withoutEmptyTimings`** into the opening (drop the `Timing` and play it as the song's
   tempo). Rejected: the metadata line and the stepper hold the file's *opening* tempo, which an override is stored
   against and which `sectionBpm` scales later changes by; rewriting it would make the stepper show 60 for a file whose
   `{tempo}` says 120, and the read only line would hide that the song changes at all.

The helper is pure, so it is tested (below), while the layout code just consumes it. Update the KDoc of
`SongUnits` (`timingStarts`, `SongLyrics.kt:2008-2010`), of `RenderSection.Timing` (`SongLyrics.kt:2432-2436`, "Always a
unit of its own … since it starts a page of its own": only where a line of the song comes before it) and of
`SongRows.timingSections` (`RowSnapping.kt:63-64`: a leading change is reported at section 0 and starts no stop).

Docs: in `presentation/CLAUDE.md`, after "The layout searches each stretch from one change to the next as a song of its
own … each starting a page", add that a change written before the song's first line starts no page: it stands in place
on the first one, as in the preview and the PDF, and the click plays it from there. In the root `CLAUDE.md`'s Metronome
bullet "A later `{tempo}` or `{time}` is a change from where it stands", add the same in a short clause after "on the
song details screen a change starts a page of its own".

## Tests

In `RenderSectionsTest.kt` (it already has `shape(...)` over `prepareSongLyrics`), a test that builds the sections the
way `SongLyrics` does — `withMetadataSection(model.sections, model.song.metadata, shouldShowChords = true, isSongInfoShown = false)` —
and checks `timingStretchesOf`:

- `{tempo: 120}\n{c: Slowly}\n{tempo: 60}\n{sov}\n[G]la\n{eov}\n{tempo: 90}\n{sov}\n[G]la\n{eov}` → sections
  `[Metadata, Comment, Timing, Lines, Timing, Lines]`, `starts == [0, 4]`, `timingSections == [0, 4]`.
- `{tempo: 120}\n{sov}\n{tempo: 60}\n[G]la\n{eov}` → `[Metadata, Timing, Lines]`, `starts == [0]`, `timingSections == [0]`.
- `{tempo: 120}\n{sov}\n[G]la\n{tempo: 90}\n[G]la\n{eov}` → `starts == [0, 2]`, `timingSections == [2]` (unchanged
  behaviour for an ordinary change).
- No change at all → `starts == [0]`, `timingSections` empty.

The existing `RowSnappingTest` cases for `timingIndexAt` stay valid; add one with `timingSections = listOf(0, 4)`
asserting index 0 at offset 0.

## Manual check

On the desktop at 800×600 and on a phone, open a copy of the demo "House of the Rising Sun" with `{c: Slowly}` and
`{tempo: 60}` inserted on two lines just before `{start_of_tab: Picking pattern}`: the song opens on a page that holds
the key, capo, tempo and time controls, the "Slowly" comment, the line naming tempo 60 and the picking pattern, with no
page holding only those controls; with the metronome panel open, the click and the app bar's tempo read 60 on that first page
(check it reads 60 from the first frame rather than flipping from 76 as the page settles; if it flips, seed the page's
timing from the model before the rows are placed rather than animating over it). Then the same with `{tempo: 60}`
written right after `{start_of_verse}` of the first verse instead. With the Metronome feature off neither shows a line or
a page.
