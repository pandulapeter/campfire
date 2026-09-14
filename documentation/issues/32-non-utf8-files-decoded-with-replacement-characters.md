# 32 · Non-UTF-8 library files are read with replacement characters and written back that way

**Severity:** medium (silent corruption on the first save) · **Area:** `:data:source:local:implementation` (all four storages), `:domain:implementation`

## Cause

`JvmFileStorage.readText` (`File.readText()`), `FileStorage.ios.kt:84` (`decodeToString()`) and
`FileStorage.wasmJs.kt:166` (`file.text()`) all substitute U+FFFD for invalid sequences and never say so. A
Windows-1252 `.cho` dropped into the desktop or iOS library folder shows "�" for every accented letter, and the first
save (a tag toggle, the editor) persists the "�" over the original bytes. The import path
(`PrepareImportUseCaseImpl.text()`, `throwOnInvalidSequence = true`) rejects such files instead, which is
inconsistent and also unhelpful: most of them are perfectly readable Latin-1 / Windows-1252.

## Fix

Decode strictly, and fall back to Windows-1252 — the superset of Latin-1 that every "legacy" Western text file
turns out to be — in one shared place.

1. In `:data:source:local:implementation` commonMain, next to `FileStorage.kt`, add

   ```kotlin
   /**
    * The text of a library file. UTF-8 first, since that is what Campfire writes; a file that is not valid UTF-8 is
    * read as Windows-1252, which is what every other Western text file is, rather than as a row of replacement
    * characters that the next save would write back over the user's accents.
    */
   internal fun ByteArray.decodeLibraryText(): String = try {
       decodeToString(throwOnInvalidSequence = true)
   } catch (exception: CharacterCodingException) {
       decodeWindows1252()
   }.withoutByteOrderMark()
   ```

   with `decodeWindows1252()` a pure-Kotlin table decode: bytes 0x00–0x7F and 0xA0–0xFF map to the same code point,
   0x80–0x9F through a 32-entry table (€ ‚ ƒ „ … † ‡ ˆ ‰ Š ‹ Œ Ž ‘ ’ “ ” • – — ˜ ™ š › œ ž Ÿ; the five undefined
   slots map to U+FFFD). Unit-test it against a few known byte sequences.
2. Each `readText` actual becomes `readBytes(...)?.decodeLibraryText()` (JVM, iOS, web). On the web `readFileBytes`
   already exists; drop `readFileText`.
3. `PrepareImportUseCaseImpl.text()` uses the same rule: expose it as `ImportedFile.text()` in `:data:model` or
   route it through a `DecodeLibraryTextUseCase` — the simplest is to move the decoder to `:data:model` (it is pure
   Kotlin, `LibraryFiles` already lives there) so both the local source and the use case call it.
4. A file that is read through the fallback is written back as UTF-8 on its first save; that is an upgrade, not a
   loss, and worth a sentence in `data/source/local/implementation/CLAUDE.md`.

## Verification

Put a Windows-1252 file with `é` and `ő` (the latter is *not* in 1252; use `ő` only in the UTF-8 control file) into
the desktop library folder: the list and the details screen show `é`; tag it; the file is now UTF-8 with `é` intact.
Import the same file: it imports rather than being skipped.
