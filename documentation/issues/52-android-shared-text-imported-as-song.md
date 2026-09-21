# 52 · Android: Campfire is in the share sheet for every piece of text, and does nothing with it

**Severity:** minor (android; every time text rather than a file is shared to Campfire) · **Area:** `:app:android` — `CampfireActivity.importFrom`, `AndroidFileImport.kt` (created by plan 04), `AndroidManifest.xml` (comment only) · **Decision:** `EXTRA_TEXT` is imported as one song through the ordinary import; `EXTRA_SUBJECT`, when present, stands in as the file name, and so as the fallback title when the text declares no `{title}`

## Symptom
Select a chord sheet on a web page and choose Share, or share a note from a notes app. Campfire is among the targets,
because its share filter is `text/plain`, which is the type of a shared *file* of text and of shared *text* alike.
Choosing it opens Campfire, and nothing happens: no song, no message. Sharing a link from a browser does the same.

## Cause
`app/android/src/main/java/com/pandulapeter/campfire/CampfireActivity.kt:132-139` reads only the stream extras, and a
text share has none:

```kotlin
val uris = when (intent?.action) {
    Intent.ACTION_VIEW -> listOfNotNull(intent.data)
    Intent.ACTION_SEND -> listOfNotNull(intent.parcelableExtra(Intent.EXTRA_STREAM))
    Intent.ACTION_SEND_MULTIPLE -> intent.parcelableArrayListExtra(Intent.EXTRA_STREAM).orEmpty()
    else -> emptyList()
}
if (uris.isEmpty()) return
```

`AndroidManifest.xml:99-105` is the filter that puts the app into that share sheet; it cannot be narrowed to "files
only", since the intent resolver matches on the MIME type and both kinds of share carry the same one.

## Fix
Text shared to the app becomes an `ImportedFile` with a made-up name ending in `.cho`, and goes down the same channel
as a file. Everything after that is the ordinary import: `PrepareImportUseCaseImpl` sorts it as a song by its
extension, names it by its own header (`SongLocalSource.importFileName`, the made-up name only counting where the
text has no `{title}`), disregards it if the library already holds exactly that text, asks the conflicts question if
the name is taken by something else, and reports the result in the usual snackbar.

The rules, each with its reason:

| Case | What happens | Why |
|---|---|---|
| `EXTRA_STREAM` **and** `EXTRA_TEXT` | the stream is imported, the text ignored | the text next to a file is a caption for it — the file's name, a "sent from my phone" — not a second thing to import |
| `SEND` with `EXTRA_TEXT` | one file | the decision |
| `SEND_MULTIPLE` with a list in `EXTRA_TEXT` | one file per text, in one batch; a subject names them `Subject 1`, `Subject 2`… | each is a text of its own, and one batch is one snackbar and one conflicts question |
| text only in `clipData` | read from there | some senders fill in nothing else |
| blank or whitespace text | passed on as it is | `ChordProSplitter.split` finds no song in it, the import counts it as skipped and the snackbar says "… · 1 skipped"; there is no dedicated "nothing to import" string in the app, this is the existing message for it |
| text that is nothing but links | passed on **empty**, so it is reported as skipped like the above | see below |
| a `SEND` with no stream and no text anywhere | one empty file, reported as skipped | the user picked Campfire in a share sheet; saying nothing is the bug being fixed |
| size | no cap of its own | an intent's extras travel in one binder transaction, which cannot be larger than about 1 MB; plan 15's `ImportLimits.MAX_TEXT_FILE_SIZE` (8 MiB) is checked on `bytes.size` by `PrepareImportUseCaseImpl` for every file anyway, this one included |

**A link alone is not imported.** A shared URL is text, and taken literally the decision would make it a one-line
song named after the page. That is never what the user meant: they either expected the app to fetch the page (it
never touches the network except for sync, and will not start here) or picked the wrong target. A song made of it is
a junk file they have to find and delete, and with sync on it is uploaded first. Reported as skipped, the share
costs nothing and the snackbar says that nothing came of it. "Nothing but links" means every non-blank line is a
single `http(s)://…` token; a page title followed by a link is ordinary text and is imported.

**The file name.** `EXTRA_SUBJECT` where there is one (browsers put the page title there, notes apps the note's
title). Where there is none, the first line of the text that is neither blank, a directive, a section marker nor a
comment — which is where a pasted chord sheet has its title — and failing that `untitled`, which is what
`LibraryFiles.normalizedName` makes of an empty title as well. Slashes, backslashes and control characters become
spaces and the result is capped at 80 characters; the library normalizes it again on its way in, so that is all the
cleaning it needs. As with any titleless file, the song is then listed under the normalized form of that name
(`wonderwall_oasis`) until somebody gives it a `{title}` in the editor — do **not** write a `{title}` into the text
here: the decision is that the subject stands in as the file name, and an import never edits what it imports.

1. **`app/android/src/main/java/com/pandulapeter/campfire/AndroidFileImport.kt`** (the file plan 04 creates; it
   holds the process-wide `pendingImports` channel and `importScope`). Add:

   ```kotlin
   /**
    * Text that was shared to Campfire rather than a file - a chord sheet selected on a web page, a note - which is
    * a song that has no file yet. It is given a name and sent down the same way a file is, so everything the import
    * does for a file it does for this: the song is named by its own header, a text the library already holds is
    * disregarded, and a name that is taken is asked about.
    *
    * @param subject What the sender called it, which stands in as the file name and so titles the song where the
    *   text declares no `{title}`.
    */
   internal fun importSharedTexts(texts: List<String>, subject: String?) {
       importScope.launch {
           // The user picked Campfire in a share sheet, so a share with nothing in it is still answered: an empty
           // file is one the import reports as skipped.
           val files = texts.ifEmpty { listOf("") }.mapIndexed { index, text ->
               val name = subject?.cleanedForFileName()?.let { if (texts.size > 1) "$it ${index + 1}" else it } ?: text.firstPlainLine()
               ImportedFile(
                   name = name.orEmpty().ifEmpty { UNTITLED } + LibraryFiles.SONG_EXTENSION,
                   // A link by itself is not a song. Importing it as one would leave a file to find and delete, and
                   // fetching what it points at is not something this app does.
                   bytes = if (text.isOnlyLinks()) ByteArray(0) else text.encodeToByteArray(),
               )
           }
           pendingImports.send(files)
       }
   }

   private fun String.isOnlyLinks() = lineSequence().filter { it.isNotBlank() }.let { lines -> lines.any() && lines.all { LINK.matches(it.trim()) } }

   /** Where a pasted chord sheet has its title: the first line that is neither a directive, a section, nor a comment. */
   private fun String.firstPlainLine() = lineSequence()
       .map { it.trim() }
       .firstOrNull { it.isNotEmpty() && it.first() !in "{[#" }
       ?.cleanedForFileName()

   /** Only what would break a file name; the library normalizes the rest on the way in. Null when nothing is left. */
   private fun String.cleanedForFileName() = map { if (it == '/' || it == '\\' || it.isISOControl()) ' ' else it }
       .joinToString("")
       .trim()
       .take(MAX_SHARED_NAME_LENGTH)
       .trim()
       .ifEmpty { null }

   private val LINK = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
   private const val UNTITLED = "untitled"
   private const val MAX_SHARED_NAME_LENGTH = 80
   ```

   (imports `com.pandulapeter.campfire.data.model.domain.LibraryFiles`; `ImportedFile`, `launch` and the channel are
   already there from plan 04). Blank text is *not* special-cased: it goes through as it is and comes out as
   skipped. If plan 15 has landed, write the empty file as `ImportedFile.unread(name)` — the same thing under the
   name that plan gives it.

2. **`CampfireActivity.kt`, `importFrom`** (in the shape plan 04 leaves it: `importFiles(uris)` from
   `AndroidFileImport.kt` in place of the `lifecycleScope` block):

   ```kotlin
       /**
        * What an "open with" or a share carries, whichever of the three shapes the intent uses. A share is a file or
        * a piece of text, and the file wins where an intent has both: the text next to a stream is a caption for it
        * - the file's name, a "sent from" line - and not a second thing to import.
        */
       private fun importFrom(intent: Intent?) {
           val uris = when (intent?.action) {
               Intent.ACTION_VIEW -> listOfNotNull(intent.data)
               Intent.ACTION_SEND -> listOfNotNull(intent.parcelableExtra(Intent.EXTRA_STREAM))
               Intent.ACTION_SEND_MULTIPLE -> intent.parcelableArrayListExtra(Intent.EXTRA_STREAM).orEmpty()
               else -> emptyList()
           }
           when {
               uris.isNotEmpty() -> importFiles(uris)
               intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE ->
                   importSharedTexts(texts = intent.sharedTexts(), subject = intent.getStringExtra(Intent.EXTRA_SUBJECT))
           }
       }

       /**
        * `EXTRA_TEXT` is one text for a `SEND` and a list of them for a `SEND_MULTIPLE`, asked for in that order
        * because the platform logs a warning for an extra asked for as the wrong type. Some senders fill in only the
        * clip data.
        */
       private fun Intent.sharedTexts(): List<String> {
           val single = { getCharSequenceExtra(Intent.EXTRA_TEXT)?.let { listOf(it) } }
           val several = { getCharSequenceArrayListExtra(Intent.EXTRA_TEXT) }
           val texts = if (action == Intent.ACTION_SEND) single() ?: several() else several() ?: single()
           return (texts ?: clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).text } })
               .orEmpty()
               .map { it.toString() }
       }
   ```

   `getCharSequenceExtra` rather than `getStringExtra`: a selection shared from a rich text field arrives as a
   `Spanned`, which is not a `String`.

3. **Handled once.** Nothing to add: `importFrom` is only reached through `handle(intent)`, which plan 04 guards
   with `savedInstanceState == null` and `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`, and the file is queued from the
   process-wide scope, so a rotation neither imports the text again nor loses it. Without plan 04 this branch would
   make every rotation after a text share import (and then disregard, as identical) the same text again — which is
   why this plan depends on it.

4. **`AndroidManifest.xml`** — the filter stays as it is. Extend the comment above it (it ends "…in the share sheet
   of every photo and video on the device.") with: "That type is also what a piece of *text* is shared under - a
   selection, a note - which arrives as EXTRA_TEXT instead of a stream and is imported as a song, see
   importSharedTexts."

Do **not** add a second filter or a second activity for text, do not open the editor with the text instead of
importing it (the decision is the ordinary import, which is also what keeps the conflicts question and the
"already there" rule), and do not touch `:domain` or `:presentation` — a shared text is an `ImportedFile` by the
time it leaves `:app:android`.

## Tests
None: the change is in `:app:android`, which is a shell and untested, and the domain behaviour it relies on is
already pinned (`ChordProSplitter` turning blank text into no parts is covered in `:chordpro`'s tests; the import
planning is not a tested module).

## Verify
`./gradlew :app:android:assembleDebug`, install the `.debug` build, then with `adb` (each line is one share):
1. `adb shell am start -a android.intent.action.SEND -t text/plain -n com.pandulapeter.campfire.debug/com.pandulapeter.campfire.CampfireActivity --es android.intent.extra.TEXT "'{title: Shared}'" --es android.intent.extra.SUBJECT "'Ignored'"`
   → "Imported 1 songs…", the song is "Shared" (`shared.cho`). Run it again → "… 1 already there". (`am` passes
   `\n` literally, so the multi-line cases are step 5's.)
2. Text `"'[C]Hello [G]world'"` with subject `"'My Song'"` → a song listed as `my_song`.
3. Text `"'Wonderwall by Oasis'"` and no subject → a song listed as `wonderwall_by_oasis`.
4. `--es android.intent.extra.TEXT "'https://example.com/chords'"` → "Imported 0 songs and 0 setlists · 0 already
   there · 1 skipped", nothing new in the library. The same for `"'   '"`.
5. From Chrome: select a paragraph → Share → Campfire: imported. Share the page itself (a link): "1 skipped".
6. From a file manager, share a `.cho` file (stream + text caption): exactly one song, the file's.
7. Rotate after each of the above: no second snackbar (plan 04).
8. With the app closed, repeat 1: cold start, launch screen, then the import snackbar.

## Docs
`app/android/CLAUDE.md`, the manifest paragraph. Replace "The share filter is narrowed to `text/plain` for the same
reason." with: "The share filter is narrowed to `text/plain` for the same reason — which is the type of shared
*text* as much as of a shared text file, so Campfire is offered for a selection or a note as well. Such a share
carries `EXTRA_TEXT` instead of a stream, and `importSharedTexts` (`AndroidFileImport.kt`) turns it into an
`ImportedFile` named after `EXTRA_SUBJECT` — or the text's first plain line — ending in `.cho`, which then goes
through the ordinary import like any file: named by its own header, disregarded when the library already has it,
asked about when the name is taken. A stream wins over a text in the same intent, since that text is a caption; a
text that is nothing but links is passed on empty and reported as skipped, because a link is not a song and the app
does not fetch one." The `CampfireActivity` bullet's "`ACTION_SEND` / `ACTION_SEND_MULTIPLE` (shared to Campfire)"
gains "— files and text alike".

## Touches
- `app/android/src/main/java/com/pandulapeter/campfire/CampfireActivity.kt`
- `app/android/src/main/java/com/pandulapeter/campfire/AndroidFileImport.kt`
- `app/android/src/main/AndroidManifest.xml` (comment only)
- `app/android/CLAUDE.md`

## Depends on
04 (creates `AndroidFileImport.kt` with `pendingImports` / `importScope`, reshapes `importFrom`, and is what makes an
intent handled once). Plans 11 and 15 edit `CampfireActivity.kt` too (`handle` and the `importFrom` read
respectively) and are landed one after the other with this one; none of them touches the new `when`.
