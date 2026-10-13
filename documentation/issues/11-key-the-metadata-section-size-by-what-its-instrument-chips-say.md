# Key the metadata section's measured size by what its instrument and tuning chips say, not only by whether it has them

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** amended — the content's tuning is the tuning chip *drawn*, held while it fades out: a leaving chip is
still laid out in the frame the new content is first measured in, so keying by the tuning picked alone cached the old
height for the new content and left Guitar → Keyboard (the case the plan names) unfixed. Adds step 4 and
`InstrumentChips.kt` / `SongInstrumentChips.kt` to the files.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/RenderSection.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongInstrumentChips.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/InstrumentChips.kt` (Lane C's
file, one optional parameter; plan 23 edits the same `AnimatedVisibility` after this lands),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/ChordCells.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/RenderSectionsTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/CLAUDE.md`

## Problem

The song details page measures each section once per content and reuses the size for equal content
(`SectionSizesPool`, `SectionMeasurements.kt`: `sizesBySection[section]?.removeFirstOrNull()`, keyed by `UnitContent`,
which holds the `RenderSection`). The pool itself is only rebuilt for the text style, density, and section labels
(`SongLyrics.kt`: `remember(fontScale, density, lyricsStyle, headerStyle, defaultLabels) { SectionSizesPool<UnitContent>() }`).

The instrument and tuning chips are drawn at the top of the metadata section, and the section says only *whether* it has
them (`RenderSection.kt` at 72d48deb7):

```kotlin
data class Metadata(
    val metadata: ChordProMetadata,
    val hasPlayingControls: Boolean = false,
    val readsDefaults: Boolean = false,
    val hasTuner: Boolean = false,
    val hasMetronome: Boolean = false,
    val hasInstrumentChips: Boolean = false,
) : RenderSection
```

set by `withChordsSection` (`ChordCells.kt`: `if (hasChords && isInstrumentShown) it.copy(hasInstrumentChips = true)`).
Its own KDoc gives the rule this breaks: "which is part of the content because the measured sizes of a section are kept
by it". But how tall the chips are depends on what they say: the instrument's name ("Guitar", "12-string guitar",
"Ukulele"), whether there is a tuning chip at all (only for an instrument with more than one tuning: none for the
keyboard), and the tuning's label in the reader's notation ("Standard tuning", "Drop D", "Drop H"; Hungarian
"Normál hangolás", "Egész hanggal lejjebb"). A chip that does not fit beside the other goes under it whole
(`ui/chords/CLAUDE.md`), so a longer label can add a row.

So after Guitar → Keyboard (the tuning chip leaves, one row less) or, at 360dp in Hungarian, "Normál hangolás" →
"Egész hanggal lejjebb" (the tuning chip wraps under the instrument's), the metadata section is equal to the one before
and keeps the old measured height. The page is planned with the wrong height: the first row of the grid is taller (or
shorter) than planned, and content overflows the page or a page is cut mid-row. Guitar → 12-string guitar changes no
model at all (same `ChordTuning`), so nothing else invalidates it either.

## Fix

Replace the Boolean with what the chips show:

1. `RenderSection.kt`: add an `@Immutable data class InstrumentChipsContent(val instrument: UserPreferences.Instrument,
   val tuning: InstrumentTuning?, val notation: ChordNotation)` (KDoc: what the chips at the top of the first section
   name, part of the section's content because their width decides how many lines they take), and in `Metadata` replace
   `hasInstrumentChips: Boolean = false` with `instrumentChips: InstrumentChipsContent? = null`. `tuning` is the one the
   tuning chip shows, so null where `InstrumentChips` shows none (`instrument.tunings.size <= 1`), so that two
   instruments without a tuning chip and with the same name width are not told apart for nothing.
2. `ChordCells.kt` `withChordsSection`: replace `isInstrumentShown: Boolean` with `instrumentChips: InstrumentChipsContent?`
   (null where the chips are not offered) and set `it.copy(instrumentChips = instrumentChips)` where it now sets the
   Boolean; update the KDoc.
3. `SongLyrics.kt`: build the value from `chordDiagrams` where `onInstrumentClicked != null`:
   `InstrumentChipsContent(chordDiagrams.instrument, <the tuning of step 4>, chordDiagrams.notation)`,
   pass it to `withChordsSection` and key that `remember` on it instead of `isInstrumentShown`; at the
   `is RenderSection.Metadata ->` branch read `section.instrumentChips != null` where it read `section.hasInstrumentChips`.

4. **The tuning in the content is the one the tuning chip draws, not the one picked.** The pool measures a new
   content once, by `maxIntrinsicHeight`, in the frame it first appears (`SectionMeasurements.height`'s `getOrPut`), and
   `InstrumentChips` keeps a leaving tuning chip composed while it fades out (`AnimatedVisibility(exit = fadeOut())`,
   which keeps its size). So after Guitar → Keyboard the content `(KEYBOARD, null)` would be measured *with* the fading
   chip, and that height kept for as long as the content stays: the very row the plan sets out to drop. (An arriving chip
   is composed in its first frame, so the other direction is right either way.) Hoist the chip's visibility:
   - `InstrumentChips`: add `tuningChipVisibility: MutableTransitionState<Boolean>? = null` (after `isEnabled`), whose
     target the caller keeps at whether there is a tuning to show; where it is given, the chip's `AnimatedVisibility`
     takes it (`visibleState = …`), and where it is not, the `visible = shownTuning != null` form stays, so the Tuner
     tab, the Chords tab, Settings, and the onboarding are unchanged. KDoc: for a caller that measures the chips and so
     has to know when a leaving chip is no longer laid out.
   - `SongInstrumentChips`: take the same parameter and pass it on.
   - `SongLyrics.kt`, where it builds the content: `val shownTuning = chordDiagrams.instrumentTuning?.takeIf { chordDiagrams.instrument.tunings.size > 1 }`,
     `val tuningChipVisibility = remember { MutableTransitionState(shownTuning != null) }.apply { targetState = shownTuning != null }`
     (the documented composition-time pattern, written before anything reads it), passed to `SongInstrumentChips`, and a
     plain holder of the last non-null `shownTuning` (like `LastTuning`); the content's tuning is
     `shownTuning ?: lastShownTuning.value.takeIf { !tuningChipVisibility.isIdle || tuningChipVisibility.currentState }`.
     `currentState` is snapshot state, so the content changes again, to `tuning = null`, in the frame the fade ends,
     and only then is the section measured without the chip; a picked tuning that keeps the chip (Standard → Drop D)
     changes the content at once, as it should, since the label swaps in one frame.
   Plan 23 (Lane C) later reads whether the chip is leaving inside the same `AnimatedVisibility`; `shownTuning == null`
   still answers that, so the two compose.

The language is already covered: the pool is rebuilt with `defaultLabels`, which are the in-app language's.

The whole chips flow fading out of `SongMetadataSection` (`instrumentChips` becoming null: the chords switched off, or a
song that loses its last chord) has the same measuring frame; it is not in this plan's scope, and it is not made worse.

Plan 10 also edits `SongLyrics.kt` and `ChordCells.kt`; land plan 10 first.

## Tests

`RenderSectionsTest.kt`, the test "a song with chords has the instrument chips at the top of its first section, with
diagrams or without": change it to pass an `InstrumentChipsContent` and assert `instrumentChips != null`, and add a test
that `withChordsSection` with `InstrumentChipsContent(GUITAR, InstrumentTuning.GUITAR, STANDARD)` and with
`InstrumentChipsContent(GUITAR_TWELVE_STRING, InstrumentTuning.GUITAR, STANDARD)`, with `(GUITAR, InstrumentTuning.GUITAR_DROP_D, STANDARD)`,
and with `(GUITAR, InstrumentTuning.GUITAR_DROP_B, GERMAN)` against `STANDARD` give first sections that are not equal,
while the same content twice gives equal ones (what the pool relies on).

## Manual check

At 360dp wide (a small phone or the 720x1280/320dpi emulator), app language Hungarian, open a song with chords whose
first page is full. Pick a tuning with a long name (Egész hanggal lejjebb) from the tuning chip, then back to Normál
hangolás; then pick Billentyű (keyboard) and back to Gitár, and 12 húros gitár. With a text size large enough that
the tuning chip sits under the instrument's, pick Billentyű: once the chip has faded, the section closes up by that row
and the rest of the page moves up with it. After each pick the first section ends
where its content ends: no line pushed off the page bottom, no gap left under the chips, and paging (Down) still steps
by whole rows.
