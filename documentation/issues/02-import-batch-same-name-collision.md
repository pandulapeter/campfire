# 02 · Two imported songs wanting one file name raise a bogus question, and "Replace" or "Skip" loses one

**Severity:** data loss (all platforms; every library that holds a `…_2.cho`, which "Keep both", sync and the editor
all produce, hits it on its first export → import round trip) · **Area:** `:domain:implementation`
(`PrepareImportUseCaseImpl`, `ImportFilesUseCaseImpl`, new `ImportPlanner`), `:data:model` (`ImportPlan`,
`LibraryFiles`), `:data:source:local:implementation` (`FileNames.kt`)

## Symptom

A. **Restoring a backup into an empty library.** The library holds two arrangements of one song with the same
   `{artist}` / `{title}` and no `{subtitle}`: `john_newton-amazing_grace.cho` and `john_newton-amazing_grace_2.cho`.
   Export the library, import the zip on a new device. The "Some names are taken" dialog appears although the library
   is empty. **Replace** writes the first arrangement and then writes the second over it; **Skip them** never imports
   the second. Either way one song of the backup is gone, the result message counts nothing as lost, and a setlist
   that held both now holds one. Only **Keep both** restores the library.
B. **Importing the same export back into the library it came from.** `…_2.cho` is named by its header again, which
   is the un-numbered name, and is held against the *other* arrangement there. It is reported as a conflict although
   an identical file sits in the library as `…_2.cho`. **Replace** overwrites arrangement 1 with arrangement 2 — the
   library now holds arrangement 2 twice and arrangement 1 not at all — and **Keep both** writes a third copy as `_3`.
C. The same happens to a `.txt` collection whose parts declare no `{title}` (every part derives `untitled.cho`), and
   to two setlists of one batch whose titles normalize to one name ("Summer set" / "Summer Set!").

## Cause

`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/PrepareImportUseCaseImpl.kt:91-101`
(songs) and `:129-137` (setlists):

```kotlin
val existingText = plannedTexts[fileName] ?: songContentRepository.loadSongContent(fileName, shouldCache = false)?.text
val status = when {
    existingText == null -> ImportPlan.Status.NEW
    ChordProSplitter.comparable(existingText) == ChordProSplitter.comparable(text) -> ImportPlan.Status.IDENTICAL
    else -> ImportPlan.Status.CONFLICTING
}
if (status == ImportPlan.Status.NEW) {
    plannedTexts[fileName] = text
}
```

Two things are wrong with the one lookup:

- `CONFLICTING` means "the library holds something else under this name; ask, and `REPLACE` may overwrite it"
  (`ImportFilesUseCaseImpl.kt:114-122`), but it is also what a song gets when the name is only claimed by an *earlier
  song of the same batch*. `importSong(shouldReplace = true)` then overwrites a file the same import wrote a moment
  ago, and `SKIP` drops a song whose name nothing in the library ever had.
- Only the derived, un-numbered name is looked at. The storage layer numbers a colliding name (`FileNames.kt:53-73`,
  `_2`, `_3`…), so the identical copy of an incoming song is just as often under `name_2.cho` — and an entry of an
  exported archive is *named* `name_2.cho`, but that arriving name is deliberately not used for naming
  (`importFileName` reads the header), so nothing ever looks there.

How the archive's own names survive this: `ImportPlan.SongEntry.sourceFileName` carries the name a file arrived
under (the zip entry's name with its path stripped, `ArchiveLocalSourceImpl.kt:56`), and
`ImportFilesUseCaseImpl.kt:50,56,59,70` builds `storedSongFileNames[sourceFileName] = <name the song has in the
library>` and rewrites every setlist entry of the batch through it. That mapping is independent of the header-derived
name and is sound — what is wrong is only what it is fed: an `IDENTICAL` entry maps to the derived name (`x.cho`)
even where the identical file is `x_2.cho`, and with Replace two source names map to the one surviving file.

## Fix

The rules, which the code below implements:

1. An incoming song identical (`ChordProSplitter.comparable`) to **any library file of its collision family** is
   `IDENTICAL`, and its entry's `fileName` is *that* file's name, so the setlists of the batch are pointed at it. The
   family of `x.cho` is every library song whose name, extension aside, is `x` or `x` plus a collision number — both
   shapes `FileNames.kt` writes, `x_2` and the `x (2)` of a sync conflict copy — under any extension of the ChordPro
   family (an exported `x.crd` derives `x.cho` and would otherwise come back as a duplicate).
2. One identical to an **earlier song of the same batch** is `IDENTICAL` too, and says which
   (`repeatedEntryIndex`), because where that earlier song ends up is only known once it has been written.
3. Two different songs of one batch never conflict with each other: the later one is `NEW`.
4. `CONFLICTING` is only ever "the derived name is taken *in the library* by different content, no family member
   matches, and no earlier song of this batch has already raised the question for that name". There is at most one
   per name per batch, so `REPLACE` overwrites one library file once; a second different song for the same name is
   `NEW` and lands numbered.

**The number is not decided at plan time, on purpose.** A `NEW` entry keeps the derived, un-numbered name in
`fileName`, and `importSong(shouldReplace = false)` numbers it through `uniqueName` when it is written. Do not pass a
pre-numbered name: `uniqueName("x_2.cho")` answers `x_2_2.cho` when that is taken, and only the write can see the
disk at that moment (a sync run may be writing next to the import). The setlists follow the *actual* name, which the
apply step records per entry — that is what requirement "setlists in the batch can follow it" needs, and it already
works that way.

Steps, in this order:

1. **`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFiles.kt`** — the
   collision suffix becomes vocabulary, next to `NAME_SEPARATOR`, because the import policy now has to recognise it
   and must not grow a second copy of the rule. Add after `NAME_SEPARATOR`:

   ```kotlin
   /**
    * [name] — a file name without its extension — without the number a collision added to it, or null where it
    * carries none. Both shapes are recognised: the `_2` of a name the app derived itself, and the ` (2)` sync gives
    * the copy of a file that changed on both sides. `route_66` reads as a numbered `route`, which is harmless to
    * everyone who asks: a family is only ever where to look for a file, never proof that one belongs to it.
    */
   fun withoutCollisionSuffix(name: String): String? = COLLISION_SUFFIX.find(name)
       ?.let { name.substring(startIndex = 0, endIndex = it.range.first) }
       ?.takeIf { it.isNotEmpty() }
   ```

   and, with the other private values at the bottom of the object:

   ```kotlin
   /** Both shapes a colliding name is numbered in, anchored to the end of the name. */
   private val COLLISION_SUFFIX = Regex("""(_\d+| \(\d+\))$""")
   ```

2. **`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNames.kt`**
   — `isNamed` reads the shared rule; delete the private `COLLISION_SUFFIX` at the bottom of the file and its KDoc.
   The behaviour is unchanged (`FileNamesTest.aNameTheAppGaveIsRecognizedAsItsOwn` pins it):

   ```kotlin
   internal fun String.isNamed(desired: String): Boolean {
       val extension = knownExtension()
       if (!extension.equals(desired.knownExtension(), ignoreCase = true)) return false
       val base = removeSuffix(extension)
       val desiredBase = desired.removeSuffix(desired.knownExtension())
       return base == desiredBase || LibraryFiles.withoutCollisionSuffix(base) == desiredBase
   }
   ```

   `uniqueName`, `normalizedCollisionSuffix` and `arrivingCollisionSuffix` stay as they are.

3. **`data/model/.../domain/ImportPlan.kt`** — `SongEntry` gains one defaulted field, and the documentation of the
   statuses is brought in line:

   ```kotlin
   data class SongEntry(
       /**
        * The name it wants in the library, already derived the way the storage layer derives one — or, for an
        * [Status.IDENTICAL] entry, the name of the library file that already is this song, which may be a numbered
        * sibling of the name it would have wanted.
        */
       val fileName: String,
       val text: String,
       val status: Status,
       /**
        * The name of the file it arrived in, which is what a setlist travelling with it points at. Null for one
        * song of a collection, since the file it came from named none of them.
        */
       val sourceFileName: String?,
       /**
        * For an [Status.IDENTICAL] entry that repeats an earlier song of the same import rather than a library file:
        * the place of that song in [ImportPlan.songs]. Where it ends up is only known once it has been written, so
        * the plan can name the entry but not the file.
        */
       val repeatedEntryIndex: Int? = null,
   )
   ```

   `Status.NEW`: "The name is free, or taken only by an earlier file of the same import, so the file goes in and the
   storage layer numbers it if it has to." `Status.IDENTICAL`: "The library — under this name or a numbered sibling
   of it — or an earlier file of the same import already is exactly this, so the import has nothing to do. …" (keep
   the sentence about what is compared). `Status.CONFLICTING`: "The name is taken *in the library* by something
   else, which only the user can decide about. At most one entry per name: a second file of the same import wanting
   it is [NEW]."

4. **New file `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlanner.kt`**
   (MPL header; next to `ExportFileNames.kt`). Pure apart from the one suspending lambda that reads a library text,
   which is what makes it testable the way `SyncPlanner` is:

   ```kotlin
   package com.pandulapeter.campfire.domain.implementation

   import com.pandulapeter.campfire.chordpro.ChordProSplitter
   import com.pandulapeter.campfire.data.model.domain.ImportPlan
   import com.pandulapeter.campfire.data.model.domain.LibraryFiles
   import com.pandulapeter.campfire.data.model.domain.Setlist

   /**
    * What an import does with each song and setlist of a batch, as a function of the batch and of what the library
    * holds. Nothing is written and nothing is numbered here: a file that goes in keeps the name its header derives,
    * and the storage layer numbers it as it is written, which is the only moment that can see what is on disk.
    *
    * A name stands for a whole family — `x`, `x_2`, `x_3`… — because the storage layer numbers a name that is taken,
    * while an incoming song is always named by its header and so always asks for the un-numbered one. Holding it
    * against that one file alone reports the second arrangement of a song as a conflict with the first on every
    * re-import of an export, and offers to replace the first with it.
    */
   internal object ImportPlanner {

       /** One song of the batch, already named by its own header (`SongRepository.importFileName`). */
       data class IncomingSong(
           val fileName: String,
           val text: String,
           val sourceFileName: String?,
       )

       /** One setlist of the batch, as `SetlistRepository.parseSetlist` named it, and the name of the file it arrived in. */
       data class IncomingSetlist(
           val setlist: Setlist,
           val sourceFileName: String,
       )

       /**
        * @param libraryFileNames Every song file of the library, by name alone: which of them belong to a family is
        *   decided from the names, so that only those are ever read.
        * @param readLibraryText The text of one library song, null where there is no such file. Asked at most once
        *   per file, and only for the derived names and their families.
        */
       suspend fun planSongs(
           incoming: List<IncomingSong>,
           libraryFileNames: Collection<String>,
           readLibraryText: suspend (fileName: String) -> String?,
       ): List<ImportPlan.SongEntry> {
           val libraryFamilies = libraryFileNames.groupedByFamily(LibraryFiles.SONG_EXTENSIONS)
           val families = mutableMapOf<String, SongFamily>()
           return incoming.inArrivingOrder(LibraryFiles.SONG_EXTENSIONS, { it.fileName }, { it.sourceFileName }).mapIndexed { index, song ->
               val family = families.getOrPut(song.fileName) {
                   readSongFamily(
                       desired = song.fileName,
                       members = song.fileName.familyKeys(LibraryFiles.SONG_EXTENSIONS).firstOrNull()?.let(libraryFamilies::get).orEmpty(),
                       readLibraryText = readLibraryText,
                   )
               }
               // Most songs of most imports are the only one of their name, and folding the text of every one of them
               // for a comparison nothing asks for would copy the whole batch once more.
               val comparable = if (family.hasNothingToCompareWith) null else ChordProSplitter.comparable(song.text)
               val libraryFileName = comparable?.let(family::libraryFileNameOf)
               val repeatedEntryIndex = comparable?.let(family::plannedEntryIndexOf)
               when {
                   libraryFileName != null -> song.toEntry(fileName = libraryFileName, status = ImportPlan.Status.IDENTICAL)
                   repeatedEntryIndex != null -> song.toEntry(status = ImportPlan.Status.IDENTICAL, repeatedEntryIndex = repeatedEntryIndex)
                   else -> {
                       family.plan(index = index, text = song.text)
                       // The question is about the one file the library has under this name, so it is raised once:
                       // a second song wanting the name has nothing left to replace, and goes in numbered.
                       val isConflicting = family.isNameTaken && !family.hasConflict
                       family.hasConflict = family.hasConflict || isConflicting
                       song.toEntry(status = if (isConflicting) ImportPlan.Status.CONFLICTING else ImportPlan.Status.NEW)
                   }
               }
           }
       }

       fun planSetlists(incoming: List<IncomingSetlist>, librarySetlists: List<Setlist>): List<ImportPlan.SetlistEntry> {
           val libraryFamilies = librarySetlists.map { it.fileName }.groupedByFamily(SETLIST_EXTENSIONS)
           val librarySetlistsByFileName = librarySetlists.associateBy { it.fileName }
           val plannedSetlists = mutableMapOf<String, MutableList<Setlist>>()
           val conflictingFileNames = mutableSetOf<String>()
           return incoming.inArrivingOrder(SETLIST_EXTENSIONS, { it.setlist.fileName }, { it.sourceFileName }).map { (setlist, _) ->
               val members = setlist.fileName.familyKeys(SETLIST_EXTENSIONS).firstOrNull()?.let(libraryFamilies::get).orEmpty()
                   .mapNotNull(librarySetlistsByFileName::get)
               val identical = members.firstOrNull { it.holdsTheSameAs(setlist) }
               val planned = plannedSetlists.getOrPut(setlist.fileName) { mutableListOf() }
               when {
                   identical != null -> ImportPlan.SetlistEntry(fileName = identical.fileName, setlist = setlist, status = ImportPlan.Status.IDENTICAL)
                   planned.any { it.holdsTheSameAs(setlist) } ->
                       ImportPlan.SetlistEntry(fileName = setlist.fileName, setlist = setlist, status = ImportPlan.Status.IDENTICAL)

                   else -> {
                       planned += setlist
                       val isConflicting = setlist.fileName in librarySetlistsByFileName && conflictingFileNames.add(setlist.fileName)
                       ImportPlan.SetlistEntry(
                           fileName = setlist.fileName,
                           setlist = setlist,
                           status = if (isConflicting) ImportPlan.Status.CONFLICTING else ImportPlan.Status.NEW,
                       )
                   }
               }
           }
       }

       /**
        * A setlist is compared by what is in it rather than by its stored document, which carries a priority the
        * import assigns itself and would therefore never match. Its description and whether it is archived do count,
        * since both are the user's own words about the setlist and travel with the file the way its title does. The
        * entries are held against the names they arrived with: a song that had to be renamed is followed when the
        * plan is applied, and a setlist pointing at one is a different setlist anyway.
        */
       private fun Setlist.holdsTheSameAs(other: Setlist) =
           title == other.title && description == other.description && isArchived == other.isArchived && entries == other.entries

       private fun IncomingSong.toEntry(
           status: ImportPlan.Status,
           fileName: String = this.fileName,
           repeatedEntryIndex: Int? = null,
       ) = ImportPlan.SongEntry(
           fileName = fileName,
           text = text,
           status = status,
           sourceFileName = sourceFileName,
           repeatedEntryIndex = repeatedEntryIndex,
       )

       /**
        * The library's half of a family, read once per import however many incoming songs ask about it: a collection
        * of fifty untitled songs all derive `untitled.cho`.
        *
        * The derived name is asked for even when the listing does not have it. The listing is the cached scan, which
        * a file dropped into the folder since is not part of, and on a case-insensitive file system the name answers
        * for a file the listing spells with capitals.
        */
       private suspend fun readSongFamily(
           desired: String,
           members: List<String>,
           readLibraryText: suspend (fileName: String) -> String?,
       ): SongFamily {
           val libraryFileNames = mutableMapOf<String, String>()
           var isNameTaken = false
           // The name itself first and the rest by their number, so that where the library holds the same text twice
           // an incoming copy of it is always said to be the same one of them.
           val candidates = (members + desired).distinct().sortedWith(compareBy<String>({ it != desired }, { it.length }, { it }))
           candidates.forEach { fileName ->
               val text = readLibraryText(fileName) ?: return@forEach
               if (fileName == desired) isNameTaken = true
               libraryFileNames.getOrPut(ChordProSplitter.comparable(text)) { fileName }
           }
           return SongFamily(isNameTaken = isNameTaken, libraryFileNames = libraryFileNames)
       }

       private class SongFamily(
           /** Whether the library holds a file under the derived name itself, which is what makes a different song a question. */
           val isNameTaken: Boolean,
           /** The library file each comparable text of the family is found in. */
           private val libraryFileNames: Map<String, String>,
       ) {
           private val plannedSongs = mutableListOf<IndexedValue<String>>()
           private val plannedEntryIndices = mutableMapOf<String, Int>()
           private var foldedSongCount = 0
           var hasConflict = false

           val hasNothingToCompareWith get() = libraryFileNames.isEmpty() && plannedSongs.isEmpty()

           fun libraryFileNameOf(comparable: String) = libraryFileNames[comparable]

           /** The texts planned so far are only folded once a second song asks about them, which most never see. */
           fun plannedEntryIndexOf(comparable: String): Int? {
               while (foldedSongCount < plannedSongs.size) {
                   val song = plannedSongs[foldedSongCount++]
                   plannedEntryIndices.getOrPut(ChordProSplitter.comparable(song.value)) { song.index }
               }
               return plannedEntryIndices[comparable]
           }

           fun plan(index: Int, text: String) {
               plannedSongs += IndexedValue(index = index, value = text)
           }
       }

       /**
        * The names a library file is found under when a family is looked up: its own, and the one it is a numbered
        * variant of. Lowercase and without the extension, since a file written by hand may be `X_2.CRD` and still be
        * the song an incoming `x.cho` is. Empty for a name of neither kind.
        */
       private fun String.familyKeys(extensions: List<String>): List<String> {
           val extension = extensions.firstOrNull { endsWith(it, ignoreCase = true) } ?: return emptyList()
           val name = dropLast(extension.length).lowercase()
           return listOfNotNull(name, LibraryFiles.withoutCollisionSuffix(name))
       }

       /** One pass over the library's names, so that looking a family up costs nothing per incoming file. */
       private fun Collection<String>.groupedByFamily(extensions: List<String>): Map<String, List<String>> {
           val families = mutableMapOf<String, MutableList<String>>()
           forEach { fileName -> fileName.familyKeys(extensions).forEach { families.getOrPut(it) { mutableListOf() } += fileName } }
           return families
       }

       /**
        * The batch with the files that arrived under a numbered variant of their own derived name moved behind the
        * rest, lowest number first, and otherwise left as it came. An exported library restored into an empty one
        * then hands `x.cho` and `x_2.cho` out to the songs that had them, whatever order the archive lists them in —
        * which matters once sync is connected, where the same two names holding each other's text are two conflicts.
        */
       private fun <T> List<T>.inArrivingOrder(
           extensions: List<String>,
           fileName: (T) -> String,
           sourceFileName: (T) -> String?,
       ) = sortedBy { item ->
           val desired = fileName(item).familyKeys(extensions).firstOrNull()
           val arrivedAs = sourceFileName(item)?.familyKeys(extensions).orEmpty()
           if (desired == null || arrivedAs.size < 2 || arrivedAs[1] != desired) {
               1
           } else {
               arrivedAs[0].removePrefix(desired).filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
           }
       }

       private val SETLIST_EXTENSIONS = listOf(LibraryFiles.SETLIST_EXTENSION)
   }
   ```

   `sortedBy` is stable, which the "otherwise left as it came" relies on. `repeatedEntryIndex` indexes the list this
   function *returns* (the sorted order), which is `ImportPlan.songs`.

5. **`PrepareImportUseCaseImpl.kt`** — `planSongs` and `planSetlists` only gather the batch and hand it over; delete
   `plannedTexts`, `plannedSetlists`, `existingSetlists` and the private `holdsTheSameAs` (it moved), and replace the
   KDoc of both functions:

   ```kotlin
   /** Every song of the batch under the name its own header gives it, held against the library by [ImportPlanner]. */
   private suspend fun planSongs(files: List<ImportedFile>, skippedFileNames: MutableList<String>): List<ImportPlan.SongEntry> {
       val incoming = files.flatMap { file ->
           val parts = ChordProSplitter.split(file.bytes.decodeLibraryText())
           if (parts.isEmpty()) {
               skippedFileNames += file.name
               return@flatMap emptyList()
           }
           parts.map { part ->
               // (keep the two existing comments about fallbackTitle and the trailing newline)
               val fallbackTitle = if (parts.size == 1) file.name.substringBeforeLast('.') else ""
               val text = part + "\n"
               ImportPlanner.IncomingSong(
                   fileName = songRepository.importFileName(fallbackTitle = fallbackTitle, text = text),
                   text = text,
                   sourceFileName = file.name.takeIf { parts.size == 1 },
               )
           }
       }
       return ImportPlanner.planSongs(
           incoming = incoming,
           // The names come from the scan the app already made, so finding the numbered siblings of a name costs no
           // listing of the directory - on the web a listing opens every file - and only those siblings are read.
           libraryFileNames = songRepository.loadSongsIfNeeded().orEmpty().map { it.fileName },
           // Not cached: an import walks files the library has no other reason to hold on to.
           readLibraryText = { fileName -> songContentRepository.loadSongContent(fileName, shouldCache = false)?.text },
       )
   }

   private suspend fun planSetlists(files: List<ImportedFile>, skippedFileNames: MutableList<String>): List<ImportPlan.SetlistEntry> {
       val incoming = files.mapNotNull { file ->
           val setlist = setlistRepository.parseSetlist(file.bytes.decodeLibraryText())
           if (setlist == null) {
               skippedFileNames += file.name
               return@mapNotNull null
           }
           ImportPlanner.IncomingSetlist(setlist = setlist, sourceFileName = file.name)
       }
       return ImportPlanner.planSetlists(incoming = incoming, librarySetlists = setlistRepository.loadSetlistsIfNeeded().orEmpty())
   }
   ```

   Cost on a 3000-song library: no directory listing, one pass over 3000 names to build the family index, and reads
   only of the files whose name is in the family of an incoming song. A re-import of the whole library reads each
   library file once (as today); an import of new songs reads nothing but one failed probe per derived name (as
   today). A `null` from `loadSongsIfNeeded()` (the scan failed) degrades to today's behaviour: only the derived name
   is probed.

6. **`ImportFilesUseCaseImpl.kt`** — the songs loop records where each *entry* ended up, resolves a repeat through
   it, and never replaces the same name twice (the planner never asks it to; the guard is for a plan somebody else
   built, since this is the one place a library file is overwritten). Replace the `plan.songs.forEach { … }` block:

   ```kotlin
   // Where each song of the plan ended up, by its place in the plan: a repeat of an earlier song of the batch is
   // wherever that one went, which was not known when the plan was made.
   val storedNames = arrayOfNulls<String>(plan.songs.size)
   val replacedSongFileNames = mutableSetOf<String>()
   plan.songs.forEachIndexed { index, entry ->
       val storedName = when (val action = entry.action(resolution)) {
           Action.WRITE, Action.REPLACE -> songRepository.importSong(
               fileName = entry.fileName,
               text = entry.text,
               shouldReplace = action == Action.REPLACE && replacedSongFileNames.add(entry.fileName),
           ).fileName.also { importedSongFileNames += it }

           // Already in the library, or already written by this import, so the name it arrived under points there.
           Action.DISREGARD -> (entry.repeatedEntryIndex?.let(storedNames::getOrNull) ?: entry.fileName).also { duplicateFileNames += it }
           Action.LEAVE_ALONE -> entry.fileName
       }
       storedNames[index] = storedName
       entry.sourceFileName?.let { storedSongFileNames[it] = storedName }
   }
   ```

   and in the setlists loop, the same guard: declare `val replacedSetlistFileNames = mutableSetOf<String>()` before
   it and pass `shouldReplace = action == Action.REPLACE && replacedSetlistFileNames.add(entry.fileName)`. Update
   the comment inside `action()`: "The name is taken, so writing under it is what produces the "_2" the storage layer
   suffixes — which is also how a `NEW` entry whose name an earlier file of the same import took gets its number."
   Nothing else changes: songs are still written before setlists, the rescans stay in the `finally`.

7. **`presentation/src/commonMain/composeResources/values/strings.xml` and `values-hu/strings.xml`** — the "Keep
   both" description names a suffix the import has not written since names were normalized; the dialog this plan is
   about should say what happens:
   - `import_conflicts_keep_both_description`: EN `Import them next to the files they collide with, numbered \"_2\"`
     · HU `Importálás a meglévő fájlok mellé, \"_2\" számozással`

8. **`domain/implementation/build.gradle.kts`** — the planner is the first thing in this module worth a unit test;
   add, inside `sourceSets { … }` after `commonMain.dependencies { … }`:

   ```kotlin
   commonTest.dependencies {
       implementation(kotlin("test"))
       implementation(libs.kotlin.coroutines.test)
   }
   ```

What must NOT change: `importFileName` keeps naming by the header (the arriving name only orders the batch, step 4);
`REPLACE` never applies to a `NEW` entry; texts are compared through `ChordProSplitter.comparable` only, never raw;
no `fileStorage.list` per incoming song; archive entries with the same stripped name from two folders still share one
`sourceFileName` (the last one wins, as today — an exported library is flat).

## Tests

New `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlannerTest.kt`
(MPL header, `kotlinx.coroutines.test.runTest`). Helper: `plan(library: Map<String, String>, vararg incoming)` calls
`planSongs(incoming, library.keys) { library[it] }` and a counter records which names were read. `A`, `B`, `C` are
three different one-line songs.

- `aFreeNameIsNew` — empty library, `x.cho`=A → `NEW x.cho`.
- `theSameSongUnderItsNameIsIdentical` — library `x.cho`=A with CRLF and a trailing blank line; incoming A →
  `IDENTICAL x.cho`.
- `aDifferentSongUnderATakenNameConflicts` — library `x.cho`=A; incoming B → `CONFLICTING x.cho`.
- `twoDifferentSongsOfOneBatchAreBothNew` — empty library; A and B both deriving `x.cho` → `NEW`, `NEW`, both
  `fileName == "x.cho"`, `hasConflicts == false` (scenario A).
- `aNumberedSiblingAnswersForItsCopy` — library `x.cho`=A, `x_2.cho`=B; incoming A (source `x.cho`) and B (source
  `x_2.cho`) → `IDENTICAL x.cho`, `IDENTICAL x_2.cho` (scenario B).
- `aSyncConflictCopyAnswersForItsCopy` — library `x.cho`=A, `x (2).cho`=B; incoming B → `IDENTICAL "x (2).cho"`.
- `aSiblingUnderAnotherExtensionAnswers` — library `x.crd`=A; incoming A deriving `x.cho` → `IDENTICAL x.crd`.
- `aGapInTheNumbersIsNoObstacle` — library `x.cho`=A, `x_3.cho`=B; incoming B → `IDENTICAL x_3.cho`.
- `aRepeatWithinTheBatchPointsAtTheFirst` — empty library; A, B, A → third is `IDENTICAL` with
  `repeatedEntryIndex == 0`.
- `aRepeatOfAConflictingSongPointsAtIt` — library `x.cho`=A; B, B → `CONFLICTING`, then `IDENTICAL` with
  `repeatedEntryIndex == 0`.
- `onlyOneSongPerNameMayReplace` — library `x.cho`=A; B, C → `CONFLICTING`, `NEW`; `summary.conflictingFileNames ==
  ["x.cho"]`.
- `numberedArrivalsArePlannedAfterTheUnnumbered` — empty library; incoming B (source `x_2.cho`) listed before A
  (source `x.cho`) → result order A, B.
- `onlyTheFamilyIsRead` — library of `x.cho`, `x_2.cho`, `y.cho`, `xylophone.cho`; incoming deriving `x.cho` → the
  read names are exactly `x.cho` and `x_2.cho`, each once, also when three incoming songs derive `x.cho`.
- `aLookalikeNameIsNotIdenticalByName` — library `route.cho`=A, `route_66.cho`=B; incoming C deriving `route.cho` →
  `CONFLICTING route.cho` (the lookalike was read, did not match, and changes nothing).
- Setlists (`planSetlists`): `twoSetlistsNormalizingToOneNameAreBothNew` ("Summer set", "Summer Set!" into an empty
  library); `aNumberedSetlistAnswersForItsCopy` (library `summer_set.setlist.json` and `summer_set_2.setlist.json`,
  incoming copy of the second → `IDENTICAL summer_set_2.setlist.json`); `aChangedDescriptionConflictsOnce` (two
  different incoming versions of a library setlist → `CONFLICTING`, `NEW`); `aRepeatedSetlistIsIdentical`.

New `…/useCases/ImportFilesUseCaseImplTest.kt` with two map-backed fakes (`FakeSongRepository` whose `importSong`
numbers with `_2`, `_3` unless `shouldReplace`, and records every overwrite; `FakeSetlistRepository` likewise; every
other member `error("not used")`): `replaceOverwritesALibraryFileOnlyOnce` (a hand-built plan with two `CONFLICTING`
entries for `x.cho` → one overwrite, the other lands as `x_2.cho`); `aSetlistFollowsARepeatToWhereTheFirstWent` (plan
A `NEW x.cho` source `a.cho`, repeat source `b.cho` with `repeatedEntryIndex = 0`, library already holding an
unrelated `x.cho` so A lands as `x_2.cho`; a setlist naming `b.cho` is written naming `x_2.cho`);
`aSetlistFollowsAnIdenticalSibling` (entry `IDENTICAL x_2.cho` source `x_2.cho` → the setlist keeps `x_2.cho`).

`FileNamesTest` (`:data:source:local:implementation`) is unchanged and must still pass; add to it
`aConflictCopyIsRecognizedAsItsOwn`: `"x (2).cho".isNamed("x.cho")`, `!"_2.cho".isNamed(".cho")`.

## Verify

1. `./gradlew :domain:implementation:desktopTest :data:source:local:implementation:desktopTest`, then the compile
   checks.
2. `:app:desktop:run` with an empty library: create "Amazing Grace" by "John Newton" twice with different lyrics
   (the second file is `…_2.cho`), put both into a new setlist, **Export library**.
3. Import that zip straight back: no dialog; the message reports everything as already in the library; nothing new on
   disk.
4. Delete both songs and the setlist, import the zip: no dialog, both songs are back under the same two names, the
   setlist holds both.
5. Edit the un-numbered song, import the zip again: the dialog lists exactly one name. **Replace** restores that one
   song and leaves `…_2.cho` byte for byte; repeat with **Skip them** and **Keep both** (`…_3.cho` appears).
6. Import a `.txt` holding three title-less songs separated by `{new_song}` into an empty library: no dialog,
   `untitled.cho`, `untitled_2.cho`, `untitled_3.cho`.

## Docs

- Root `CLAUDE.md`, "An **import decides before it writes**" bullet: "a name taken by something with exactly the
  same content is disregarded" becomes "a song the library already holds under that name *or a numbered sibling of
  it* (`x_2.cho`) is disregarded … and two different files of one batch that want the same name are never a question:
  the second is numbered like any other collision". Add `:domain:implementation` (`ImportPlanner`) to the "Only pure
  logic is tested" bullet and `:domain:implementation:desktopTest` to its command; the same command in
  `.claude/skills/code-style/SKILL.md` ("Tests").
- `domain/implementation/CLAUDE.md`, the `PrepareImportUseCaseImpl` bullet: replace "names claimed earlier in the
  same batch count as taken too, so an archive holding the same song twice answers for the second copy the way the
  library answers for the first" with a description of `ImportPlanner` (family lookup, repeats by entry index, one
  conflict per name, numbering left to the write) and say it is covered by `commonTest`.
- `data/model/CLAUDE.md`, `LibraryFiles.kt` bullet: mention `withoutCollisionSuffix` as the shared reading of both
  collision shapes. `data/source/local/implementation/CLAUDE.md`, `FileNames.kt` bullet: `isNamed` reads that rule.
- `documentation/features.md:47-50`: same wording change as the root file.

## Touches

- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFiles.kt`
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/ImportPlan.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNames.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNamesTest.kt`
- `domain/implementation/build.gradle.kts`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlanner.kt` (new)
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/PrepareImportUseCaseImpl.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImpl.kt`
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlannerTest.kt` (new)
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImplTest.kt` (new)
- `presentation/src/commonMain/composeResources/values/strings.xml`
- `presentation/src/commonMain/composeResources/values-hu/strings.xml`
- `CLAUDE.md`, `domain/implementation/CLAUDE.md`, `data/model/CLAUDE.md`,
  `data/source/local/implementation/CLAUDE.md`, `.claude/skills/code-style/SKILL.md`, `documentation/features.md`

## Depends on

Nothing. Lands before 15, 27, 28 and 34, which all edit `PrepareImportUseCaseImpl.kt` (15 and 27 in `sort()`, 28 at
the two `decodeLibraryText()` calls, 34 around the body of `invoke`) and are written against this plan's shape of
`planSongs` / `planSetlists`. Shares `LibraryFiles.kt` and `FileNames.kt` with 26, 27 and 29: this plan only adds
`withoutCollisionSuffix` and rewrites `isNamed`, and 26's Unicode names do not change either — a collision suffix is
ASCII whatever alphabet the name is in.
