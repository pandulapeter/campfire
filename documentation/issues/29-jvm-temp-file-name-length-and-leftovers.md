# 29 · A song with a long name can never be saved on desktop and Android, crashed writes leave files behind, and Windows cannot store a song called "Con"

**Severity:** wrong behaviour (desktop, Android; rare today — a name over ~230 bytes, or a process killed mid-write, or
Windows with a song or setlist titled `Con`, `Aux`, `Nul`, `Prn`, `Com1`… — but the first of these becomes reachable
for ordinary titles once plan 26 keeps multi-byte letters in file names, and at 143 bytes on an eCryptfs home
directory) · **Area:** `:data:source:local:implementation` (`JvmFileStorage`, both copies)

## Symptom

1. **Long names.** A song whose file name is between roughly 232 and 251 bytes (`artist-title.cho` with both halves
   near the cap, or any hand-named file that long in the desktop library folder) opens fine, but every save — a tag,
   a language, the editor — fails with "Could not save the song". Creating or importing one fails the same way.
2. **Leftovers.** If the app is killed (or the machine loses power) between creating the temporary file and moving it
   into place, `library/songs` keeps a `my_song.cho.8372615243.tmp` for ever. The app never lists it and sync never
   uploads it, which is right, but nothing ever removes it either, and on desktop the folder is the user's to look at.
3. **Windows device names.** On Windows, a song without an artist titled exactly "Con" (or a setlist titled "Aux")
   normalizes to `con.cho` / `aux.setlist.json`. Win32 resolves those names to the console and the auxiliary device
   whatever the extension, so the file cannot be created: "Could not save", every time. The same name arriving through
   sync from a Mac or a phone, where it is perfectly legal, fails that machine's sync run on every attempt. (Not
   reproduced on a Windows machine during the review; the Win32 rule itself is documented behaviour.)

## Cause

`data/source/local/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt:80-98`
(and the identical `androidMain` copy):

```kotlin
private fun writeAtomically(directory: StorageDirectory, name: String, write: (File) -> Unit) {
    val target = file(directory, name).toPath()
    // A name of its own per write, so two writes of one file cannot share a temporary file.
    val temporary = Files.createTempFile(target.parent, "$name.", TEMPORARY_FILE_SUFFIX)
```

`createTempFile` inserts an unsigned 64-bit number between prefix and suffix, so the temporary file is called
`<name>.<up to 20 digits>.tmp`: up to 25 bytes longer than the target. ext4, F2FS, APFS and NTFS cap a name at 255
(bytes, or UTF-16 units), so a target of 231+ bytes that is itself legal gets a temporary name that is not, and
`createTempFile` throws `IOException: File name too long`, which `failingAsStorage` turns into
`LibraryStorageException`. `LibraryFiles.MAX_NAME_LENGTH` is 120 *per half*, so `artist-title_2.cho` can be 248.

The leftovers: the `finally { Files.deleteIfExists(temporary) }` on line 95-97 is the only thing that removes a
temporary file, and a killed process does not run it. `list` (line 44) only hides them:

```kotlin
files.filter { it.isFile && !it.name.endsWith(TEMPORARY_FILE_SUFFIX) }
```

The device names: nothing between `LibraryFiles.normalizedName` and `File(directoryFile(directory), name)` (line 109)
knows that Windows reserves `CON`, `PRN`, `AUX`, `NUL`, `COM0`-`COM9` and `LPT0`-`LPT9` (and the superscript digits
`¹²³` after `COM`/`LPT`), with or without an extension, in any case.

## Fix

Everything is in `JvmFileStorage`. **Both copies change identically** — `desktopMain/…/storage/file/JvmFileStorage.kt`
and `androidMain/…/storage/file/JvmFileStorage.kt` differ only in the word "Android"/"desktop" in the class KDoc's
last sentence, and must keep differing only there (`diff` the two when done).

### Where a device name is made safe — the decision

Two places could do it: `LibraryFiles.normalizedName` (prefix or suffix such a base, as the review suggested) or the
JVM storage on Windows. **The storage, on Windows only**, because:

- `normalizedName` is the identity rule of all four platforms. Changing what it answers for "Con" renames nothing by
  itself, but it turns **Update file name** on for an existing `con.cho` on macOS, Android, iOS and the web, where
  the name was never a problem, and it contradicts the guarantee plan 26 is written around (Latin text normalizes
  exactly as today, no existing name changes).
- It would only cover names the app derives. A `con.cho` named by hand on a Mac, or written by a version before the
  change, still arrives on the Windows machine through sync under that name and fails the run for ever. The storage
  sees every route a name can take.
- The storage is where the other file-system facts already live (the temporary names, the case-insensitivity note in
  `FileNames.kt` is about the same kind of thing), and the mapping is a dozen lines that do nothing anywhere but
  Windows.

The mapping has to be reversible, since `list` must hand back the name the rest of the app knows the file by, and
injective, since two library names must never share one file. Rule: a name whose base (the part before the first dot)
is a device name *once its leading underscores are taken off* is stored with one more underscore in front.
`con.cho` is stored as `_con.cho`, a (hand-named) `_con.cho` as `__con.cho`; going back, a stored name of that shape
loses one underscore. A stored `con.cho` cannot exist on Windows, so nothing maps onto it.

### Steps

1. Constructor and state:

   ```kotlin
   internal class JvmFileStorage(
       private val root: File,
       private val escapesDeviceNames: Boolean = System.getProperty("os.name").orEmpty().startsWith("windows", ignoreCase = true),
   ) : FileStorage {

       /** The directories already looked through for the leftovers of an earlier run, see [removeLeftovers]. */
       private val sweptDirectories = ConcurrentHashMap.newKeySet<StorageDirectory>()
   ```

   `startsWith("windows")`, not `contains("win")`: "Darwin" contains it. Android reports "Linux", so the flag is off
   there without the Android copy having to differ. `DesktopFileStorage` and `AndroidFileStorage` keep calling
   `JvmFileStorage(root)`; the parameter exists so that a test can turn the rule on on the machine it runs on. Add
   `import java.util.concurrent.ConcurrentHashMap` (API 24, below the `minSdk` of 28).

   Extend the class KDoc's second paragraph: "Writes go to a temporary file first and are moved into place
   afterwards, so that a crash in the middle of one leaves the previous content intact instead of a half written
   file. What such a crash does leave is the temporary file, which the next run removes."

2. `list` reports the library's name for a file, not the stored one:

   ```kotlin
   files.filter { it.isFile && !it.name.endsWith(TEMPORARY_FILE_SUFFIX) }
       .map { StoredFileInfo(name = it.name.toLibraryName(), size = it.length(), lastModified = it.lastModified()) }
       .sortedBy { it.name }
   ```

   and `info` answers with the name it was asked about (`name = name` instead of `name = it.name`), for the same
   reason. The `.tmp` filter stays: the leftovers of the last hour are still there, and on Windows a leading dot hides
   nothing.

3. `writeAtomically` — a name that does not grow with the target:

   ```kotlin
   // A name of its own per write, so two writes of one file cannot share a temporary file - and one that says
   // nothing about the target, whose name may already be as long as the file system allows.
   val temporary = Files.createTempFile(target.parent, TEMPORARY_FILE_PREFIX, TEMPORARY_FILE_SUFFIX)
   ```

   That is `.campfire-<up to 20 digits>.tmp`, at most 34 bytes, in the same directory as before — it has to stay
   there, since an atomic move only exists within one file system. Nothing else in the function changes.

4. `file` and `directoryFile`:

   ```kotlin
   private fun file(directory: StorageDirectory, name: String): File {
       requireValidFileName(name)
       return File(directoryFile(directory), name.toStoredName())
   }

   private fun directoryFile(directory: StorageDirectory) = directory.pathSegments
       .fold(root) { parent, segment -> File(parent, segment) }
       .also {
           it.mkdirs()
           if (sweptDirectories.add(directory)) removeLeftovers(it)
       }
   ```

   Every caller of `directoryFile` is already inside `withContext(Dispatchers.IO)`, so the sweep needs no dispatcher
   of its own. It runs on the first touch of each of the three directories — the preferences directory included,
   which is never listed but is written to more often than any other — and costs one more listing per directory per
   run.

5. New private members, after `failingAsStorage`:

   ```kotlin
   /**
    * A write the process did not survive leaves its temporary file behind, since the `finally` that removes it never
    * ran. They are looked for once per directory and run, and only those old enough that no write can still be using
    * them are removed: another write of this process may be under way by the time the first one gets here, and so may
    * one of a second copy of the app. Only names this class gives out are touched - the current shape and the
    * `<target>.<number>.tmp` of the versions before it - because the library folder is the user's, and a `.tmp` of
    * their own is none of the app's business. Best effort: a leftover that will not be deleted is asked again next run.
    */
   private fun removeLeftovers(directoryFile: File) {
       val newestLeftover = System.currentTimeMillis() - LEFTOVER_AGE_MILLIS
       directoryFile.listFiles { _, name -> LEFTOVER_NAME.matches(name) }
           // A modification time of 0 is a file system that could not say, which is not the same as old.
           ?.filter { it.isFile && it.lastModified() in 1..newestLeftover }
           ?.forEach { it.delete() }
   }

   /**
    * Windows resolves a handful of names to devices rather than files - the console, the printer, the serial ports -
    * whatever their extension and case, so "con.cho" cannot be created there, however legal it is on the phone that
    * synced it over. Such a name is stored with an underscore in front, and so is one that would otherwise be taken
    * for an escaped name, which keeps the mapping reversible: [toLibraryName] takes off exactly what this put on.
    */
   private fun String.toStoredName() = if (escapesDeviceNames && trimStart(DEVICE_NAME_ESCAPE).isDeviceName()) DEVICE_NAME_ESCAPE + this else this

   private fun String.toLibraryName() =
       if (escapesDeviceNames && startsWith(DEVICE_NAME_ESCAPE) && trimStart(DEVICE_NAME_ESCAPE).isDeviceName()) drop(1) else this

   /** Windows looks at what precedes the first dot and ignores the spaces at the end of it. */
   private fun String.isDeviceName() = DEVICE_NAME.matches(substringBefore('.').trimEnd(' '))
   ```

   and the companion becomes:

   ```kotlin
   private companion object {

       const val TEMPORARY_FILE_PREFIX = ".campfire-"
       const val TEMPORARY_FILE_SUFFIX = ".tmp"

       /** Both shapes a temporary file of this class has had: `.campfire-<number>.tmp` and `<target>.<number>.tmp`. */
       val LEFTOVER_NAME = Regex("""\.campfire-\d+\.tmp|.+\.\d+\.tmp""")

       /** Far longer than any write takes, and short enough that a leftover is gone by the next day's first run. */
       const val LEFTOVER_AGE_MILLIS = 60L * 60L * 1000L

       const val DEVICE_NAME_ESCAPE = '_'
       val DEVICE_NAME = Regex("""con|prn|aux|nul|(com|lpt)[0-9¹²³]""", RegexOption.IGNORE_CASE)
   }
   ```

Do **not**:
- change `LibraryFiles.normalizedName` or `FileNames.kt` (see the decision above; plan 26 owns that function);
- move the temporary file to `java.io.tmpdir` or the cache directory — another file system, so no atomic move;
- sweep in `list` on every call, or delete every `*.tmp`: the first costs a pass per listing for something that
  happens once in a blue moon, the second deletes files the app did not write;
- handle the other names Windows refuses (`<>:"|?*`, a trailing dot or space). No name the app derives contains them;
  a hand-named file from another device that does fails as that one file's sync failure (plan 16), which is the
  honest answer.

## Tests

`:data:source:local:implementation`, `desktopTest`, `storage/file/JvmFileStorageTest`. New cases:

- `` `saves a file whose name is nearly as long as the file system allows` `` — `val name = "a".repeat(240) + ".cho"`;
  `writeText`, then `readText` returns the text, and `root.resolve("library/songs").list()` is `[name]`. (Fails today
  with `LibraryStorageException`.)
- `` `removes the temporary files an earlier run left behind` `` — before the storage is touched, create
  `root/library/songs` by hand and in it: `.campfire-123.tmp` and `a.cho.456.tmp` with
  `setLastModified(System.currentTimeMillis() - 2 * 60 * 60 * 1000)`, a fresh `.campfire-789.tmp`, and an equally old
  `notes.tmp` and `a.cho`. After `JvmFileStorage(root).list(StorageDirectory.SONGS)`: the listing is `["a.cho"]`; on
  disk the two old leftovers are gone and `.campfire-789.tmp`, `notes.tmp` and `a.cho` are still there.
- `` `looks for leftovers once per run` `` — `list` once, then create an old `.campfire-5.tmp` the same way, `list` and
  `writeText` again: the file is still on disk.
- `` `removes the leftovers of the preferences directory on its first write` `` — old
  `root/preferences/preferences.json.77.tmp`; `writeText(StorageDirectory.PREFERENCES, "preferences.json", "{}")`;
  it is gone.
- `` `stores a Windows device name under an escaped one and reports the name it was given` `` — with
  `JvmFileStorage(root, escapesDeviceNames = true)`: `writeText(SONGS, "con.cho", "{title: Con}")` → on disk
  `_con.cho` and no `con.cho`; `list` → `["con.cho"]`; `info(SONGS, "con.cho")?.name` → `"con.cho"`; `exists` true;
  `readText` returns the text; `delete` leaves the directory empty. Repeat the on-disk assertion for
  `"AUX.setlist.json"` in `SETLISTS` (`_AUX.setlist.json`) and `"Com1.cho"`.
- `` `keeps an escaped looking name apart from the name it escapes` `` — same storage: write `con.cho` and `_con.cho`
  with different texts → on disk `_con.cho` and `__con.cho`; `list` → `["_con.cho", "con.cho"]`; each reads back its
  own text.
- `` `leaves names that only start like a device alone` `` — `console.cho`, `_x.cho`, `com10.cho`, `nul_2.cho` are
  stored under exactly those names, and listed as them.
- `` `does not escape device names where they are ordinary files` `` — `JvmFileStorage(root, escapesDeviceNames = false)`
  writes `con.cho` as `con.cho`. (Do not rely on the default here: the test also has to pass on a Windows machine.)
- The existing `` `lets concurrent writes of one file all finish, keeping one of them whole` `` stays as it is; its last
  assertion is what pins "a finished write leaves no temporary file" for the new name too.

## Verify

1. `./gradlew :data:source:local:implementation:desktopTest`, then
   `diff data/source/local/implementation/src/{desktopMain,androidMain}/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`
   — exactly one differing line, in the KDoc.
2. `./gradlew :app:android:assembleDebug` (the Android copy is only compiled there).
3. Desktop: in the library's `library/songs`, `touch -t 202601010000 .campfire-1.tmp old.cho.2.tmp`; run
   `./gradlew :app:desktop:run`; both are gone once the song list has loaded. Create a song, quit: no `.tmp` in the
   folder.
4. Desktop: copy a song to a name of 240 `a`s plus `.cho`, restart, add a tag to it: it saves.
5. On a Windows machine, if one is at hand: create a song titled "Con" with no artist; it saves, the folder shows
   `_con.cho`, the song list shows "Con", and **Update file name** is not offered for it.

## Docs

- `data/source/local/implementation/CLAUDE.md`, the "Writes are atomic…" bullet: "(a temporary file of its own per
  write, flushed to the device and moved over the target on the JVM, `atomically` on iOS)" becomes "(a temporary file
  of its own per write — `.campfire-<number>.tmp`, named without the target so that a name at the file system's limit
  still saves —, flushed to the device and moved over the target on the JVM, `atomically` on iOS)", and the bullet
  gains: "A write the process did not survive leaves that file behind; the JVM storage removes the ones older than an
  hour the first time it touches each directory in a run, and only names of its own making."
- Same file, a new sub-bullet after it: "On Windows the JVM storage stores a name Win32 would resolve to a device
  (`con.cho`, `aux.setlist.json`, `com1.cho`…) with an underscore in front and reports it without, so the name rule
  stays the same on every platform and a file synced from a phone under such a name can be written. Nothing above the
  storage knows."
- Root `CLAUDE.md`: nothing.

## Touches

- `data/source/local/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`
- `data/source/local/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorageTest.kt`
- `data/source/local/implementation/CLAUDE.md`

## Depends on

nothing in code. Schedule after 26 (it is what makes long names common, and its byte cap is what the first new test's
240 bytes sits under) and after 15 if that plan bounds reads inside `JvmFileStorage`, since both copies of the file
are rewritten here.
