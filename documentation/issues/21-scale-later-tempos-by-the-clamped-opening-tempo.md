# Scale a later tempo by the song's opening tempo as the click plays it, held to 30–300

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the change's own tempo is held to 30–300 too, not only the opening one: with plan 04's fill (a change's tempo side filled with the song's own) a clamped opening alone would scale a filled `400` by `300` and play an override of 150 as 200; two tests added.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/SongTempo.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/SongTempoTest.kt`

## Problem

An override (a setlist's entry or the library's) holds the song's opening tempo, and a later `{tempo}` keeps its ratio
to the file's opening one. `ChordProSong.withTempo` (`SongTempo.kt:102-111` at 1c52e5347) reads that opening tempo
unclamped:

```kotlin
internal fun ChordProSong.withTempo(bpm: Int?): ChordProSong {
    if (bpm == null) return this
    val songFileBpm = ChordProTempo.parse(metadata.tempo)
    ...
        if (block is ChordProBlock.Timing) block.copy(tempo = sectionBpm(ChordProTempo.parse(block.tempo), songFileBpm, bpm).toString()) else block
```

while everything else reads it held to the click's range: `effectiveTempo` (`SongTempo.kt:83`,
`song?.tempo?.let(MetronomePattern::coerceBpm)`) is what the stepper starts from and what decides that an override
equal to it is no override, and the metadata line shows `coerceBpm`'d too (`SongMetadata.kt:162`).

So a file with `{tempo: 400}` and a later `{tempo: 200}`: the stepper reads 300 (and with no override the change plays
200, since `withTempo(null)` changes nothing). Stepped to 150, `tempoOverride = 150` and the change is scaled by the
file's 400 rather than the 300 the song opens at: `sectionBpm(200, 400, 150) = 75` (verified with a probe through
`ChordProParser.parse(...).withTempo(150)`, which wrote `Timing(tempo=75)`), where the ratio the reader hears at the
top — 300 → 200 — gives 100. One step down from 300 to 299 drops the change from 200 to 150, a jump the stepper never
otherwise makes. The PDF export (`CampfireViewModel.kt:3364`) goes through the same function.

## Fix

Hold both of the file's tempos to the click's range before one scales the other, in `withTempo` — the opening one and
the change's — since the ratio the reader hears without an override is between the two as the click plays them
(`RenderSection.Timing` and `toSongTiming` already hold a change's tempo to 30–300):

```kotlin
val songFileBpm = ChordProTempo.parse(metadata.tempo)?.let(MetronomePattern::coerceBpm)
…
block.copy(tempo = sectionBpm(ChordProTempo.parse(block.tempo)?.let(MetronomePattern::coerceBpm), songFileBpm, bpm).toString())
```

Clamping the opening alone is not enough once plan 04 lands: it fills a change's missing tempo with the song's own,
so a song whose own tempo is `400`, named below a `{time}` change, has `Timing(tempo = "400")`; with the opening clamped
to 300 and the change not, an override of 150 plays that stretch at `400 * 150 / 300 = 200`, though nothing in the file
changes the tempo there. With both clamped it is `300 * 150 / 300 = 150`. The same holds without plan 04 for a file
going from 120 to 400: unclamped, halving the opening to 60 plays 200 there, while the reader heard 120 → 300 and gets
150 with both held.

`sectionBpm` itself stays as it is (its `songFileBpm == playedBpm` shortcut then also catches an override equal to the
clamped opening tempo, matching `effectiveTempo`). Mention the clamping in `withTempo`'s KDoc ("the file's tempos as the
click plays them"). No CLAUDE.md change: the root's "a later tempo keeps its ratio to the file's opening one"
still holds.

## Tests

In `SongTempoTest.kt`, next to `aLaterTempoKeepsItsRatioToTheOpeningOne`:

```kotlin
@Test
fun aLaterTempoKeepsItsRatioToTheOpeningOneAsTheClickPlaysIt() {
    val song = ChordProSong(
        metadata = ChordProMetadata(tempo = "400"),
        blocks = listOf(ChordProBlock.Timing(tempo = "200", time = null)),
    )
    assertEquals("100", (song.withTempo(150).blocks.single() as ChordProBlock.Timing).tempo)
}
```

and, in the same place, that a change at the song's own out-of-range tempo follows the override
(`ChordProMetadata(tempo = "400")` with `Timing(tempo = "400", time = "3/4")`, `withTempo(150)` → `"150"`) and that a
change out of range is held before it is scaled (`ChordProMetadata(tempo = "120")` with `Timing(tempo = "400")`,
`withTempo(60)` → `"150"`).

(The file already imports `ChordProBlock`, `ChordProMetadata` and `ChordProSong`.)

## Manual check

None needed beyond the test; optionally, a song with `{tempo: 400}` and a later `{tempo: 200}`, the stepper stepped
to 150: the page's later tempo line and the click on that page read 100.
