# 47 · A chord row above a tab is left untransposed if it contains `N.C.`

**Severity:** low · **Area:** `:chordpro` (`ChordProTabTransposer.rewriteChordLine`)

`rewriteChordLine` (`ChordProTabTransposer.kt:100–113`) bails out of the whole line on the first word that is neither
a `chordNameRegex` match nor a marker; `N.C.` (no chord) is neither. `{sot}\nAm   N.C.  G\ne|--0--3--|\n{eot}` at +2:
the staff moves, the row stays `Am   N.C.  G`.

## Fix

Add `N.C.` / `NC` / `N.C` (case-insensitive) to `isMarker`, with a constant and a comment: it is the one word a chord
row carries that names the absence of a chord. Add the input above to `ChordProTabTransposerTest`.

**Known limitation, deliberately not handled:** a bare tuning line (`E A D G B E`) is six valid chord names and is
transposed like a chord row. Distinguishing it from a real row of six chords needs a heuristic that would be wrong as
often as right; `Tuning: E A D G B E` is safe because `Tuning:` fails the regex. Note it in the KDoc.
