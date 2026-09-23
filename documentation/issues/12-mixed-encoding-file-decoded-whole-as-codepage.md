# 12 — A UTF-8 file with one stray code-page byte is decoded entirely as Windows-1252

**Severity:** garbled text, then written back garbled on the next save (all platforms; import and library reads) ·
**Area:** `:data:model` (`domain/LibraryText.kt`)

**Read, not run.** This was found by reading the code at HEAD (`2065e47f`); it has not been reproduced in a running
build. The "Verification" section below is how to confirm it, and confirming it is the first step of the work.

Reviewer finding 2-local#2.

## What the user sees

A songbook file — one `.cho` holding many songs, or a single long song — written in UTF-8, in which one line was
pasted from an old Windows document and carries a Windows-1252 `é` (the single byte `0xE9`). Imported, every accented
letter of **every** song in it comes out as mojibake: `Tükörfúrógép` becomes `TÃ¼kÃ¶rfÃºrÃ³gÃ©p`, Cyrillic and Greek
titles turn into rows of Latin-1 letters. Two hundred songs are split out of that text, named after the garbled
titles, and stored as UTF-8 — the garbling is now the file's real content. The same happens to a library file with a
stray byte that is only read (a desktop library folder edited with another tool): the song shows garbled, and the
first save writes the garbling back.

## Cause

`decodeLibraryText` (`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryText.kt:24-35`)
decides for the whole file at once:

```kotlin
fun ByteArray.decodeLibraryText(): String {
    val byteOrder = utf16ByteOrder()
    return if (byteOrder == null) {
        try {
            decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            if (isCentralEuropean()) decodeCodePage(WINDOWS_1250_CHARACTERS) else decodeWindows1252()
        }
    } else {
        decodeUtf16(byteOrder)
    }.trimStart(BYTE_ORDER_MARK)
}
```

One invalid sequence anywhere and every byte of the file goes through the code-page table, including the thousands
of valid multi-byte UTF-8 sequences. `PrepareImportUseCaseImpl.kt:100` feeds the whole archive entry through it before
splitting (`ChordProSplitter.split(file.bytes.decodeLibraryText())`), and every storage `readText` does the same for a
library file.

## The change

Invoke the **`code-style`** skill before the first edit. `:data:model` is common code: no `java.*`, no platform
charset.

Decide per byte run instead of per file, with one guard that keeps a genuine code-page file on its code page:

- Walk the bytes once, recognising every **well-formed** UTF-8 sequence (Unicode Table 3-7) and counting the bytes that
  start none (`invalid`) and the multi-byte sequences that are well-formed (`multiByte`).
- `invalid == 0` → plain `decodeToString()` (today's first branch).
- `multiByte > 0 && invalid <= multiByte` → **mixed**: valid sequences decode as UTF-8, each stray byte through the
  code page. The file is UTF-8 with a few foreign bytes; there is at least one proper UTF-8 character for every stray
  byte.
- otherwise → today's whole-file code-page decode. A real Windows-1252 file almost never contains a well-formed
  multi-byte sequence (it would take an uppercase accented letter followed by one of `‚ƒ„…` / `¡¢£` / `°±²`), and when
  it does by accident, its many accented letters far outnumber it.
- The code page of the stray bytes is chosen by the existing `isCentralEuropean()`, asked about a view of the file in
  which every well-formed multi-byte sequence is replaced by one byte: the byte both code pages give that character
  where they share one (`á í é ó ö ü Á Í`… — the Latin-1 letters Windows-1250 keeps in place), and an ASCII `a`
  otherwise. So the UTF-8 lead bytes (`0xC3`, which `isCentralEuropean` would read as a Western `Ã`) never vote for
  1252, the "after a letter" rule of the Polish test still sees a letter before a stray `ł`, and a Hungarian `á` or
  `í` written in UTF-8 still counts as the evidence the double-acute rule needs when only the `ő` / `ű` are stray.

Replace `LibraryText.kt:24-35` and the two decoders with:

```kotlin
fun ByteArray.decodeLibraryText(): String {
    val byteOrder = utf16ByteOrder()
    return (if (byteOrder == null) decodeUtf8OrCodePage() else decodeUtf16(byteOrder)).trimStart(BYTE_ORDER_MARK)
}
```

and add:

```kotlin
/**
 * UTF-8 where the bytes are UTF-8. A file that is not is read through a code page, the whole of it where it is a
 * code-page file ([decodeWindows1252], or Windows-1250 where [isCentralEuropean]) - but a UTF-8 file with a few stray
 * bytes in it, a line pasted from an old document into a songbook, keeps every character that is UTF-8 and reads only
 * the stray bytes through the code page. Decided whole, one such byte used to turn every accented letter of two hundred
 * songs into two Latin-1 ones, which the first save then wrote back as the song.
 */
private fun ByteArray.decodeUtf8OrCodePage(): String {
    var index = 0
    var multiByte = 0
    var invalid = 0
    while (index < size) {
        val length = utf8SequenceLength(index)
        when {
            length == 0 -> { invalid++; index++ }
            else -> { if (length > 1) multiByte++; index += length }
        }
    }
    return when {
        invalid == 0 -> decodeToString()
        multiByte > 0 && invalid <= multiByte -> decodeMixed(isCentralEuropean = withUtf8AsCodePage().isCentralEuropean())
        isCentralEuropean() -> decodeCodePage(WINDOWS_1250_CHARACTERS)
        else -> decodeWindows1252()
    }
}

/** The length of the well-formed UTF-8 sequence that starts at [index], or 0 where none does (Unicode, Table 3-7). */
private fun ByteArray.utf8SequenceLength(index: Int): Int {
    fun byteAt(offset: Int) = if (index + offset < size) this[index + offset].toInt() and 0xFF else -1
    fun isContinuation(offset: Int, range: IntRange = 0x80..0xBF) = byteAt(offset) in range
    return when (byteAt(0)) {
        in 0x00..0x7F -> 1
        in 0xC2..0xDF -> if (isContinuation(1)) 2 else 0
        0xE0 -> if (isContinuation(1, 0xA0..0xBF) && isContinuation(2)) 3 else 0
        in 0xE1..0xEC, 0xEE, 0xEF -> if (isContinuation(1) && isContinuation(2)) 3 else 0
        0xED -> if (isContinuation(1, 0x80..0x9F) && isContinuation(2)) 3 else 0
        0xF0 -> if (isContinuation(1, 0x90..0xBF) && isContinuation(2) && isContinuation(3)) 4 else 0
        in 0xF1..0xF3 -> if (isContinuation(1) && isContinuation(2) && isContinuation(3)) 4 else 0
        0xF4 -> if (isContinuation(1, 0x80..0x8F) && isContinuation(2) && isContinuation(3)) 4 else 0
        else -> 0
    }
}

/** UTF-8 with the bytes that start no sequence read through Windows-1250 or -1252, decoded a run at a time. */
private fun ByteArray.decodeMixed(isCentralEuropean: Boolean) = buildString(size) {
    var runStart = 0
    var index = 0
    while (index < size) {
        val length = utf8SequenceLength(index)
        if (length == 0) {
            append(this@decodeMixed.decodeToString(runStart, index))
            append(codePageCharacter(this@decodeMixed[index].toInt() and 0xFF, isCentralEuropean))
            index++
            runStart = index
        } else {
            index += length
        }
    }
    append(this@decodeMixed.decodeToString(runStart, size))
}

/**
 * The file as [isCentralEuropean] should see it once its UTF-8 is set aside, the stray bytes left where they were.
 * A character both code pages put on the same byte - the Latin-1 letters Windows-1250 kept, `á`, `í`, `ö` and the
 * rest - becomes that byte, so that it is evidence about the language the way it would be in a code-page file; every
 * other multi-byte character becomes an `a`, a letter, which is what almost all of them are and all the Polish rule
 * needs to know about the one before a stray byte.
 */
private fun ByteArray.withUtf8AsCodePage(): ByteArray {
    val bytes = ByteArray(size)
    var count = 0
    var index = 0
    while (index < size) {
        val length = utf8SequenceLength(index)
        if (length > 1) {
            // Only a two-byte sequence can be below U+0100.
            val codePoint = if (length == 2) ((this[index].toInt() and 0x1F) shl 6) or (this[index + 1].toInt() and 0x3F) else -1
            val isShared = codePoint in 0xA0..0xFF && WINDOWS_1250_CHARACTERS[codePoint - 0x80].code == codePoint
            bytes[count++] = if (isShared) codePoint.toByte() else 'a'.code.toByte()
            index += length
        } else {
            bytes[count++] = this[index]
            index++
        }
    }
    return bytes.copyOf(count)
}

private fun codePageCharacter(value: Int, isCentralEuropean: Boolean) = when {
    value < 0x80 -> value.toChar()
    isCentralEuropean -> WINDOWS_1250_CHARACTERS[value - 0x80]
    value in WINDOWS_1252_RANGE -> WINDOWS_1252_CHARACTERS[value - WINDOWS_1252_RANGE.first]
    else -> value.toChar()
}
```

and have `decodeWindows1252` / `decodeCodePage` call `codePageCharacter` so the three decoders share one table lookup.
Notes:

- `ByteArray.decodeToString(startIndex, endIndex)` is common stdlib; each run it is given is well-formed by
  construction, so no replacement characters appear from it.
- The threshold `invalid <= multiByte` is deliberately simple and is the one rule stated in the KDoc and in
  `:data:model/CLAUDE.md`; do not add a proportion of the file size, which would make a short file and a long file
  with the same stray bytes decode differently.
- The existing tests in `LibraryTextDecodingTest` all have `multiByte == 0` and are unaffected.

## Tests

`data/source/local/implementation/src/commonTest/.../storage/file/LibraryTextDecodingTest.kt` (the decoder's tests
live here, `:data:model` has none):

1. `readsAStrayWindows1252ByteInsideUtf8AsTheLetterItStandsFor`: `"é".encodeToByteArray() + bytes(0xE9)` →
   `"éé"` (the reviewer's case).
2. `keepsEveryUtf8CharacterOfASongbookWithOneStrayLine`: 20 lines of `"{title: Tükörfúrógép}\n"` encoded as UTF-8, plus
   one line `bytes(0x43, 0x61, 0x66, 0xE9, 0x0A)` ("Café\n" in 1252) → the 20 lines unchanged and `"Café\n"`.
3. `readsStrayHungarianBytesThroughWindows1250`: `"Árvíz ".encodeToByteArray() + bytes(0x74, 0xFB, 0x72, 0xF5)`
   ("tűrő" in Windows-1250; the stray bytes are only the double acutes) → `"Árvíz tűrő"`, not `"Árvíz tûrõ"`. The
   `Á` and `í` in UTF-8 are what make it Hungarian (see `withUtf8AsCodePage`).
4. `keepsAWindows1252FileWithOneAccidentalUtf8PairOnWindows1252`: the 1252 bytes of `"Ã© à la crème, déjà vu"`
   (`0xC3 0xA9 0x20 0xE0 …`): the first two bytes happen to be a well-formed UTF-8 `é`, so `multiByte == 1` against
   four stray bytes (`à`, `è`, `é`, `à`) → whole-file 1252, `"Ã© à la crème, déjà vu"` exactly as written.
5. Existing tests unchanged and passing.

Run `./gradlew :data:source:local:implementation:desktopTest`, then the root unit test command.

## Verification

1. Build a test file: `printf '{title: Tükörfúrógép}\n{artist: Árvíz}\n' > mixed.cho; printf 'Caf\xe9\n' >> mixed.cho`.
2. `./gradlew :app:desktop:run`, import `mixed.cho`. **Before:** the title reads `TÃ¼kÃ¶rfÃºrÃ³gÃ©p` in the list.
   **After:** `Tükörfúrógép`, and the song text shows `Café`.
3. Open it in the editor, save without changes, and check the file on disk is now valid UTF-8 with both lines as
   shown (`iconv -f utf-8 -t utf-8 file.cho` succeeds).
4. Regression: import a genuine Windows-1252 file (`printf '{title: Caf\xe9 cr\xe8me}\n'`) and a Windows-1250 one
   (`printf '{title: \xe1rv\xedzt\xfbr\xf5}\n'`): unchanged from today (`Café crème`, `árvíztűrő`).

## Docs

- `data/model/CLAUDE.md:17` — "strict UTF-8; for a file that is not valid UTF-8, Windows-1250 where …, Windows-1252
  otherwise" → "UTF-8; a file that is not valid UTF-8 but has at least as many well-formed multi-byte UTF-8
  characters as stray bytes is UTF-8 with the stray bytes read through the code page (a line pasted from an old
  document into a songbook), and any other file is read whole as Windows-1250 where …, Windows-1252 otherwise. The
  code page of stray bytes is chosen by the same test, asked about the file with its UTF-8 set aside."
- `data/source/local/implementation/CLAUDE.md:32-35` — "UTF-8 when the bytes are valid UTF-8, and otherwise
  Windows-1250 … and Windows-1252 for everything else" → add "(a UTF-8 file with a few stray bytes keeps its UTF-8 and
  reads only those through the code page, see `:data:model`)".
- The KDoc of `decodeLibraryText` (`LibraryText.kt:12-23`): add the mixed case in one sentence, pointing at
  `decodeUtf8OrCodePage`.

## Files touched

- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryText.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/LibraryTextDecodingTest.kt`
- `data/model/CLAUDE.md`, `data/source/local/implementation/CLAUDE.md`

## Depends on

Nothing. Lane C's import plans (21, 22, 25, 26) touch `PrepareImportUseCaseImpl` / `ImportPlanner`, which call this
function but are not changed by it.
