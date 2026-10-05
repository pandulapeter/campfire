# Say the tempo, time signature and capo the song is played at in the song's playing line, not the raw directive text

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongMetadata.kt` (`withMetadataSection`),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongMetadataTest.kt`,
`presentation/CLAUDE.md` (the read only line's description, one clause)

Lane D: apply before 36, which changes `withMetadataSection`'s callers. Read `SongMetadata.kt` at HEAD: a7bf0de72 changed it
(cover art button), not this function.

## Problem

In read only mode (performance mode, an archived setlist) and in the editor's preview the four playing values are one line
of accent text built in `SongPlayingMetadata` from what `withMetadataSection` hands it:

```kotlin
metadata.capo?.takeIf { readsCapoAndTime || it != 0 }?.let { stringResource(Res.string.song_details_capo, it) },
metadata.tempo?.takeIf { it.isNotBlank() }?.let { textResource(Res.string.song_details_tempo, it) },
metadata.time?.takeIf { it.isNotBlank() }?.let { textResource(Res.string.song_details_time, it) },
```

and `withMetadataSection` passes the directives through as written:

```kotlin
tempo = metadata.tempo.takeIf { shouldShowTempo },
time = when {
    !shouldShowTempo -> null
    readsBoth -> metadata.time?.takeIf { it.isNotBlank() } ?: TimeSignature.COMMON_TIME.toString()
    else -> metadata.time
},
```

(only an override is rewritten, as `bpm.toString()` by `withTempo`). `song_details_tempo` is `%1$s BPM`, and the click reads
the same directives through `ChordProTempo.parse` + `MetronomePattern.coerceBpm` and `ChordProTime.parse`
(`Song?.timeSignatureOrDefault`), with the capo held to `Song.CAPO_RANGE` (`effectiveCapo`). So the line contradicts what is
played and what a card says:

- `{tempo: 120 bpm}` → "120 bpm BPM"; `{tempo: ~96}` → "~96 BPM"; `{tempo: fast}` → "fast BPM";
- `{tempo: 400}` → "400 BPM" while the click plays 300;
- `{time: C}` → "C" while the click counts 4/4; `{time: 7/9}` → "7/9" while it counts 4/4;
- `{capo: 15}` → "Capo 15" while the capo is effectively 12.

## Fix

Normalize the three values in `withMetadataSection`'s `shownMetadata`, so every consumer of the metadata section (the read
only line, the editor preview, and `hasPlayingValues`) sees what the click and the steppers use:

```kotlin
tempo = ChordProTempo.parse(metadata.tempo)?.let(MetronomePattern::coerceBpm)?.toString()?.takeIf { shouldShowTempo },
time = when {
    !shouldShowTempo -> null
    else -> ChordProTime.parse(metadata.time)?.let { (beats, unit) -> TimeSignature(beats, unit).toString() }
        ?: TimeSignature.COMMON_TIME.toString().takeIf { readsBoth }
},
capo = when {
    !shouldShowChords -> null
    readsBoth -> (metadata.capo ?: 0).coerceIn(Song.CAPO_RANGE)
    else -> metadata.capo?.coerceIn(Song.CAPO_RANGE)
},
```

An unreadable tempo is then left out rather than shown; an unreadable time reads as the common time where the line always
names one (`readsBoth`) and is left out otherwise. The editable page is unaffected (its controls read `EffectiveTempo` /
`EffectiveCapo` already). The About the song sheet's Song defaults group is deliberately *not* changed: it says what the
file declares, and already parses the tempo (`ChordProTempo.parse(metadata.tempo)`), so it has no "BPM BPM".

Lane A (plans 01–03) changes which `{tempo}` / `{time}` directive counts in `:chordpro`; this plan only reads
`ChordProMetadata.tempo` / `.time`, so the two compose in either order.

Add to the read only line's description in `presentation/CLAUDE.md`: the values are read the way the click reads them
(number, clamped range, a `C` as 4/4).

## Tests

In `SongMetadataTest`, using the existing `withMetadataSection(...)` style and reading the `RenderSection.Metadata`'s
`metadata`:
- `tempo = "120 bpm"` → `"120"`; `"400"` → `"300"`; `"fast"` → `null` (no section where nothing else is set);
- `time = "C"` → `"4/4"`; `time = "7/9"` with `readsCapoAndTime = true` → `"4/4"`, without → `null`;
- `capo = 15` → `12`; `capo = -1` → `0`, which creates no section on its own without `readsCapoAndTime` (as a file capo of 0 does today).
The existing "playing metadata" test (`"96"`, `"6/8"`, capo 2) keeps passing unchanged.

## Manual check

Give a song `{tempo: 120 bpm}`, `{time: C}` and `{capo: 15}`, switch on Read only in Settings → Features and open it: the
line reads "Capo 12 • 120 BPM • 4/4". The editor's preview of the same text reads the same.
