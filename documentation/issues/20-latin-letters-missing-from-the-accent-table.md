# 20 · Polish, Turkish, Vietnamese and other Latin letters turn into gaps in file names and are not found by a search without the accent

**Severity:** wrong behaviour (all platforms. Certain for any song whose artist or title carries one of the missing
letters — Polish ą ę ń, Turkish ı ğ ş, Romanian cedilla ş ţ, Croatian/Serbian đ, Lithuanian ė į ų, Slovak ľ ĺ ŕ,
Icelandic þ ð, all of Vietnamese; Hungarian, Czech's č ř š ž and the Western languages are already covered, so the
app's main audience rarely sees it) · **Area:** `:data:model` (`Accents.kt`, `LibraryFiles.normalizedName`),
`:domain:implementation` (`NormalizeTextUseCaseImpl`, `ImportPlanner`)

## Symptom
1. Write a new song titled `Gęsi za wodą` (or import one). It is stored as `g_si_za_wod.cho`: the ę and the ą are
   separators, and the ą at the end of the word is dropped altogether.
2. The same title typed on macOS or taken from a file that stores it decomposed (`e` + U+0328) is stored as
   `gesi_za_woda.cho`. One song, two names, and an import of one next to the other is not recognised as the same
   family, so it is copied again instead of being disregarded.
3. In the song list, a search for `gesi` does not find the composed `Gęsi`, while a search for `gęsi` does not find
   the decomposed one.
4. Other examples: `Işık` → `i_k`, `Şarkı` (cedilla) → `ark`, `Việt Nam` → `vi_t_nam`, `Þú` → `u`, `Đorđe` → `or_e`.

## Cause
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/Accents.kt:24-40` lists only the
Hungarian, Romanian comma-below, Czech and Western accents. `LibraryFiles.normalizedName`
(`LibraryFiles.kt:105-119`) treats every character below U+0370 and U+1E00–1EFF as Latin (`isLatin`, `:166-167`), so a
Latin letter the table does not know is neither kept as a foreign letter nor folded, and falls to the separator:

```kotlin
else -> when (val plain = character.withoutAccent()) {
    ...
    else -> folded.append(if (plain in 'a'..'z' || plain in '0'..'9') plain else NAME_SEPARATOR)
}
```

The decomposed spelling reaches `a..z` because every U+0300–036F mark is dropped (`:112`), which is the only reason
the two spellings split. `NormalizeTextUseCaseImpl` (`domain/implementation/.../useCases/NormalizeTextUseCaseImpl.kt:30-40`)
uses the same table for sorting and search, so it has the same split.

## Fix

### 1. `Accents.kt`: cover every Latin letter that is a base letter plus marks

Replace `withoutAccent` (`:12-40`, KDoc included) with the version below. The letter strings were generated from the
Unicode decompositions of U+00C0–024F and U+1E00–1EFF (every lowercase letter whose NFD form is an ASCII letter
followed by marks), plus the letters with a stroke or bar that have no decomposition (ø đ ð ħ ı ŀ ł ŧ ſ ǥ ƀ ƶ ǿ ȷ).
Only lowercase letters are listed, because both callers lowercase first. Copy the strings exactly, composed (NFC).

```kotlin
/**
 * The lowercase Latin letter behind an accented one, or the character itself when there is none. Shared by the text
 * normalization behind sorting and searching (`:domain:implementation`) and by [LibraryFiles.normalizedName], which
 * both need a song title to come out as the plain letters someone would have typed looking for it.
 *
 * The table is every lowercase letter of Latin-1, Latin Extended-A and -B and Latin Extended Additional that is a
 * plain letter with marks on it, plus the letters drawn with a stroke or a bar, which Unicode does not decompose but
 * which a keyboard without them spells with the bare letter all the same (`ł`, `đ`, the Turkish dotless `ı`). A
 * letter left out of it is not kept either: `normalizedName` turns it into a separator, which files a Polish or a
 * Vietnamese title under a name with holes in it, and under a different name from the same title written decomposed.
 *
 * Only lowercase letters are listed, since both callers lowercase first. A string per letter rather than a map, so
 * that nothing boxes a Char, and everything below the first accented letter returns at once, which is almost every
 * character of almost every song.
 *
 * Anything outside this table is left alone: Kotlin's common standard library has no Unicode normalizer, so this is
 * the whole of what the app knows about accents. The letters that fold to two ("ß", "æ", "þ"…) are the file name's
 * business, see [LibraryFiles.normalizedName].
 */
fun Char.withoutAccent() = when {
    this < FIRST_ACCENTED_LETTER -> this
    this in "àáâãäåāăąǎǟǡǻȁȃȧḁạảấầẩẫậắằẳẵặẚ" -> 'a'
    this in "ƀḃḅḇ" -> 'b'
    this in "çćĉċčḉ" -> 'c'
    this in "ďđðḋḍḏḑḓ" -> 'd'
    this in "èéêëēĕėęěȅȇȩḕḗḙḛḝẹẻẽếềểễệ" -> 'e'
    this in "ḟ" -> 'f'
    this in "ĝğġģǥǧǵḡ" -> 'g'
    this in "ĥħȟḣḥḧḩḫẖ" -> 'h'
    this in "ìíîïĩīĭįıǐȉȋḭḯỉị" -> 'i'
    this in "ĵǰȷ" -> 'j'
    this in "ķǩḱḳḵ" -> 'k'
    this in "ĺļľŀłḷḹḻḽ" -> 'l'
    this in "ḿṁṃ" -> 'm'
    this in "ñńņňǹṅṇṉṋ" -> 'n'
    this in "òóôõöøōŏőơǒǫǭǿȍȏȫȭȯȱṍṏṑṓọỏốồổỗộớờởỡợ" -> 'o'
    this in "ṕṗ" -> 'p'
    this in "ŕŗřȑȓṙṛṝṟ" -> 'r'
    this in "śŝşšșſṡṣṥṧṩẛ" -> 's'
    this in "ţťțŧṫṭṯṱẗ" -> 't'
    this in "ùúûüũūŭůűųưǔǖǘǚǜȕȗṳṵṷṹṻụủứừửữự" -> 'u'
    this in "ṽṿ" -> 'v'
    this in "ŵẁẃẅẇẉẘ" -> 'w'
    this in "ẋẍ" -> 'x'
    this in "ýÿŷȳẏẙỳỵỷỹ" -> 'y'
    this in "źżžƶẑẓẕ" -> 'z'
    else -> this
}

/** À, the first accented letter of Latin-1: nothing below it has an accent to lose. */
private const val FIRST_ACCENTED_LETTER = '\u00C0'
```

`isCombiningMark` below it stays as it is.

### 2. `LibraryFiles.normalizedName`: the letters that fold to two

In `LibraryFiles.kt:114-119`, extend the inner `when` so the ligatures and thorn become two letters rather than a
separator:

```kotlin
else -> when (val plain = character.withoutAccent()) {
    'ß' -> folded.append("ss")
    'æ', 'ǣ', 'ǽ' -> folded.append("ae")
    'œ' -> folded.append("oe")
    'þ' -> folded.append("th")
    'ĳ' -> folded.append("ij")
    'ǆ', 'ǳ' -> folded.append("dz")
    'ǉ' -> folded.append("lj")
    'ǌ' -> folded.append("nj")
    else -> folded.append(if (plain in 'a'..'z' || plain in '0'..'9') plain else NAME_SEPARATOR)
}
```

Every output is `a..z`, so the rule stays idempotent. Nothing else in `normalizedName` changes; `isLatin` already
routes all of these here.

### 3. `ImportPlanner.planSongs` / `planSetlists`: also recognise the library file a song arrived under

This is what keeps the table change (and every earlier change of the naming rule) from copying a song twice. An
exported archive hands its songs out under their *library* names, and the import names each song by its header. A
library file named under the old table (`g_si_za_wod.cho`) is exported under that name, re-imported, and derives
`gesi_za_woda.cho` — a different family, so today the planner marks it NEW and writes a second copy. The same
happens already for every library file that predates normalization and was never renamed (a hand-placed
`Tükörfúrógép - Árvíz.cho`). An identical text under the exact name the song arrived with is the same song.

In `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlanner.kt`,
`planSongs`:

```kotlin
val libraryFamilies = libraryFileNames.groupedByFamily(LibraryFiles.SONG_EXTENSIONS)
val libraryFileNameSet = libraryFileNames.toHashSet()
...
    // The library file the song arrived under, where that is not its own name. An export hands its songs out under
    // their library names while the import names each one by its header, and a library file named before the rule it
    // would be named by today (a letter the table did not know yet, or a file nobody ever renamed) is still the same
    // song when it holds the same text.
    val arrivedAs = song.sourceFileName?.takeIf { it != song.fileName && it in libraryFileNameSet }
    // Most songs of most imports are the only one of their name, and folding the text of every one of them
    // for a comparison nothing asks for would copy the whole batch once more.
    val comparable = if (family.hasNothingToCompareWith && arrivedAs == null) null else ChordProSplitter.comparable(song.text)
    val libraryFileName = comparable?.let(family::libraryFileNameOf)
        ?: arrivedAs?.takeIf { readLibraryText(it)?.let(ChordProSplitter::comparable) == comparable }
```

(the existing comment above `comparable` moves below the new one; the `when` that follows is unchanged).
`readLibraryText` is only called for `arrivedAs` when the family did not already match, so an archive of the
library's own files under their current names reads nothing more than before
(`numberedArrivalsAreHandledInTheirLibraryOrderAndOnlyReadTheirFamily` keeps passing).

In `planSetlists`, the same for a setlist, whose file name follows its title:

```kotlin
val identical = members.firstOrNull { it.holdsTheSameAs(setlist) }
    // See planSongs: a setlist file named before today's rule, arriving under that name.
    ?: librarySetlistsByFileName[sourceFileName]?.takeIf { it.holdsTheSameAs(setlist) }
```

which needs the destructuring in the `map` to name the second component: `.map { (setlist, sourceFileName) ->`.

## What this does to names that already exist
- **Nothing in the library is renamed.** Names are derived only when a song is written in the editor, imported or
  renamed on request, and a setlist's when its title is edited. A song stored as `g_si_za_wod.cho` keeps that name.
  Because its header now derives `gesi_za_woda.cho`, `Song.canUpdateFileName` becomes true once and its menu offers
  **Update file name** — the documented behaviour for a file whose name and header have drifted apart. The same goes
  for a setlist, whose file simply follows its title the next time the title is edited.
- **Sync** is keyed by name and never derives one (the conflict copy's ` (2)` is appended to the existing name), so
  a sync run moves nothing because of this change, and the two tables never race. With two devices on different
  versions: a song created on the new version as `gesi_za_woda.cho` arrives on the old one under that name, where
  the old table derives `g_si_za_wod.cho` and offers **Update file name**; a song created on the old version offers
  it on the new one. If the user takes the offer on both devices, each rename reaches the other as a deletion and a
  new file, and the other device then offers the rename back. That only happens by hand, costs nothing but the
  offer, and ends when both devices run the same version. Say so in the release notes only if a user reports it.
- **Import dedup**: an archive exported before the change (or by a device still on the old version) names the song
  `g_si_za_wod.cho`; its header now derives `gesi_za_woda.cho`. Without step 3 the import writes a second copy;
  with step 3 it is disregarded as identical to `g_si_za_wod.cho` and a setlist in the same archive keeps pointing at
  that file (`storedSongFileNames` maps the arriving name to the library file). A *different* text under the same
  title goes in as `gesi_za_woda.cho` without a question, since the library has nothing under that name.
- **Search and sorting** use the same table, so `gesi` now finds both spellings of `Gęsi`, and `Işık` sorts among
  the I's. Nothing about sorting is persisted.
- The demo songs are unaffected (their headers are ASCII).

## Tests
- `data/source/local/implementation/src/commonTest/.../FileNamesTest.kt`, new test:

  ```kotlin
  @Test
  fun everyLatinLetterFoldsToItsBaseLetter() {
      assertEquals("gesi_za_woda", LibraryFiles.normalizedName("Gęsi za wodą"))
      // The same title decomposed, which is how macOS and some tools store it, arrives at the same name.
      assertEquals("gesi_za_woda", LibraryFiles.normalizedName("Ge\u0328si za woda\u0328"))
      assertEquals("isik", LibraryFiles.normalizedName("Işık"))
      assertEquals("istanbul", LibraryFiles.normalizedName("İstanbul"))
      // The cedilla spelling of the Romanian letters is at least as common as the comma below.
      assertEquals("sarki", LibraryFiles.normalizedName("Şarkı"))
      assertEquals("tara", LibraryFiles.normalizedName("Ţara"))
      assertEquals("viet_nam", LibraryFiles.normalizedName("Việt Nam"))
      assertEquals("dorde", LibraryFiles.normalizedName("Đorđe"))
      assertEquals("thu", LibraryFiles.normalizedName("Þú"))
      assertEquals("ijsselmeer", LibraryFiles.normalizedName("Ĳsselmeer"))
      listOf("gesi_za_woda", "isik", "viet_nam", "thu").forEach { assertEquals(it, LibraryFiles.normalizedName(it)) }
  }
  ```

- `domain/implementation/src/commonTest/.../ImportPlannerTest.kt`, new tests:

  ```kotlin
  @Test
  fun aSongIsRecognizedUnderTheLibraryNameItArrivedWith() = runTest {
      // Exported under a name the library gave it before the naming rule changed, and named by its header now.
      val plan = plan(library = mapOf("old_name.cho" to A), song(text = A, sourceFileName = "old_name.cho"))

      assertEquals(listOf("old_name.cho"), plan.map { it.fileName })
      assertEquals(listOf(ImportPlan.Status.IDENTICAL), plan.map { it.status })
  }

  @Test
  fun aDifferentSongUnderTheNameItArrivedWithIsNew() = runTest {
      val plan = plan(library = mapOf("old_name.cho" to B), song(text = A, sourceFileName = "old_name.cho"))

      assertEquals(listOf("x.cho"), plan.map { it.fileName })
      assertEquals(listOf(ImportPlan.Status.NEW), plan.map { it.status })
  }

  @Test
  fun aSetlistIsRecognizedUnderTheLibraryNameItArrivedWith() {
      val library = setlist(fileName = "old_name.setlist.json", title = "Summer set")
      val planned = ImportPlanner.planSetlists(
          incoming = listOf(ImportPlanner.IncomingSetlist(library.copy(fileName = "summer_set.setlist.json"), "old_name.setlist.json")),
          librarySetlists = listOf(library),
      )

      assertEquals(listOf("old_name.setlist.json"), planned.map { it.fileName })
      assertEquals(listOf(ImportPlan.Status.IDENTICAL), planned.map { it.status })
  }
  ```

Run `./gradlew :data:source:local:implementation:desktopTest :domain:implementation:desktopTest` (in a worktree).

## Verify
1. Desktop: create a song with artist `Kasia` and title `Gęsi za wodą`. The file in the library folder is
   `kasia-gesi_za_woda.cho`. Search `gesi`: it is found.
2. Put a file named `g_si_za_wod.cho` with `{title: Gęsi za wodą}` into the library folder (as an older version
   would have written it). Its menu offers **Update file name**; taking it renames it to `gesi_za_woda.cho`.
3. Before step 2's rename, export the library, then import the archive: the import reports the song as already
   there, not as a new file.
4. Compile `:app:desktop:run`, `:app:android:assembleDebug` and `:app:web:wasmJsBrowserDevelopmentRun` (the table
   is common code; the web build is the one where a `Char in String` check is worth a glance for speed with a large
   library: sort a 1000-song library and see no stall).

## Docs
- `data/model/CLAUDE.md`, the `domain/LibraryFiles.kt` bullet: replace "It folds accents through `Accents.kt`'s
  `withoutAccent`, which lives here rather than next to the search normalization that also uses it, since two
  modules now need the same table." with "It folds accents through `Accents.kt`'s `withoutAccent` — every Latin
  letter that is a plain letter with marks on it, and the ones drawn with a stroke (`ł`, `đ`, the dotless `ı`), since
  a letter the table does not know becomes a separator — which lives here rather than next to the search
  normalization that also uses it, since two modules need the same table. The ligatures and `þ` fold to two letters
  in `normalizedName` itself."
- `domain/implementation/CLAUDE.md:44-46`: replace "the name the file arrived under is passed as a fallback *title*,
  for the songs that declare none, and is otherwise not used at all." with "the name the file arrived under is
  passed as a fallback *title*, for the songs that declare none, and is otherwise only used to recognise a library
  file of exactly that name holding the same text — an export hands songs out under their library names, and one
  named before today's rule is still the same song."
- Root `CLAUDE.md:170` and `documentation/features.md:49`: after "a song the library already holds under that name
  or a numbered sibling of it (`x_2.cho`)" add "— or under the very name it arrived with, which is what an export of
  a file named by an older rule carries —".

## Touches
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/Accents.kt`
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFiles.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlanner.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNamesTest.kt`
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlannerTest.kt`
- `data/model/CLAUDE.md`, `domain/implementation/CLAUDE.md`, `CLAUDE.md`, `documentation/features.md`

## Depends on
None.
