# 52 · A German-notated song that never needs an `H` is read as English: its `B` (B♭) is drawn as `H` and transposed as B natural

**Severity:** wrong behaviour (all platforms. German/Hungarian charts in flat keys — F, B♭, d, g, c — which have no `H` chord; with the German notation preference on, the viewer draws a wrong chord even untransposed, and every transposition moves that chord to the wrong note, in the viewer and through the editor's Transpose in the file) · **Area:** `:chordpro` (`ChordProNotation.isGermanNotated`, and documentation)

## Decision (taken by the user on 2026-09-22)
**A. Keep the per-song `H` rule and document the limit.** `chordpro/CLAUDE.md` says what such a song is read as
and what the user can do about it (write the `B` of such a song as `Bb`, or add any `H` chord). No behaviour change,
and no second, harmonic signal that could misread an English song.

## Symptom
A Hungarian song in F: `[F] … [B] … [C] … [Dm]`, where `B` means B♭.
1. It has no `H`, so it is read as English and `B` is B natural.
2. With the German notation preference on, the viewer draws `[H]`: a wrong chord, untransposed.
3. Transposing +2 gives `G … C# … D … Em`: the B♭ became C♯ instead of C.

## Cause
`ChordProNotation.isGermanNotated` (`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotation.kt:57-58`)
and `normalized` (`:77-87`) take a song for German only when a chord uses `H`; `ChordProParser.scan` does the same
for the key (`ChordProParser.kt:90-101`). A song in a flat key never needs an `H`, so it is read as English, and
`toGerman` then draws its `B` as `H` (`:89-99`).

## Fix
Documentation only.

`chordpro/CLAUDE.md`, the `ChordProNotation` bullet, after "It runs after the transposition, never before, because
the transposition works in the notation the file is written in.": add

"A file is taken for German-notated when one of its chords uses `H` (a key counts too), and for nothing else, since
the file carries no marker: a German chart in a flat key, which never needs an `H`, reads as English, so the `B` in
it is B natural — drawn as `H` with the German notation preference on and transposed as B. Writing that chord `Bb`,
or any `H` chord in the song, is what tells the two apart."

## Tests
None (no behaviour change). The existing `a song says it is German notated with an H chord anywhere` pins the rule.

## Verify
Read the new paragraph against `ChordProNotation.isGermanNotated` and `ChordProParser.scan`.

## Docs
As in Fix.

## Touches
- `chordpro/CLAUDE.md`

## Depends on
None. If 23 lands first, "a chord uses `H`" also covers a lowercase `h`; the sentence stays true.
