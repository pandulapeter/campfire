# 25 · With a key change, the list shows the song's last key, not its first

**Severity:** minor (all platforms. Songs that declare a second `{key}` for a modulation; the list and the song header show the wrong key, and a transposition spells the whole song for the final key) · **Area:** `:chordpro` (`ChordProParser.MetadataBuilder`)

## Symptom
A song with `{key: F}` in its header and `{key: A}` before the last chorus:
- the song list and the song details header show `A`;
- transposing it spells every chord for the key the song ends in: with `{key: F}` … `{key: A}` and `[C]`, +1 gives
  `Db` (A→B♭ wants flats) instead of the `C#` that the song's own F→F♯ asks for.

## Cause
The spec: "each specification is assumed to apply from where it was specified", i.e. the first `{key}` is the key
the song starts in. `MetadataBuilder.consume` keeps the last one
(`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt:362`: `"key" -> key = value`),
and `prefersFlats` (`ChordProTransposer.kt:241-246`) decides from `song.metadata.key`.

## Fix
`ChordProParser.kt:362`:
```kotlin
// A later key is a modulation from where it stands; the song is in the key it starts in.
"key" -> if (key.isNullOrEmpty()) key = value
```
(`isNullOrEmpty` rather than `== null`, so that a `{key: }` still waiting to be typed into does not hide a real key
after it, as it does not today.)

Nothing else: the text transposition still renames every `{key}` line, and the model transposition renames the one
key the model carries. The mid-song key change is not drawn by the viewer today, and this does not change that.

## Tests
`ChordProParserTest.kt`, new `a song is in the key it starts in`:
```kotlin
val text = "{key: F}\n[C]a\n{key: A}\n[E]b"
assertEquals("F", ChordProParser.parse(text).metadata.key)
assertEquals("F", ChordProParser.summarize(text).metadata.key)
assertEquals("G", ChordProParser.parse("{key: }\n{key: G}").metadata.key)
```
`ChordProTransposerTest.kt`, new `a modulation does not decide the spelling of the whole song`:
`ChordProTransposer.transpose(ChordProParser.parse("{key: F}\n[C]a\n{key: A}\n[E]b"), 1)` has the chords `C#` and
`F`, and the key `F#`.

## Verify
A song with two `{key}` directives shows its first key in the list and in the header. `./gradlew :chordpro:desktopTest`.

## Docs
`chordpro/CLAUDE.md`, `ChordProParser` bullet, after "Unknown directives are ignored;": "a song that changes key is
in the key its first `{key}` names;".

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `chordpro/CLAUDE.md`

## Depends on
None. 21 edits the same builder (`meta` branch); run one after the other.
