# 06 · Old Hungarian and Polish song files that are not UTF-8 show õ û for ő ű (and ¹ ³ ¿ for ą ł ż), and the first save writes that into the file

**Severity:** wrong behaviour (all platforms. Uncommon: only files saved in a legacy 8-bit code page, which the old
Windows songbook tools and Notepad before Windows 10 1903 wrote. When it happens, the damage becomes permanent on
the first tag, language, transposition or editor save, and sync then carries it to every device) · **Area:**
`:data:model` (`LibraryText.kt`)

## Decision (taken by the user on 2026-09-22)
**B. Narrow Windows-1250 detection.** A file that is not valid UTF-8 carries no label saying which 8-bit code page
it was written in; today it is always read as Windows-1252 (Western). It is now read as Windows-1250 (Central
European) only when its bytes point at it clearly, and as Windows-1252 otherwise. Two kinds of evidence count, and
anything typical of a Western language vetoes them:

- Hungarian: `ő`/`ű`/`Ő`/`Ű` (0xF5 0xFB 0xD5 0xDB, which are `õ û Õ Û` in 1252) *together with* `á`/`í`/`Á`/`Í`
  (0xE1 0xED 0xC1 0xCD). Hungarian text of any length has both. A French line with `û` has no `á` or `í`, and
  Portuguese and Estonian `õ` are vetoed by their `ã ç ä` (below).
- Polish: `ą ł ż ź Ą Ł Ż Ź` (0xB9 0xB3 0xBF 0x9F 0xA5 0xA3 0xAF 0x8F) *right after a letter*. In 1252 these
  bytes are `¹ ³ ¿ Ÿ ¥ £ ¯` and the undefined 0x8F, none of which follows a letter in Western text. The Spanish
  `¿` always opens a sentence, after a space or a bracket.
- Veto: any of `à ã å è ì ò ø ù ä ç` in either case (0xE0 0xE3 0xE5 0xE8 0xEC 0xF2 0xF8 0xF9 0xE4 0xE7 and
  0xC0 0xC3 0xC5 0xC8 0xCC 0xD2 0xD8 0xD9 0xC4 0xC7). Neither Hungarian nor Polish uses any of these letters, and
  French, Italian, Portuguese, Spanish-with-grave, Scandinavian, German and Estonian text almost always does.

Czech, Slovak and Romanian files keep the 1252 reading they get today: their distinguishing letters (`č ř ě ů`, `ă`)
sit on the same bytes as Western `è ø ì ù`, `ã`, so they cannot be told apart without guessing the language, and a
wrong guess there would garble French and Italian files that read correctly today.

## Symptom
1. Take a song file saved as ANSI on a Hungarian Windows (Windows-1250), for example `{title: Árvíztűrő tükörfúrógép}`,
   and drop it into the library folder or import it.
2. The title shows as `Árvíztûrõ tükörfúrógép`. A Polish `Gęsi za wodą, żółw` shows as `Gêsi za wod¹, ¿ó³w`.
3. Tap a tag, pick a language, transpose, or open and save the editor. The file is rewritten as UTF-8 with those
   wrong letters in it, and a connected sync uploads it.

## Cause
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryText.kt:22-33` falls back to one
fixed table for every file that is not UTF-8:

```kotlin
try {
    decodeToString(throwOnInvalidSequence = true)
} catch (_: CharacterCodingException) {
    decodeWindows1252()
}
```

Every library read goes through it (`JvmFileStorage.kt:69` on Android and desktop, `FileStorage.ios.kt:99`,
`FileStorage.wasmJs.kt:81`), and so does every import (`PrepareImportUseCaseImpl.kt:99`, `:134`). Reading changes
nothing on disk, and sync hashes the raw bytes. The damage is done by the first write, which encodes the misread
text as UTF-8.

## Fix
All in `LibraryText.kt`, which is common code and has no dependencies.

1. The fallback branch of `decodeLibraryText` becomes:

   ```kotlin
   } catch (_: CharacterCodingException) {
       if (isCentralEuropean()) decodeCodePage(WINDOWS_1250_CHARACTERS) else decodeWindows1252()
   }
   ```

   and its KDoc's first paragraph is replaced by:

   ```kotlin
   /**
    * The text of a library file, or of a file on its way into the library. UTF-8 first, since that is what Campfire
    * writes. A file that is not valid UTF-8 was written in one of the old 8-bit code pages, which it does not name, and
    * is read as Windows-1250 where its bytes clearly say Hungarian or Polish ([isCentralEuropean]) and as
    * Windows-1252, which is what every other Western text file is, otherwise. Either is better than a row of
    * replacement characters, and the right one of the two is what keeps the next save from writing a misreading back
    * over the user's accents. Shared by the storage layer and the import, so that a file reads the same whether it was
    * dropped into the library folder or imported.
   ```

   (the byte order mark paragraph stays).

2. Add below `decodeWindows1252`:

   ```kotlin
   /**
    * Every byte of 0x80-0xFF through [characters], the 128 characters a code page puts there. Windows-1250 moves too
    * many of Latin-1's letters to be written as exceptions to it the way [decodeWindows1252] is.
    */
   private fun ByteArray.decodeCodePage(characters: String) = buildString(size) {
       for (byte in this@decodeCodePage) {
           val value = byte.toInt() and 0xFF
           append(if (value < 0x80) value.toChar() else characters[value - 0x80])
       }
   }

   /**
    * Whether a file that is not UTF-8 is Windows-1250 rather than 1252. The two agree on most accented letters and
    * nothing in the bytes says which one wrote them, so only what cannot be anything else counts: the Hungarian
    * double acute (`ő`, `ű`) next to an `á` or `í` somewhere in the text, or a Polish letter (`ą`, `ł`, `ż`, `ź`)
    * right after another letter, where 1252 has `¹`, `³`, `¿` and `Ÿ`, which never follow a letter in Western text.
    * One letter that neither language uses and the Western ones do (`à`, `ã`, `è`, `ä`, `ç`…) settles it for 1252:
    * it is what tells a Portuguese `õ`, a French `û` or an Estonian `õ` from a Hungarian one. Czech, Slovak and
    * Romanian stay on 1252: their `č`, `ř`, `ě` and `ă` share their bytes with the Western `è`, `ø`, `ì` and `ã`, and
    * telling them apart would mean guessing the language, wrongly for some of the Western files that read right.
    */
   private fun ByteArray.isCentralEuropean(): Boolean {
       var hasDoubleAcute = false
       var hasAcuteAOrI = false
       var hasPolishLetter = false
       for (index in indices) {
           when (this[index].toInt() and 0xFF) {
               0xE0, 0xE3, 0xE4, 0xE5, 0xE7, 0xE8, 0xEC, 0xF2, 0xF8, 0xF9,
               0xC0, 0xC3, 0xC4, 0xC5, 0xC7, 0xC8, 0xCC, 0xD2, 0xD8, 0xD9 -> return false
               0xD5, 0xDB, 0xF5, 0xFB -> hasDoubleAcute = true
               0xC1, 0xCD, 0xE1, 0xED -> hasAcuteAOrI = true
               0x8F, 0x9F, 0xA3, 0xA5, 0xAF, 0xB3, 0xB9, 0xBF -> if (index > 0 && isLetterByte(index - 1)) hasPolishLetter = true
           }
       }
       return (hasDoubleAcute && hasAcuteAOrI) || hasPolishLetter
   }

   /** An ASCII letter, or a byte both code pages put a letter on (0xC0 and up, apart from × and ÷). */
   private fun ByteArray.isLetterByte(index: Int): Boolean {
       val value = this[index].toInt() and 0xFF
       return value in 0x41..0x5A || value in 0x61..0x7A || (value >= 0xC0 && value != 0xD7 && value != 0xF7)
   }
   ```

   Keep the `when` branches on one line each if the file's width allows it. The first branch's two lines are one
   `when` condition list, so the second line has no trailing comma (it ends at the arrow).

3. Add the table after `WINDOWS_1252_CHARACTERS`:

   ```kotlin
   /**
    * 0x80-0xFF in Windows-1250, the Central European code page. The five bytes it leaves undefined become the
    * replacement character.
    */
   private const val WINDOWS_1250_CHARACTERS =
       "€\uFFFD‚\uFFFD„…†‡\uFFFD‰Š‹ŚŤŽŹ" +
           "\uFFFD‘’“”•–—\uFFFD™š›śťžź" +
           "\u00A0ˇ˘Ł¤Ą¦§¨©Ş«¬\u00AD®Ż" +
           "°±˛ł´µ¶·¸ąş»Ľ˝ľż" +
           "ŔÁÂĂÄĹĆÇČÉĘËĚÍÎĎ" +
           "ĐŃŇÓÔŐÖ×ŘŮÚŰÜÝŢß" +
           "ŕáâăäĺćçčéęëěíîď" +
           "đńňóôőö÷řůúűüýţ˙"
   ```

   Each row is 16 characters, 128 in all. The table was generated from Python's `cp1250` codec and checked against
   it character for character. Copy it as it is, with the `\u` escapes for the replacement character, the
   no-break space and the soft hyphen.

A file in ISO-8859-2 (Latin-2) reads correctly for Hungarian, since `ő ű` are on the same bytes there. Polish Latin-2
files still lose `ą ś ź` (0xB1 0xB6 0xBC), which is no worse than today.

What this changes for files that exist:
- A file already rewritten by the app is UTF-8 and is not affected.
- A legacy Hungarian or Polish file that was never saved reads correctly from the next scan on. Nothing is written
  until the user changes the song.
- Sync compares raw bytes, so a change of reading uploads nothing. Two devices on different versions show the same
  unchanged file with different letters until one of them saves it, and that save is an ordinary edit.

## Tests
`data/source/local/implementation/src/commonTest/.../storage/file/LibraryTextDecodingTest.kt`, next to the existing
1252 tests (the existing three must keep passing unchanged):

```kotlin
@Test
fun readsAHungarianFileInWindows1250() {
    // "Árvíztűrő tükörfúrógép": the ű and the ő are on the bytes where Windows-1252 has û and õ.
    val bytes = bytes(0xC1, 0x72, 0x76, 0xED, 0x7A, 0x74, 0xFB, 0x72, 0xF5, 0x20, 0x74, 0xFC, 0x6B, 0xF6, 0x72, 0x66, 0xFA, 0x72, 0xF3, 0x67, 0xE9, 0x70)

    assertEquals("Árvíztűrő tükörfúrógép", bytes.decodeLibraryText())
}

@Test
fun readsAPolishFileInWindows1250() {
    // "Gęsi za wodą, żółw"
    val bytes = bytes(0x47, 0xEA, 0x73, 0x69, 0x20, 0x7A, 0x61, 0x20, 0x77, 0x6F, 0x64, 0xB9, 0x2C, 0x20, 0xBF, 0xF3, 0xB3, 0x77)

    assertEquals("Gęsi za wodą, żółw", bytes.decodeLibraryText())
}

@Test
fun keepsWesternFilesThatShareBytesWithCentralEuropeanLettersOnWindows1252() {
    // Portuguese õ next to ç and ã, Estonian Õ next to ä, a French û with no á or í, and a Spanish ¿ opening a sentence.
    assertEquals("Corações não", bytes(0x43, 0x6F, 0x72, 0x61, 0xE7, 0xF5, 0x65, 0x73, 0x20, 0x6E, 0xE3, 0x6F).decodeLibraryText())
    assertEquals("Õhtu äär", bytes(0xD5, 0x68, 0x74, 0x75, 0x20, 0xE4, 0xE4, 0x72).decodeLibraryText())
    assertEquals("Sûr été", bytes(0x53, 0xFB, 0x72, 0x20, 0xE9, 0x74, 0xE9).decodeLibraryText())
    assertEquals("¿Qué año?", bytes(0xBF, 0x51, 0x75, 0xE9, 0x20, 0x61, 0xF1, 0x6F, 0x3F).decodeLibraryText())
}
```

Run `./gradlew :data:source:local:implementation:desktopTest` (in a worktree).

## Verify
1. Desktop: write `{title: Árvíztűrő tükörfúrógép}` plus a lyric line containing `á` into a file with
   `iconv -f UTF-8 -t CP1250`, drop it into the library folder, and check the title in the list. Tap a tag and open
   the file in a text editor: it is now UTF-8 with `ő ű` intact.
2. The same with a French text in CP1252 (`Café à la crème, sûr`): it reads as before.
3. Import the Hungarian CP1250 file through the file picker on Android: it reads correctly.
4. Compile `:app:desktop:run`, `:app:android:assembleDebug`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`,
   `:app:web:wasmJsBrowserDevelopmentRun` (common code only).

## Docs
- `data/model/CLAUDE.md`, the `domain/LibraryText.kt` bullet: replace "strict UTF-8, Windows-1252 for a file that is
  not valid UTF-8, and no byte order mark." with "strict UTF-8; for a file that is not valid UTF-8, Windows-1250
  where the bytes can only be Hungarian (`ő`/`ű` next to `á`/`í`) or Polish (`ą ł ż ź` after a letter) and no
  Western-only letter (`à ã è ä ç`…) is among them, Windows-1252 otherwise; and no byte order mark. Czech, Slovak and
  Romanian are left on 1252 on purpose: their letters share bytes with Western ones, and guessing the language would
  misread files that read right."
- `data/source/local/implementation/CLAUDE.md:32-35`: replace "UTF-8 when the bytes are valid UTF-8, Windows-1252
  when they are not (what every other Western text file dropped into the library folder turns out to be)" with
  "UTF-8 when the bytes are valid UTF-8, and otherwise Windows-1250 for a file whose bytes can only be Hungarian or
  Polish and Windows-1252 for everything else (see `:data:model`)".

## Touches
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryText.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/LibraryTextDecodingTest.kt`
- `data/model/CLAUDE.md`, `data/source/local/implementation/CLAUDE.md`

## Depends on
None.
