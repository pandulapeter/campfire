# 06 · Re-importing your own export reports conflicts for byte-identical songs

**Severity:** high (misleading dialog, `_2` duplicates on "Keep both") · **Area:** `:domain:implementation`

## Symptom

Export the library, import the archive on the same device. Songs whose file does not end in exactly one `\n` (any
song ever tagged in the app, see 05; any hand-written file ending in a blank line or in no newline) come up as
"Some names are taken". **Keep both** duplicates them as `_2`; **Replace** rewrites them.

## Cause

`PrepareImportUseCaseImpl.planSongs` (`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/PrepareImportUseCaseImpl.kt:79–87`)
compares `existingText == part + "\n"`, while `ChordProSplitter.split` (`chordpro/.../ChordProSplitter.kt:27`) trims
blank lines from both ends of every part. So `…{end_of_verse}\n\n` and `…{end_of_verse}` both come back as
`…{end_of_verse}\n` and compare unequal to what is on disk. Leading blank lines do the same. CRLF files too.

## Fix

1. Add a private comparison that folds exactly what the splitter and the line-ending detection fold:

   ```kotlin
   /** Two texts are the same song when they differ only by line endings and by blank lines at either end. */
   private fun String.comparable() = ChordProSyntax.splitLines(this)
       .dropWhile { it.isBlank() }
       .dropLastWhile { it.isBlank() }
       .joinToString("\n")
   ```

   and use it: `existingText.comparable() == text.comparable() -> IDENTICAL`. (`:domain:implementation` already depends
   on `:chordpro`; if `ChordProSyntax.splitLines` is not public, expose it or add a `ChordProSplitter.comparable(text)`
   helper in `:chordpro` with a test.)

2. Keep writing `part + "\n"` for a `NEW` file: that is the canonical form and the comment above it still holds.

3. `plannedTexts` (the same-batch memo) should store the comparable form as well, or compare through `comparable()`
   on both sides — do the latter, it is one call.

4. Add a test if `:domain:implementation` gets a `commonTest` (it has none today; a fake `SongContentRepository` is a
   two-method interface, so a test of `planSongs` with `"a\n\n"` on disk and `"a"` incoming → `IDENTICAL` is cheap).
   Otherwise cover the helper in `:chordpro`.

## Verification

Desktop: export the library, import the same archive: the import must report every song as "already there" and
show no conflicts dialog.
