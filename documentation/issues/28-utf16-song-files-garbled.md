# 28 · A song file saved as UTF-16 (Notepad's "Unicode") shows as garbage, and the first change writes the garbage back

**Severity:** wrong behaviour, and the file's text is destroyed by the first save (all platforms; needs a UTF-16 file,
which is what Notepad's "Unicode" / "UTF-16 LE" / "UTF-16 BE" encodings and Windows PowerShell's `>` produce) ·
**Area:** `:data:model` (`LibraryText.kt`), tested from `:data:source:local:implementation`

## Symptom

1. On Windows, write a song in Notepad and save it with the encoding "UTF-16 LE" (called "Unicode" before Windows 10
   1903), or produce one with `Get-Content song.cho > copy.cho` in Windows PowerShell.
2. Import it, or drop it into the desktop library folder.
3. The song is titled by its file name instead of its `{title}`, and its text reads `ÿþ{ t i t l e :` … — every
   letter followed by a blank box, no directive recognised, no chords.
4. An import stores that text as UTF-8, so the library copy is garbage from the start. For a file dropped into the
   folder, the first tag or language change (or any save in the editor) rewrites the user's own file that way.

## Cause

`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryText.kt:22-26` knows two
encodings:

```kotlin
fun ByteArray.decodeLibraryText(): String = try {
    decodeToString(throwOnInvalidSequence = true)
} catch (_: CharacterCodingException) {
    decodeWindows1252()
}.removePrefix(BYTE_ORDER_MARK)
```

A UTF-16 file starts with the byte order mark `FF FE` (little endian) or `FE FF` (big endian). Neither byte can occur
in UTF-8, so the strict decode throws and the file is read as Windows-1252: `ÿþ{\u0000t\u0000i\u0000…`.
`removePrefix("\uFEFF")` does not match `ÿþ`, and `:chordpro` finds no directive in a text with a NUL between every
two letters. A UTF-16 file *without* a mark whose text is plain ASCII is even valid UTF-8 (NUL is a valid code point),
so it takes the first branch with the same result.

Every reader goes through this one function — `JvmFileStorage.readText` (both copies, line 58),
`FileStorage.ios.kt:91`, `FileStorage.wasmJs.kt:76`, and `PrepareImportUseCaseImpl.kt:76,124` for incoming files — so
it is the only place to fix.

## Fix

One file: `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryText.kt`. The common
standard library has no UTF-16 charset, so the decoder is written by hand like the Windows-1252 one next to it. A
Kotlin `Char` *is* a UTF-16 code unit, so decoding is pairing bytes up; the only real work is that a surrogate without
its partner must not get into the string, because what `encodeToByteArray` makes of one differs per platform (`?` on
the JVM, U+FFFD elsewhere) and the text is written back as UTF-8.

1. Replace `decodeLibraryText` and its KDoc:

   ```kotlin
   /**
    * The text of a library file, or of a file on its way into the library. UTF-8 first, since that is what Campfire
    * writes; a file that is not valid UTF-8 is read as Windows-1252, which is what every other Western text file is,
    * rather than as a row of replacement characters that the next save would write back over the user's accents. Before
    * either, a file that is UTF-16 is read as that: it is what Notepad calls "Unicode" and what Windows PowerShell
    * redirects into, and read as either of the other two it is a NUL between every two letters. Shared by the storage
    * layer and the import, so that a file reads the same whether it was dropped into the library folder or imported.
    *
    * A byte order mark is stripped, because editors on Windows like to prefix files with one and it is not part of the
    * content. Whatever a file was read as, it is UTF-8 once the app has written it.
    */
   fun ByteArray.decodeLibraryText(): String {
       val byteOrder = utf16ByteOrder()
       return if (byteOrder == null) {
           try {
               decodeToString(throwOnInvalidSequence = true)
           } catch (_: CharacterCodingException) {
               decodeWindows1252()
           }
       } else {
           decodeUtf16(byteOrder)
       }.removePrefix(BYTE_ORDER_MARK)
   }
   ```

   The mark of a UTF-16 file needs no handling of its own: decoded in the right byte order it *is* U+FEFF, which the
   existing `removePrefix` strips.

2. Add below `decodeWindows1252` (all `private`, same file):

   ```kotlin
   private enum class Utf16ByteOrder {
       LITTLE_ENDIAN,
       BIG_ENDIAN,
   }

   /**
    * The byte order of a file that is UTF-16, null for one that is not. A byte order mark settles it: neither of its
    * bytes can occur in UTF-8 at all, and as Windows-1252 they would be a text that opens with "ÿþ".
    *
    * A file without a mark is recognised by its NUL bytes. UTF-16 spells every character of the ASCII range - the
    * braces, colons, spaces and line breaks a song file is full of, whatever script its lyrics are in - as that
    * character and a NUL, always on the same side. A NUL is not a character of any text, so a file in UTF-8 or
    * Windows-1252 holds none and can never be taken for UTF-16 by this; what holds them on both sides is not text
    * (or is UTF-32, which nothing writes) and is left to the other two encodings. The share asked for is what keeps a
    * text file with one stray NUL in it from being read as a page of CJK.
    */
   private fun ByteArray.utf16ByteOrder(): Utf16ByteOrder? {
       if (size < 2) return null
       val first = this[0].toInt() and 0xFF
       val second = this[1].toInt() and 0xFF
       if (first == 0xFF && second == 0xFE) return Utf16ByteOrder.LITTLE_ENDIAN
       if (first == 0xFE && second == 0xFF) return Utf16ByteOrder.BIG_ENDIAN
       val sampledUnits = minOf(size, UTF_16_SAMPLE_SIZE) / 2
       var leadingZeros = 0
       var trailingZeros = 0
       for (unit in 0 until sampledUnits) {
           if (this[unit * 2] == ZERO_BYTE) leadingZeros++
           if (this[unit * 2 + 1] == ZERO_BYTE) trailingZeros++
       }
       return when {
           trailingZeros * UTF_16_MINIMUM_ZERO_SHARE >= sampledUnits && leadingZeros == 0 -> Utf16ByteOrder.LITTLE_ENDIAN
           leadingZeros * UTF_16_MINIMUM_ZERO_SHARE >= sampledUnits && trailingZeros == 0 -> Utf16ByteOrder.BIG_ENDIAN
           else -> null
       }
   }

   /**
    * A pairing of bytes rather than a platform charset, which the common standard library does not have: a [Char] is a
    * UTF-16 code unit, so a character outside the basic plane arrives as the surrogate pair it is stored as. A
    * surrogate without its partner becomes the replacement character, and so does the odd byte at the end of a file
    * that was cut short: what encoding a lone surrogate as UTF-8 produces differs between the platforms, and this
    * text is written back as UTF-8.
    */
   private fun ByteArray.decodeUtf16(byteOrder: Utf16ByteOrder): String {
       val units = CharArray(size / 2) { index ->
           val first = this[index * 2].toInt() and 0xFF
           val second = this[index * 2 + 1].toInt() and 0xFF
           when (byteOrder) {
               Utf16ByteOrder.LITTLE_ENDIAN -> (second shl 8) or first
               Utf16ByteOrder.BIG_ENDIAN -> (first shl 8) or second
           }.toChar()
       }
       val hasOddByte = size % 2 != 0
       return buildString(units.size + 1) {
           var index = 0
           while (index < units.size) {
               val unit = units[index]
               val next = units.getOrNull(index + 1)
               when {
                   unit.isHighSurrogate() && next != null && next.isLowSurrogate() -> {
                       append(unit).append(next)
                       index++
                   }
                   unit.isSurrogate() -> append(REPLACEMENT_CHARACTER)
                   else -> append(unit)
               }
               index++
           }
           if (hasOddByte) append(REPLACEMENT_CHARACTER)
       }
   }
   ```

   and next to `BYTE_ORDER_MARK`:

   ```kotlin
   private const val REPLACEMENT_CHARACTER = '\uFFFD'

   private const val ZERO_BYTE: Byte = 0

   /** How much of the start of a file is looked at for the NULs of UTF-16. A song's first directives are enough. */
   private const val UTF_16_SAMPLE_SIZE = 256

   /** One in this many of the sampled characters has to be in the ASCII range, which no song file falls short of. */
   private const val UTF_16_MINIMUM_ZERO_SHARE = 8
   ```

   `hasOddByte` is read before `buildString` because inside it the receiver is the `StringBuilder`, whose own
   length would answer to `size`-like calls; the `CharArray` initializer is an ordinary lambda, so `this` in it is
   still the byte array.

Why the mark-less heuristic is in, and why it cannot misfire on the text the app reads today:
- It only ever answers "UTF-16" for a sample that holds NUL bytes, on one side of every pair only, in at least one
  pair out of eight. Windows-1252 and UTF-8 *text* holds no NUL byte at all, so no file that the two existing branches
  read correctly changes. The files the app writes (songs, setlists, `preferences.json`, the sync index — JSON escapes
  a NUL) never hold one either.
- A binary file under a song extension (an AppleDouble `._x.cho` starts `00 05 16 07 00 02 00 00`) has NULs on both
  sides and is left to the old path; it was garbage before and is garbage after, and plan 27 stops listing it.
- The share (one in eight) is met by any ChordPro text: in a Cyrillic or Greek song the directives' braces, names and
  colons, the chords, the spaces and the line breaks are all ASCII. A Latin-script song is near eight in eight.
  `leadingZeros == 0` for little endian would be broken only by a character U+xx00 (`Ā`, `Ѐ`) in the first 128
  characters, in which case the file reads as it does today.
- Not handled on purpose: UTF-32 (nothing a musician uses writes it; its little-endian mark `FF FE 00 00` reads as
  UTF-16 with a NUL after every character, which is no worse than today) and UTF-16 without a mark in a script with
  next to no ASCII *and* no ChordPro markup in the first 256 bytes.

**What the first write-back does:** the file becomes UTF-8 without a mark, exactly as a Windows-1252 file does today
(`writeText` always encodes UTF-8). That is intended and loses nothing: every UTF-16 text has a UTF-8 spelling, and
every tool that reads ChordPro reads UTF-8. Until that first save the file's bytes are untouched — sync moves them as
bytes (`LibraryFileLocalSource.readLibraryFile`), and every device decodes them the same way. An *import* stores the
decoded text, so the library copy is UTF-8 from the start; the "same content" comparison of a re-import is made on
decoded text (`ChordProSplitter.comparable`), so a UTF-16 original and its UTF-8 library copy are still recognised as
the same song.

Do not move the detection into the storages or the import: `decodeLibraryText` is the one rule both share, which is
the point of it.

## Tests

`:data:source:local:implementation`, `commonTest`,
`storage/file/LibraryTextDecodingTest` (the decoder's tests live there, see the class KDoc). Add a helper next to
`bytes(...)`:

```kotlin
private fun utf16(text: String, isBigEndian: Boolean, hasMark: Boolean): ByteArray {
    val units = (if (hasMark) "\uFEFF" else "") + text
    return ByteArray(units.length * 2) { index ->
        val unit = units[index / 2].code
        val isHighByte = (index % 2 == 0) == isBigEndian
        (if (isHighByte) unit shr 8 else unit and 0xFF).toByte()
    }
}
```

Cases (camelCase names, as the rest of `commonTest`):
- `readsUtf16LittleEndianWithAByteOrderMark` — `utf16("{title: Tükörfúrógép}\r\n[Am]Őszi szél", false, true)` →
  exactly that text, no U+FEFF in front. Also assert the first four bytes are `FF FE 7B 00` so the helper is pinned.
- `readsUtf16BigEndianWithAByteOrderMark` — the same text with `isBigEndian = true` (starts `FE FF 00 7B`).
- `keepsCharactersOutsideTheBasicPlane` — text `"{c: 𝄞 segno}"` (U+1D11E, a surrogate pair in the source string) in
  both byte orders → equal to the input, and `decoded.encodeToByteArray()` contains `F0 9D 84 9E`.
- `replacesASurrogateWithoutItsPartner` — mark + units `D834 0041` → `"\uFFFDA"`; mark + `0041 DC00` → `"A\uFFFD"`;
  mark + `DC00 D834` (wrong order) → `"\uFFFD\uFFFD"`.
- `replacesTheOddByteOfAFileCutShort` — `bytes(0xFF, 0xFE, 0x41, 0x00, 0x42)` → `"A\uFFFD"`.
- `readsAFileThatIsOnlyAByteOrderMark` — `bytes(0xFF, 0xFE)` → `""`.
- `readsUtf16WithoutAByteOrderMark` — `utf16("{title: Ősz}\n{artist: Valaki}\n", isBigEndian, hasMark = false)` for both
  orders → the text.
- `readsCyrillicUtf16WithoutAByteOrderMark` — `"{title: Катюша}\n[Am]Расцветали яблони и груши\n"`, little endian, no
  mark → the text.
- `doesNotTakeTextWithAStrayNulForUtf16` — `("{title: A}\n" + "x".repeat(40)).encodeToByteArray()` with the byte at
  index 13 set to 0 → decodes to the same string with `\u0000` at index 13 (one NUL in 25 units is under the share).
- `doesNotTakeBinaryForUtf16` — `bytes(0x00, 0x05, 0x16, 0x07, 0x00, 0x02, 0x00, 0x00)` → the 8-character
  Windows-1252/UTF-8 reading (`"\u0000\u0005\u0016\u0007\u0000\u0002\u0000\u0000"`), i.e. length 8, not 4.
- The five existing cases stay unchanged and green: they are what pins "Windows-1252 text is never taken for UTF-16".

## Verify

1. `./gradlew :data:source:local:implementation:desktopTest`, then the four compile checks (the file is common code:
   `:app:ios:linkDebugFrameworkIosSimulatorArm64` and `:app:web:wasmJsBrowserDistribution` matter here).
2. Make the files: `printf '{title: Ősz}\n{artist: Teszt}\n[Am]Őszi [G]szél\n' | iconv -f UTF-8 -t UTF-16 > with-mark.cho`
   (iconv's `UTF-16` writes a mark) and `… | iconv -f UTF-8 -t UTF-16LE > without-mark.cho`.
3. `./gradlew :app:desktop:run` and import both in one go: one song titled "Ősz" by "Teszt" is imported and the other
   is reported as already there, which is itself the proof that both files decode to the same text. Open the song:
   chords above the lyrics, `ő` intact.
4. Copy `with-mark.cho` into the library folder under another name, restart, add a tag to it, then
   `file <name>.cho` → "UTF-8 text" and `head -c 3 <name>.cho | xxd` shows `7b 74 69`, no mark.
5. Repeat step 3 on the web build (`:app:web:wasmJsBrowserDevelopmentRun`) with the file picker.

## Docs

- `data/model/CLAUDE.md`, the `domain/LibraryText.kt` bullet: "strict UTF-8, Windows-1252 for a file that is not valid
  UTF-8, and no byte order mark" becomes "UTF-16 where a byte order mark says so (what Notepad calls \"Unicode\") or
  where the NUL bytes of a file without one do — text in the other two encodings holds none, so it cannot be mistaken
  —, otherwise strict UTF-8, Windows-1252 for a file that is not valid UTF-8, and no byte order mark. The UTF-16
  decoder is written by hand, like the Windows-1252 table, since the common standard library has neither; a lone
  surrogate becomes U+FFFD because encoding one differs per platform."
- `data/source/local/implementation/CLAUDE.md`, the "Text is written as UTF-8 and read through…" bullet: insert
  "UTF-16 when the file says so," before "UTF-8 when the bytes are valid UTF-8", and change "A file read through the
  fallback" to "A file read as anything but UTF-8".
- Root `CLAUDE.md`: nothing (it does not describe the decoding).

## Touches

- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryText.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/LibraryTextDecodingTest.kt`
- `data/model/CLAUDE.md`
- `data/source/local/implementation/CLAUDE.md`

## Depends on

nothing. (Plan 15 bounds how large a file is read at all and does not touch `LibraryText.kt`; plan 27 is what keeps the
binary `._x.cho` files out of the listing, independently of this.)
