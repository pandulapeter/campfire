# 26 · Every song whose title is not written in Latin letters is filed as `untitled`, and the N-th file of one name costs N storage calls

**Severity:** wrong behaviour + performance (all platforms; certain for every Cyrillic, Greek, Hebrew, Arabic, Indic
or CJK library) · **Area:** `:data:model` (`LibraryFiles.normalizedName`), `:data:source:local:implementation`
(`FileNames.kt` `uniqueName`, `FileStorage` and its four actuals) · **Decision:** keep any Unicode letter or digit,
lowercased; Latin text normalizes exactly as today, so no existing name changes; the cap moves from characters to
UTF-8 bytes.

## Symptom

1. Create or import a song with `{title: Катюша}` and no artist: the file is `untitled.cho`. With `{artist: Кино}` it
   is `untitled-untitled.cho`. The second such song is `untitled_2.cho`, the three-hundredth `untitled_300.cho`.
2. The names say nothing anywhere they are read instead of the app: the desktop library folder, the Files app on iOS,
   the Dropbox folder, an exported zip, the name an exported or shared song leaves under.
3. Two devices that each wrote their first Cyrillic song before connecting sync both hold an `untitled.cho` with
   different content, so the first run produces a ` (2)` conflict copy for every such pair.
4. A setlist titled "Летний сет" is `untitled.setlist.json`.
5. Importing a few thousand of them takes minutes on desktop and tens of minutes on the web: the N-th file probes
   `untitled.cho`, `untitled_2.cho` … one `exists()` at a time — about N²/2 storage calls for the batch, each one a
   dispatcher hop plus a `stat`, or a `getFileHandle` promise on OPFS.

(The "names are taken" question such an import runs into is plan 02's; this plan removes most of the collisions
that lead to it.)

## Cause

`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFiles.kt:90-100`, verified at
`29820b93`:

```kotlin
// A character no accent table knows (a Cyrillic or CJK title) becomes a separator like punctuation does,
// which is the one case where nothing is left of the name and the fallback has to stand in for it.
.map { character ->
    when (val folded = character.withoutAccent()) {
        …
        else -> if (folded in 'a'..'z' || folded in '0'..'9') folded.toString() else NAME_SEPARATOR
    }
}
```

There is no branch for "a letter of another script", so a whole title folds to separators, `words` is empty and
`FALLBACK_NAME` stands in. `FileNamesTest.setlistNameIsAlwaysOpenableAndCapped` pins exactly that
(`"untitled.setlist.json"` for `"Летний сет"`).

`data/source/local/implementation/src/commonMain/…/implementation/FileNames.kt:59-72`:

```kotlin
suspend fun isFree(candidate: String) = if (candidate.equals(currentName, ignoreCase = true)) {
    list(directory).none { it.name == candidate }
} else {
    !exists(directory, candidate)
}
if (isFree(desired)) return desired
…
while (true) {
    val candidate = base + collisionSuffix(index) + extension
    if (isFree(candidate)) return candidate
    index++
}
```

One storage call per candidate. It was written for a library where a collision is an exception; the naming rule above
made it the rule.

The reviewer's finding is right in every detail. Two things in the suggested fix do not survive a closer look and are
decided differently below: a cap of 100 bytes per half would rename existing Latin names between 101 and 120
characters, and "list the directory once" with the existing `list` opens every file on OPFS, which is the cost
`info` was added to avoid.

## Fix

### What the new rule is, and why each part of it

Work through these before touching the code; the comments in the snippets only carry the conclusions.

- **Which characters the new branch is for.** Everything below U+0370 (Basic Latin to the Combining Diacritical Marks
  block) and the Latin Extended blocks (U+1E00–1EFF, U+2C60–2C7F, U+A720–A7FF, U+AB30–AB6F) is "Latin" and is treated
  **exactly as today**, character by character — including the letters `Accents.kt` does not know: `Dziękuję` stays
  `dzi_kuj` and `Tiếng Việt` stays `ti_ng_vi_t`. That is ugly and it is the decision: no name the app has already
  written may change. (The fix for those is a longer accent table, which is a different change with a different
  consequence — **Update file name** lighting up on Latin songs — and is not part of this plan.) Every other
  character is kept when `Char.isLetterOrDigit()` says so.
- **`isLetterOrDigit()` on the four targets.** It is common stdlib and works on one UTF-16 unit. A surrogate is
  category `Cs` on every target, so both halves of a supplementary-plane character (emoji, CJK Extension B such as
  `𠮷`, historic scripts) fall to the separator branch **together**: a pair is never cut in half because no half is
  ever kept, and the output therefore never contains a surrogate at all, which the byte cap below relies on. The cost
  is that `𠮷野家` is filed as `野家`; that is the conservative reading the decision asked for. The JVM answers from
  the JDK's (on Android: the OS's ICU) Unicode tables and Native/Wasm from the tables of the Kotlin release, so a
  letter of a script added to Unicode recently can be a letter on one device and unassigned on an older Android.
  The result is a differently named file for that one title on that one device, nothing worse — a name is only a
  name — and it is accepted rather than worked around with a table of our own.
- **Lowercasing** stays `String.lowercase()`, which is locale-invariant on every target (no Turkish `ı`) and a fixed
  point after one application, which idempotence needs. One wrinkle: an upper-case Greek `Σ` at the end of a word
  becomes `ς` where the platform implements the final-sigma rule. Both are lower case already, so a second pass
  changes neither; do not "fix" it by folding `ς` to `σ`, that is a spelling mistake in Greek.
- **Combining marks.** The repo's `Char.isCombiningMark()` (`Accents.kt:48`) is only the block U+0300–036F, and today
  it is filtered out before anything else so that a decomposed `é` folds like a composed one. That stays true after a
  Latin letter. After a letter of another script a mark is **part of the letter**: Devanagari, Bengali, Tamil and
  Thai vowel signs and viramas (`Mn`/`Mc`), Arabic and Hebrew points, the kana voicing marks U+3099/309A — and a
  decomposed Cyrillic `й` or Greek `ά`, whose mark is in the U+0300 block. Turned into separators they would chop
  `हिन्दी` into `ह_न_द`; dropped they would misspell `цой` as `цои`. So: a character of category `NON_SPACING_MARK` or
  `COMBINING_SPACING_MARK` is kept **if and only if the character kept just before it was kept by the new branch**
  (a non-Latin letter or digit, or a mark on one). Anywhere else a mark does what it does today: U+0300–036F is
  dropped, any other mark is a separator. Hangul needs nothing: precomposed syllables and conjoining jamo are both
  category `Lo`, not marks.
- **NFC and NFD.** Common Kotlin has no normalizer and this plan does not add an `expect`/`actual` one: composing
  first would change what a decomposed Latin letter outside the accent table folds to (`e` + ogonek is `e` today and
  would become a separator), which the decision rules out. The consequence is that the same non-Latin title in its
  composed and its decomposed spelling gives two names that look alike (`й` against `и`+U+0306). Titles come from
  the file's text, which no file system touches, and text fields produce NFC, so this takes a decomposed *source*
  — typically a title taken from the file name of a zip entry written on a Mac. It costs a second file, never an
  overwrite: on APFS (normalization-insensitive) the storage says the other spelling is taken, and `uniqueName`
  below always lets the storage have the last word.
- **Format characters** (ZWNJ in Persian, ZWJ in Indic scripts, the bidi controls) are category `Cf`: separators.
  `می‌خواهم` is filed as `می_خواهم`, which reads fine, and no name can ever carry a right-to-left override, so an
  exported name cannot disguise its extension.
- **RTL names.** Bytes are in logical order; `_`, `-` and the ASCII digits of a collision suffix are bidi-neutral or
  weak, so a file manager may *draw* `artist-title_2` with the halves swapped and the number on the left. Nothing in
  the app reads a name by position on screen, and `toSongExportFileName` splits on the first `-` of the string.
- **The cap.** `MAX_NAME_LENGTH = 120` becomes `MAX_NAME_BYTES = 120`, measured in UTF-8. The output of Latin text is
  pure ASCII, where bytes are characters, so every existing name is capped exactly where it was; a lower number
  (the review suggested 100) would rename existing songs with a half between 101 and 120 characters. For other
  scripts it is 60 Cyrillic/Greek/Hebrew/Arabic or 40 CJK/Indic/Thai characters per half. The arithmetic for the
  255-byte limit of ext4, F2FS, APFS and NTFS (NTFS counts UTF-16 units, which is never more): a song is at most
  `120 + 1 + 120 = 241`, plus `_9999` (5) and the longest extension `.chordpro` (9) is **255**; a setlist is
  `120 + 3 + 13 = 136`. What does not fit on top of a full-length song name is the JVM storage's
  `<name>.<20 digits>.tmp` temporary file (+25) — true today for a long Latin name as well, and the reason plan 29
  names its temporary files without the target. With 29 landed nothing the app writes exceeds 255; without it, a
  non-Latin song whose artist and title are both near the cap cannot be saved on desktop and Android, exactly as a
  Latin one cannot today. The cap stays on a word boundary; only a first word longer than the cap is cut, now
  between characters at a byte count — safe because the output has no surrogates, and a mark that loses its place
  to the cut is simply not in the name.
- **Idempotence** (`normalizedName(normalizedName(x)) == normalizedName(x)`). The output consists of `a–z`, `0–9`,
  `_`, non-Latin letters and digits that are already lower case, and marks that directly follow one of those. On a
  second pass: lowercasing changes nothing; ASCII letters and digits are kept by the old branch; each non-Latin
  letter is kept by the new one; each mark still directly follows a kept non-Latin character, because the only
  things removed *without* leaving a separator are apostrophes (which leave the flag alone) and U+0300–036F marks in
  Latin context (after which the flag is false in both passes); `_` is category `Pc`, so words split where they were
  joined; `ft` and `and` map to themselves; and a name within the byte cap fits whole, since the cap is applied to
  the same words with the same separators. The cut of an over-long first word yields one word within the cap, which
  the loop then takes whole.
- **File systems and services.** Letters, digits, marks, `_` and `-` are legal on ext4/F2FS (Android), APFS, NTFS,
  in OPFS (any string without `/`), in Dropbox (a component may be 255 characters, and none of its forbidden
  characters can occur) and in a zip (`ZipWriter` sets the UTF-8 name flag, `ZipReader` decodes names as UTF-8).
  Dropbox compares names case-insensitively with a lowercasing of its own that is not Kotlin's for a handful of
  characters; every name the app derives is already lower case, so the two agree on all of them, and
  `foldRemoteNamesOntoLocal` (`SyncEngine.kt:40-48`, `name.lowercase()`) only ever matters for hand-named files, as
  today. Leave it alone.
- **What users will see after updating.** `Song.canUpdateFileName` compares the file's name with the derived one, so
  every existing `untitled_N.cho` whose song declares a `{title}` now offers **Update file name** — intended: it is
  the only way those files get their names, and the rule that the app never renames a song on its own stands. A
  setlist's file follows its title, so an `untitled.setlist.json` moves to its real name the next time the setlist
  is edited (`SetlistLocalSourceImpl.renameSetlist`). Both reach sync as a deletion plus a new file, as any rename
  does. Nothing is renamed at start-up.

### 1. `data/model/…/domain/LibraryFiles.kt`

Rename `MAX_NAME_LENGTH` to `MAX_NAME_BYTES` (the only uses outside this file are in `FileNamesTest`) and replace its
KDoc, the KDoc's first paragraph of `normalizedName`, and the function body. The second and third KDoc paragraphs,
`NAME_SEPARATOR`, `FALLBACK_NAME`, `APOSTROPHES` and `ABBREVIATIONS` stay as they are.

```kotlin
    /**
     * The longest a name may be before its extension, in UTF-8 bytes, since that is what the file systems count: a
     * name in another alphabet spends two or three of them on a letter. Text in Latin letters comes out of
     * [normalizedName] as ASCII, where this is a count of characters. Two halves of this length, the dash between
     * them, a collision number and the longest extension of the family come to the 255 bytes every file system the
     * library can end up on allows.
     */
    const val MAX_NAME_BYTES = 120

    /**
     * [base] reduced to what every file system, shell and cloud service agrees about: lowercase words joined with
     * underscores, capped at [MAX_NAME_BYTES] and never empty. Latin letters lose their accents; a letter or a digit
     * of any other script is kept as it is, together with the marks written on it, because a transliteration table
     * for every alphabet is not something the app can carry and a name that says nothing is worse than one a shell
     * has to quote. The extension is the caller's to add.
     *
     * (the two existing paragraphs follow unchanged)
     */
    fun normalizedName(base: String): String {
        val folded = StringBuilder()
        // True while the character kept last was a letter or a digit of another script, or a mark on one. It is the
        // only place a mark is kept: there it is part of the letter (a vowel sign, a virama, the breve of a decomposed
        // "й"), while on a Latin letter it is the accent of a decomposed spelling, which the fold drops.
        var isAfterForeignCharacter = false
        for (character in base.lowercase()) {
            // The one piece of punctuation that binds rather than separates, and so is dropped instead of folded to
            // an underscore: "don't" is one word, in whichever of its spellings a keyboard produced it.
            if (character in APOSTROPHES) continue
            // Judged one UTF-16 unit at a time. Half a surrogate pair is no letter on any platform, so a character
            // outside the basic plane becomes a separator as a whole and none is ever cut in half.
            val isForeignCharacter = when {
                character.isMark() -> isAfterForeignCharacter
                character.isLatin() -> false
                else -> character.isLetterOrDigit()
            }
            when {
                isForeignCharacter -> folded.append(character)
                // The accent of a decomposed Latin letter, which would otherwise be folded to a separator in the
                // middle of its word.
                character.isCombiningMark() -> Unit
                // Turned into the word it is read as rather than into a separator, so that the two ways of writing
                // the same title meet here instead of naming two files.
                character in AND_SIGNS -> folded.append(NAME_SEPARATOR).append("and").append(NAME_SEPARATOR)
                else -> when (val plain = character.withoutAccent()) {
                    // The three letters that are two letters once they are spelled out, which is the one thing the
                    // accent table cannot say: everything in it folds to a single character.
                    'ß' -> folded.append("ss")
                    'æ' -> folded.append("ae")
                    'œ' -> folded.append("oe")
                    // A Latin letter the accent table does not know is a separator like punctuation is. Keeping it
                    // would be kinder to the title and would rename every file that was named before it was kept.
                    else -> folded.append(if (plain in 'a'..'z' || plain in '0'..'9') plain.toString() else NAME_SEPARATOR)
                }
            }
            isAfterForeignCharacter = isForeignCharacter
        }
        val words = folded.split(NAME_SEPARATOR).filter { it.isNotEmpty() }.map { word -> ABBREVIATIONS[word] ?: word }
        // Capped by whole words rather than by characters: a word cut short can become a different word on the next
        // pass ("feather" cut to "feat" is filed as "ft"), and the name would then not survive being normalized again.
        // Only a first word that is longer than the cap on its own is cut, and no abbreviation is anywhere near that long.
        val name = StringBuilder()
        var nameBytes = 0
        for (word in words) {
            val addedBytes = (if (name.isEmpty()) 0 else NAME_SEPARATOR.length) + word.encodeToByteArray().size
            if (nameBytes + addedBytes > MAX_NAME_BYTES) break
            if (name.isNotEmpty()) name.append(NAME_SEPARATOR)
            name.append(word)
            nameBytes += addedBytes
        }
        if (name.isEmpty() && words.isNotEmpty()) name.append(words.first().takeBytes(MAX_NAME_BYTES))
        return name.toString().ifEmpty { FALLBACK_NAME }
    }
```

and, with the private values at the bottom of the object (`AND_SIGN`, the `Regex`, is replaced by the string, since
the loop asks about one character at a time):

```kotlin
    /** Both signs a title writes "and" with. */
    private const val AND_SIGNS = "&+"

    /**
     * The blocks Latin letters live in. Everything in them goes through the accent table as it always has, the letters
     * that table does not know included, so that no name given before other scripts were kept comes out differently.
     */
    private fun Char.isLatin() = this < '\u0370' || this in '\u1E00'..'\u1EFF' || this in '\u2C60'..'\u2C7F' ||
        this in '\uA720'..'\uA7FF' || this in '\uAB30'..'\uAB6F'

    /** A mark of any script, where [isCombiningMark] only knows the block that carries the Latin accents. */
    private fun Char.isMark() = category == CharCategory.NON_SPACING_MARK || category == CharCategory.COMBINING_SPACING_MARK

    /**
     * As much of the start of this word as fits into [limit] UTF-8 bytes. A word of [normalizedName] holds no
     * surrogates, so every character is one, two or three bytes on its own and any of them is a place to cut.
     */
    private fun String.takeBytes(limit: Int): String {
        var bytes = 0
        return takeWhile { character ->
            bytes += when {
                character.code < 0x80 -> 1
                character.code < 0x800 -> 2
                else -> 3
            }
            bytes <= limit
        }
    }
```

Do **not**: add transliteration; touch `Accents.kt` (the search normalization shares it); add an `expect`/`actual`
Unicode normalizer; fold fullwidth forms, `ς`, or non-ASCII digits; lower the cap; rename anything at start-up.

### 2. `FileStorage.listNames` — a listing that opens nothing

`data/source/local/implementation/src/commonMain/…/storage/file/FileStorage.kt`, after `list`:

```kotlin
    /**
     * The name of everything in the directory, in no particular order, without opening any of it - which is what
     * makes it cheap where [list] is not, OPFS above all. It is there to answer which names are taken, so unlike [list]
     * it filters nothing: a sub-directory or a temporary file takes its name as surely as a song does.
     */
    suspend fun listNames(directory: StorageDirectory): List<String>
```

- **`JvmFileStorage.kt`, both copies (`androidMain` and `desktopMain`), identically**, after `list`:

  ```kotlin
      override suspend fun listNames(directory: StorageDirectory) = withContext(Dispatchers.IO) {
          val directoryFile = directoryFile(directory)
          // The same rule as the listing above: not there yet is empty, there and unreadable is a failure.
          directoryFile.list()?.toList()
              ?: if (directoryFile.isDirectory) throw IOException("Could not read \"${directoryFile.absolutePath}\".") else emptyList()
      }
  ```

- **`FileStorage.ios.kt`**: `list` already asks `contentsOfDirectoryAtPath` for the names. Move that expression,
  with its comment, into `private fun entryNames(directoryPath: String): List<String>` (ending in
  `.filterIsInstance<String>()`), have `list` map over it, and add:

  ```kotlin
      override suspend fun listNames(directory: StorageDirectory) = withContext(Dispatchers.IO) {
          entryNames(directoryPath(directory))
      }
  ```

- **`FileStorage.wasmJs.kt`**, in `OpfsFileStorage` and next to `listEntries`:

  ```kotlin
      override suspend fun listNames(directory: StorageDirectory) = withContext(Dispatchers.Default) {
          listEntryNames(directoryHandle(directory)).await()?.toString().orEmpty()
              .split(ENTRY_SEPARATOR)
              .filter { it.isNotEmpty() }
      }
  ```

  ```kotlin
  /** Only the keys of the directory, which it hands out without opening a file for each the way [listEntries] has to. */
  private fun listEntryNames(directory: JsAny): Promise<JsString?> = js(
      """(async function () {
          var names = [];
          for await (var name of directory.keys()) {
              names.push(name);
          }
          return names.join(String.fromCharCode(1));
      })()"""
  )
  ```

`AndroidFileStorage` and `DesktopFileStorage` delegate (`FileStorage by JvmFileStorage(…)`) and need nothing.

### 3. `FileNames.kt` — `uniqueName`

The signature does not change, so **no caller changes**: `SongLocalSourceImpl.createSong` / `importSong` /
`renameSong`, `SetlistLocalSourceImpl.createSetlist` / `renameSetlist` / `importSetlist`,
`LibraryFileLocalSourceImpl.writeLibraryFileToFreeName` and `moveFile` in the same file. Listing once per *call*
rather than once per import batch is deliberate: the batch belongs to `:domain:implementation`
(`ImportFilesUseCaseImpl`, which plan 02 rewrites) and handing a set of taken names down through
`SongRepository.importSong` and `SongLocalSource.importSong` would put a storage detail into two `api` contracts.
With real names a collision is rare again, a free name costs the one `exists` it always did, and a taken one costs a
listing of names and one more `exists` however many numbered siblings there are.

Replace the body, and add a paragraph to the KDoc:

```kotlin
 * A free name costs one question to the storage. A taken one costs the names of the directory once and is then
 * numbered in memory, because a batch of files that all want one name would otherwise ask about every number below
 * its own, each one a round trip. The storage still has the last word on the name that is picked: it is the one that
 * knows whether the file system tells capitals, or the two spellings of an accented letter, apart.
```

```kotlin
internal suspend fun FileStorage.uniqueName(
    directory: StorageDirectory,
    desired: String,
    collisionSuffix: (index: Int) -> String = ::normalizedCollisionSuffix,
    currentName: String? = null,
): String {
    fun isOwnName(candidate: String) = candidate.equals(currentName, ignoreCase = true)
    if (!isOwnName(desired) && !exists(directory, desired)) return desired
    val takenNames = listNames(directory).toHashSet()
    // Held against the names exactly as they are rather than put to the storage, see [currentName].
    suspend fun isFree(candidate: String) = candidate !in takenNames && (isOwnName(candidate) || !exists(directory, candidate))
    if (isFree(desired)) return desired
    val extension = desired.knownExtension()
    val base = desired.removeSuffix(extension)
    var index = 2
    while (true) {
        val candidate = base + collisionSuffix(index) + extension
        if (isFree(candidate)) return candidate
        index++
    }
}
```

This answers what the old one answered: for a candidate that is the file's own name in other capitals, "is it in the
listing, spelled exactly so"; for every other, "does the storage say it exists" — the set only spares asking about
the ones known to be taken. Keep picking the **first** free number rather than `max + 1`: it is what
`RenameTest` and the import tests of plan 02 expect, and with the set it costs nothing.

## Tests

`data/source/local/implementation/src/commonTest/…/implementation/FileNamesTest.kt` (this is where
`LibraryFiles.normalizedName` is tested; `:data:model` has no test source set):

- **Every existing case stays as it is** — they are the proof that Latin text is untouched — except:
  `aCappedNameSurvivesBeingNormalizedAgain` reads `LibraryFiles.MAX_NAME_BYTES` and asserts
  `once.encodeToByteArray().size <= LibraryFiles.MAX_NAME_BYTES`; `setlistNameIsAlwaysOpenableAndCapped` loses its
  first assertion and comment (`"untitled.setlist.json"` for `"Летний сет"`), keeps `"!!!"`, and measures bytes.
- `latinLettersTheAccentTableDoesNotKnowStillSeparate`: `"dzi_kuj"` for `"Dziękuję"`, `"ti_ng_vi_t"` for
  `"Tiếng Việt"`, `"edith_piaf"` for `"Édith Piaf"`, `"strasse"` for `"Straße"`.
- `lettersOfOtherScriptsAreKept`, a table of `normalizedName` inputs and outputs:

  | input | output |
  |---|---|
  | `Катюша` | `катюша` |
  | `Ελλάδα μου` | `ελλάδα_μου` |
  | `שלום עולם` | `שלום_עולם` |
  | `مرحبا بالعالم` | `مرحبا_بالعالم` |
  | `千と千尋の神隠し` | `千と千尋の神隠し` |
  | `가시리` | `가시리` |
  | `٣ أغاني` | `٣_أغاني` |
  | `Кино feat. Цой` | `кино_ft_цой` |
  | `Beyoncé & Шакира` | `beyonce_and_шакира` |
  | `می\u200Cخواهم` (a zero-width non-joiner) | `می_خواهم` |
  | `\uD83D\uDD25 Fire` (an emoji) | `fire` |
  | `\uD83D\uDD25` | `untitled` |
  | `\uD842\uDFB7野家` (U+20BB7, outside the basic plane) | `野家` |

  (every row was checked against the JVM's Unicode tables, which is what `desktopTest` runs on), plus
  `songFileName(title = "Группа крови", artist = "Кино") == "кино-группа_крови.cho"` and
  `setlistFileName("Летний сет") == "летний_сет.setlist.json"`.
- `marksStayOnLettersOfOtherScripts` (write every combining or invisible character as a `\u` escape, as the
  existing `aDecomposedAccentFoldsLikeAComposedOne` does — an editor may compose a literal one on save):
  `"हिन्दी_गीत"` for `"हिन्दी गीत"`; `"цои\u0306"` for `"Цои\u0306"` (the breve is
  kept on a Cyrillic letter) next to the existing `"edith"` for `"E\u0301dith"` (and dropped from a Latin one);
  `"か\u3099"` for `"か\u3099"`; `"e"` for `"e\u0301\u0941"` (a mark with no letter of its own script before it).
- `aNameInAnotherScriptSurvivesBeingNormalizedAgain`: for every output of the two tables above, and for
  `normalizedName` of `"я".repeat(300)`, `"千".repeat(100)`, `"слово ".repeat(40)` and
  `"a".repeat(115) + " перо"`, assert `normalizedName(once) == once`.
- `theCapCountsBytes`: `"я".repeat(60)` for `"я".repeat(300)`; `"千".repeat(40)` for `"千".repeat(100)`;
  `List(11) { "слово" }.joinToString("_")` for `"слово ".repeat(40)` (11 words are 110 bytes and 10 separators);
  `"a".repeat(115)` for `"a".repeat(115) + " перо"`; every result at most 120 bytes and none ending in `_`.

New `data/source/local/implementation/src/desktopTest/…/implementation/UniqueNameTest.kt`, against a temporary
directory like `RenameTest`, with a private `CountingFileStorage(delegate: FileStorage) : FileStorage by delegate`
that counts `exists`, `list` and `listNames` calls:

- `` `a free name costs one question` `` — empty directory, `uniqueName(SONGS, "a.cho")` is `"a.cho"`; one `exists`,
  no listing.
- `` `the hundredth file of one name is numbered from one listing` `` — write `a.cho` and `a_2.cho` … `a_99.cho`;
  the answer is `"a_100.cho"`; `exists` was called twice, `listNames` once, `list` never.
- `` `the first gap is taken` `` — `a.cho`, `a_2.cho`, `a_4.cho` → `"a_3.cho"`.
- `` `an arriving copy is numbered in brackets` `` — `a.cho` present, `::arrivingCollisionSuffix` → `"a (2).cho"`.
- `` `names in another script collide and are numbered like any other` `` — `катюша.cho` present →
  `"катюша_2.cho"`, and the file written under it reads back.
- `` `the storage has the last word` `` — write `A_2.cho` and `a.cho`, then read
  `val isCaseInsensitive = fileStorage.exists(SONGS, "a_2.cho")` (true on macOS and Windows, false on Linux) and ask
  for `a.cho`: the answer is `"a_3.cho"` where it is true and `"a_2.cho"` where it is false. The set of exact names
  says `a_2.cho` is free on both; only the confirming `exists` keeps the first from writing over `A_2.cho`.

`JvmFileStorageTest`: `` `lists names without filtering them` `` — a song, a `x.cho.1.tmp` and a sub-directory
created by hand in `root/library/songs`; `listNames` returns all three, `list` only the song.

`RenameTest` must pass unchanged.

## Verify

1. The unit test command of the brief, then the three compile checks (the iOS and Wasm actuals of `listNames` are
   only ever compiled there).
2. Desktop (`./gradlew :app:desktop:run`): create a song titled `Катюша` by `Кино` → `кино-катюша.cho` in the
   library folder; a second one with the same header → `кино-катюша_2.cho`. Create a setlist "Летний сет" →
   `летний_сет.setlist.json`. Export the song: the file offered is `кино-катюша.cho`; export the library, unzip it
   with the system's tool and check the entry names; import that zip into an empty library and get the same names.
3. With a library from before the change holding `untitled.cho` (title `Катюша`): the song's menu offers **Update
   file name**, taking it moves the file, a setlist holding the song still shows it, and the entry is gone from the
   menu afterwards. A Latin library shows no new **Update file name** entries at all.
4. Web (`:app:web:wasmJsBrowserDevelopmentRun`): import a zip of a few hundred songs that share one title; it finishes
   in seconds, and the names run `_2` … `_N` without gaps. Repeat step 2's create and export.
5. Android and iOS: step 2's create, then look at the names in `adb shell run-as … ls files/library/songs` and in the
   Files app. With sync connected, run a sync from two devices and confirm the Cyrillic names arrive unchanged and a
   second run plans nothing.

## Docs

- Root `CLAUDE.md`, "**Every name the app writes is normalized** — lowercase unaccented words joined with
  underscores": say "lowercase words joined with underscores, Latin letters without their accents and the letters of
  every other script kept as they are (`катюша.cho`)", and add that the cap is 120 UTF-8 bytes per half.
- `data/model/CLAUDE.md`, the `LibraryFiles.kt` bullet: same rewording of "lowercase unaccented words joined with
  underscores, capped and never empty"; add that marks are kept on letters of other scripts while `isCombiningMark`
  is still dropped after a Latin one, that a character outside the basic plane is a separator, and that Latin letters
  missing from `Accents.kt` deliberately still separate because adding them would rename existing files.
- `data/source/local/implementation/CLAUDE.md`: in the `FileStorage` bullet add `listNames` next to `info` (names
  only, unfiltered, opens nothing); in the `FileNames.kt` bullet replace "`uniqueName` suffixes until the name is
  free" with the one-question / one-listing / storage-has-the-last-word description.
- `documentation/file-format.md`, "File names": "folded to lowercase unaccented words joined with underscores" gains
  "— letters of other alphabets are kept as they are, so `{title: Катюша}` is `катюша.cho`", and a sentence that songs
  named `untitled_N` by an earlier version offer **Update file name**.
- `documentation/issues/29-…md` quotes `LibraryFiles.MAX_NAME_LENGTH` in its Cause section; harmless, but whoever
  lands 29 after this should read it as `MAX_NAME_BYTES`.

## Touches

- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFiles.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNames.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.kt`
- `data/source/local/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`
- `data/source/local/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`
- `data/source/local/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.ios.kt`
- `data/source/local/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.wasmJs.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNamesTest.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/UniqueNameTest.kt` (new)
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorageTest.kt`
- `CLAUDE.md`
- `data/model/CLAUDE.md`
- `data/source/local/implementation/CLAUDE.md`
- `documentation/file-format.md`

## Depends on

nothing in code. Shares `LibraryFiles.kt`, `FileNames.kt` and `FileNamesTest.kt` with plan 02 (which adds
`withoutCollisionSuffix` and rewrites `isNamed`; neither overlaps with `normalizedName` or `uniqueName`, but land them
one after the other), the two `JvmFileStorage.kt` copies with 15, 27 and 29, `FileStorage.wasmJs.kt` with 12 and 27,
and `FileStorage.ios.kt` with 27. Plan 29 should land soon after this one: it is what makes a full-length name
savable on desktop and Android, and full-length names stop being rare once a letter costs two or three bytes.
