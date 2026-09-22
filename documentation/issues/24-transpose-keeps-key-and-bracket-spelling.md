# 24 · The editor's Transpose rewrites the user's `{key}` spelling and the spaces inside chord brackets

**Severity:** minor (all platforms. Only files written with `{KEY:G}`, `{ key : G }`, `{key G}` (after 19) or `[ G ]`; the chords are right, only the formatting changes, against the documented promise) · **Area:** `:chordpro` (`ChordProTransposer.transposeText`)

## Symptom
In the editor, on a song with `{KEY:G}` (or `{ key : G }`) and a chord written `[ G ]`, choose Transpose +2. The
file now says `{key: A}` and `[A]`: the directive's case and spacing and the spaces inside the bracket are gone.
`chordpro/CLAUDE.md` promises the text transposition keeps "every byte of formatting".

## Cause
`transposeKeyLine` writes a canonical directive instead of replacing the value
(`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt:342-351`):

```kotlin
val key = value?.trim().orEmpty()
if (key.isEmpty()) return rawLine
return rawLine.replaceTrimmedPart(trimmedLine, "{$KEY: ${rename(key)}}")
```

`rewriteLyricsLineChords` (`:308-322`) appends `rename(content)` where `content = bracket.content.trim()`, dropping
the whitespace inside the brackets.

## Fix
1. `ChordProTransposer.kt`, `transposeKeyLine`: replace only the value, which `matchDirective` returns trimmed and
   which therefore ends right before the whitespace in front of the closing brace:

   ```kotlin
   /**
    * Renames the value of a key directive in place. Only the value's own characters are replaced, so the directive
    * keeps the spelling and the spacing the file gives it; the value is the last thing before the closing brace, give
    * or take whitespace, however the directive is written.
    */
   private fun transposeKeyLine(rawLine: String, key: String, rename: (String) -> String): String {
       val valueEnd = rawLine.substring(0, rawLine.trimEnd().lastIndex).trimEnd().length
       return rawLine.substring(0, valueEnd - key.length) + rename(key) + rawLine.substring(valueEnd)
   }
   ```

   and the call in `rewriteText` (`:226-228`):

   ```kotlin
   if (directive.name == KEY) {
       directive.value?.takeIf { it.isNotEmpty() }?.let { key -> lines[index] = transposeKeyLine(rawLine, key, rename) }
   }
   ```

   (`rawLine.trimEnd().lastIndex` is the closing brace: the trimmed line ends with it.) `replaceTrimmedPart` stays,
   `transposeGridLine` still uses it.

2. `rewriteLyricsLineChords` (`:313-319`): keep the whitespace around the renamed name.

   ```kotlin
   brackets.forEach { bracket ->
       val content = bracket.content.trim()
       val isChord = content.isNotEmpty() && !content.startsWith(ANNOTATION_MARKER)
       append(rawLine, consumedUntil, if (isChord) bracket.range.first else bracket.range.last + 1)
       if (isChord) {
           // The spaces a file puts inside its brackets are its own formatting, and the transposition keeps it.
           val leading = bracket.content.length - bracket.content.trimStart().length
           append(BRACKET_OPEN)
               .append(bracket.content, 0, leading)
               .append(rename(content))
               .append(bracket.content, bracket.content.trimEnd().length, bracket.content.length)
               .append(BRACKET_CLOSE)
       }
       consumedUntil = bracket.range.last + 1
   }
   ```

   `transposedOffset` needs nothing: it maps by bracket ranges and by common prefix/suffix, both of which still hold.

## Tests
`ChordProTransposerTest.kt`, new `the key keeps the spelling of its directive and a chord the spaces in its brackets`:
```kotlin
assertEquals("{KEY:A}\n[ A ]la [Bm ]la", ChordProTransposer.transposeText("{KEY:G}\n[ G ]la [Am ]la", 2))
assertEquals("  { key : A }  ", ChordProTransposer.transposeText("  { key : G }  ", 2))
assertEquals("{key: }", ChordProTransposer.transposeText("{key: }", 2))
```
The existing `transposing text leaves comments and annotations alone and updates the key` (`{key: E}` → `{key: F}`)
must still pass. Also assert that a caret after the `G` of `{KEY:G}` maps to after the `A`
(`transposedOffset("{KEY:G}", "{KEY:A}", 6) == 6`).

## Verify
Editor: a song with `{KEY:G}` and `[ G ]`, Transpose +2 → `{KEY:A}` and `[ A ]`; undo restores the original.
`./gradlew :chordpro:desktopTest`.

## Docs
None: `chordpro/CLAUDE.md` already says the text transposition keeps every byte of formatting; this makes it true.

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`

## Depends on
None. 21 builds on the new `transposeKeyLine` for `{meta: key …}`; 22 and 26 edit `rewriteText` too: run them one
after another.
