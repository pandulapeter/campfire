# 05 · Every text edit rewrites the file's line endings and drops its trailing newline

**Severity:** high (whole-file diffs, and the cause of the false conflicts in 06) · **Area:** `:chordpro`

## Symptom

- Tap a tag chip on a Windows-authored (CRLF) file: every line of it is rewritten with LF.
- Any file ending in a newline loses it on the first tag / language / transpose action. After that, re-importing
  your own export reports it as conflicting (issue 06), and a sync uploads a whole-file change for a one-line edit.
- In the editor, "transpose text" moves a caret that was on the trailing empty line.

The `:chordpro` contract says these operations leave "every other byte exactly as it was".

## Cause

`ChordProSyntax.splitLines` (`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt:81–85`)
folds `\r\n` and `\r` to `\n` and drops the final empty line; the four editors rejoin with `"\n"`:

- `ChordProTags.addTag` / `removeTag` (`ChordProTags.kt:29–31, 41–43`)
- `ChordProLanguages.setLanguages` (`ChordProLanguages.kt:48`)
- `ChordProTransposer.transposeText` (`ChordProTransposer.kt:131`)
- `ChordProHeader.insert` (`ChordProHeader.kt:62–65`) always inserts with `\n`.

## Fix

1. In `ChordProSyntax` add two helpers next to `splitLines`:

   ```kotlin
   /** The line separator the text is written with: CRLF where any line ends that way, LF otherwise. */
   fun lineSeparatorOf(text: String) = if (text.contains("\r\n")) "\r\n" else "\n"

   /** Whether the text ends with a line break, which [splitLines] does not report as a line. */
   fun endsWithLineBreak(text: String) = text.endsWith("\n") || text.endsWith("\r")

   /**
    * The inverse of [splitLines] for one particular [original]: the same separator, and the trailing line break
    * put back where the original had one, so that an edit of one line leaves every other byte as it was.
    */
   fun joinLines(lines: List<String>, original: String): String {
       val separator = lineSeparatorOf(original)
       val joined = lines.joinToString(separator)
       return if (endsWithLineBreak(original) && lines.isNotEmpty()) joined + separator else joined
   }
   ```

   A file mixing CR-only and CRLF endings is rewritten to whichever `lineSeparatorOf` picked; that is acceptable and
   worth a sentence in the KDoc.

2. Replace every `joinToString("\n")` in the four editors with `ChordProSyntax.joinLines(lines, text)`, where `text`
   is the input string of that function. In `ChordProLanguages.setLanguages` the early return
   `if (missing.isEmpty() && kept.size == lines.size) return text` stays.

3. `ChordProHeader.insert`: use `lineSeparatorOf(text)` for the break it inserts (`"$opening$suffix$separator"` and
   `"$separator$prefix"`), and compute `offset` from lines split with the same separator (the current
   `text.split('\n')` counts one byte per break, so offsets are wrong on CRLF files by one per line above the
   insertion; use `splitLines` and add `separator.length` per line instead of `+ 1`).

4. Tests in `chordpro/src/commonTest` (`ChordProTagsTest` / `ChordProLanguagesTest` / `ChordProTransposerTest` /
   `ChordProHeaderTest`, whichever exist — add where missing):
   - `addTag("{title: A}\r\nla\r\n", "x")` == `"{title: A}\r\n{tag: x}\r\nla\r\n"`
   - `transposeText("[Am]la\n", 1)` ends with `"\n"`; `transposeText("[Am]la", 1)` does not.
   - `removeTag` on a CRLF file keeps CRLF.
   - `ChordProHeader.insert` on a CRLF file yields the right `offset` and `caretOffset`.

5. Docs: `chordpro/CLAUDE.md` — the byte-preservation sentence now holds; say that the separator is detected per file.

## Verification

Unit tests above; then desktop: create a CRLF file by hand in the library folder, tag it from the details header, and
diff the file: exactly one added line.
