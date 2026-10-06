# Convert every chord of a text typed in the Latin notation, comments, labels and `{define}` names included

**Challenged:** amended — also let a definition's Latin name vote in `hasLatinName`, so files this bug already wrote are healed by `normalized()` and the import instead of losing their definition once transposed; tests added.
**Kind:** bug (data)  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotation.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProLatinNotationTest.kt`, `chordpro/CLAUDE.md`

## Problem

`ChordProNotation.convertText` returns the text untouched when it finds nothing to convert:

```kotlin
fun convertText(text: String, from: ChordNotation, to: ChordNotation): String {
    val written = if (from == ChordNotation.GERMAN) null else ChordProParser.parseAsWritten(text)
    val isGerman = written == null || isGermanNotated(written)
    val isWrittenInStandard = to == ChordNotation.STANDARD || to.isNumbering
    if (!isGerman && isWrittenInStandard && text.none { it == SHARP_SIGN || it == FLAT_SIGN } && written?.let(::hasLatinName) == false) return text
```

`hasLatinName` only looks at `ChordProTransposer.writtenChordNames` (the key, the lyric, grid and tab chords) — by
design, so that a Latin word in a comment does not *vote* a standard file into Latin. But the rewrite below the
shortcut (`rewriteChordNamesInText`) does rename the brackets of comments and labels (`hasChordsInValue`) and the names
of `{define}` / `{chord}` lines. So converting **from** Latin skips them whenever the text has no other Latin chord.
Probe at dac1d9d59:

- `convertText("{c: Intro: [G] [D]}\nla la", STANDARD, LATIN)` → `{c: Intro: [Sol] [Re]}` (shown in the Latin editor),
  and back, `convertText("{c: Intro: [Sol] [Re]}\nla la", LATIN, STANDARD)` → unchanged, `[Sol] [Re]`.
- `convertText("{define: Lam base-fret 1 frets x 0 2 2 1 0}\nla", LATIN, STANDARD)` → unchanged `Lam`, while the same
  line next to a `[Do]` becomes `Am`.

A Latin reader who opens and saves such a song (`CampfireViewModel.fileTextOf`, also the stored editor draft and the
editor's transpose action) writes Latin names into a file the root CLAUDE.md promises is "in the standard chord
notation"; the transposer then no longer moves the comment's chords, a German or English reader sees `[Sol]`, and
sync spreads it. A new song where the Chord shape button wrote `{define: Lam …}` and nothing else yet loses its
definition's identity the same way.

## Fix

The shortcut is an optimization for texts read **from a file** (from the standard notation), where Latin names must
not vote. A text typed in Latin is Latin by definition, so never take the shortcut from it:

```kotlin
// A text typed in the Latin notation is in it throughout, comments and definitions included, so only a text read
// from a file may be returned as it is: there a Latin word in a comment does not make the file Latin.
if (from != ChordNotation.LATIN && !isGerman && isWrittenInStandard && …) return text
```

A file already written by this bug — `{define: Lam …}` over `[Am]`, which is what a Latin reader's save wrote until
now — is skipped by both shortcuts as well: `hasLatinName` reads `ChordProTransposer.writtenChordNames`, which holds no
definition names. In the model the definition keeps the name `Lam`, which `ChordProTransposer.transposeChord` does not
take for a chord (`isChordName("Lam")` is false), so a reader who transposes the song moves its keys but leaves the name,
and `songChordsOf` (by name, or by `parse(it.name) == chord`) no longer finds it: the song's own shape silently drops out
of the Chords section and the PDF. A definition's name is no prose, so unlike a comment it may vote: in
`hasLatinName`, also answer true where `song.metadata.definitions.any { ChordProChordNames.latinExpanded(it.name) != null }`.
That makes `normalized()` rename such a file's definitions into the standard notation when it is read (through plan
03's `renamedFromNotation` if it has landed, which shifts nothing here since `parse` already read the Latin root), and
`convertText(…, STANDARD, STANDARD)` — the import — write them in it. Comments still do not vote. Update the
`hasLatinName` KDoc ("a Latin name in a comment or a label only counts where another one does") to say a definition's
name counts.

In `chordpro/CLAUDE.md`'s Latin paragraph, after "a Latin chord is read as the chord it names wherever it arrives
from", add: "a text typed in Latin is converted whole, the brackets of its comments and labels and the names of its
definitions included, even where nothing else in it names a chord".

## Tests

`ChordProLatinNotationTest`, new `` `a Latin text's comments and definitions come back in the standard notation` ``:
- `convertText("{c: Intro: [Sol] [Re]}\nla la", LATIN, STANDARD)` == `"{c: Intro: [G] [D]}\nla la"`;
- `convertText("{define: Lam base-fret 1 frets x 0 2 2 1 0}\nla", LATIN, STANDARD)` == `"{define: Am base-fret 1 frets x 0 2 2 1 0}\nla"`;
- round trip: `convertText(convertText(t, STANDARD, LATIN), LATIN, STANDARD) == t` for
  `t = "{c: Intro: [G] [D]}\n{define: Am base-fret 1 frets x 0 2 2 1 0}\nla la"`;
- a Latin text with no chords at all, `"la la"`, comes back unchanged;
- a file already holding a Latin definition name: `ChordProParser.parse("{define: Lam base-fret 1 frets x 0 2 2 1 0}\n[Am]la").metadata.definitions.single().name` is `"Am"`, and after `ChordProTransposer.transpose(thatSong, 2)` the definition is named `"Bm"` (today it stays `"Lam"`); `convertText("{define: Lam base-fret 1 frets x 0 2 2 1 0}\n[Am]la", STANDARD, STANDARD)` == `"{define: Am base-fret 1 frets x 0 2 2 1 0}\n[Am]la"`;
- a Latin word in a comment of an otherwise standard file still does not vote: `convertText("{c: [La] la la}\n[A]x", STANDARD, STANDARD)` is unchanged (the existing tests of that rule keep passing).

## Manual check

Settings → Songs → Latin. New song, type `{c: Intro: [Sol] [Re]}` and a lyric line, Save. Switch the notation to
Standard and reopen the editor (or look at the file on the desktop): the comment reads `[G] [D]`.
