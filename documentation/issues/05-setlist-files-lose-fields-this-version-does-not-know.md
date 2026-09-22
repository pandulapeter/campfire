# 05 · A setlist file written by a newer Campfire (or edited by hand) loses its extra fields the first time this version changes it

**Severity:** data loss (all platforms. Nothing is lost today, since no version writes more than these six fields.
It becomes likely the day a later version adds a setlist field while this release is still installed on one of a
user's synced devices. The format this release ships is the one that has to tolerate that) · **Area:**
`:data:source:local:implementation` (`SetlistDocument`, `SetlistLocalSourceImpl`, `mapper/SetlistMappers.kt`),
`:data:model` (`Setlist`), `:domain:implementation` (`ImportPlanner`)

## Symptom
1. A later version adds a field to setlists, for example a note per entry (`"songs": [{"file": "a.cho", "note": "capo 2"}]`)
   or a top-level `"venue"`. Or a user adds such a key by hand.
2. On a device still running this release, change that setlist in any way: a transposition tap, a reorder, adding a
   song, archiving, editing the title or description (EditSetlistUseCase), or an import that replaces it.
3. The file is rewritten with only `title`, `description`, `priority`, `isArchived` and `songs[].file` /
   `songs[].transposition`. The extra fields are gone, sync uploads the stripped file, and every device loses them.

## Cause
The document is decoded ignoring unknown keys and re-encoded from the model, which only holds the known ones.
`SetlistLocalSourceImpl.kt:131-136`:

```kotlin
val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    coerceInputValues = true
}
```

decoding at `:50`, `:66` and `:107` (`json.decodeFromString<SetlistDocument>(…)`), and writing at `:83-87`
(`json.encodeToString(setlist.toDocument())`). `SetlistDocument` (`model/SetlistDocument.kt:19-30`) has five fields
and `SetlistSongDocument` two. `Setlist` (`data/model/.../domain/Setlist.kt:16-41`) carries nothing else, so
`SetlistRepositoryImpl.updateSetlist` / `renameSetlist` (`:62-75`), which re-read the file through `latest()`, still
cannot hand the unknown members back. Sync itself is not the problem: it moves raw bytes. It only spreads what the
next local write stripped.

## Fix
Carry the members this version does not know through the model as an opaque JSON text, at both levels (the document
and each entry). The model is used by the import, the rename and the replace paths, not only by in-place saves, so a
read-merge inside `saveSetlist` would not be enough. A REPLACE would even merge the *old* file's extras into the
incoming document.

1. `data/model/.../domain/Setlist.kt`: add a last parameter to `Setlist` and to `Setlist.Entry`, defaulted so that
   every existing construction keeps compiling:

   ```kotlin
       val entries: List<Entry>,
       /**
        * The members of the file this version of the app does not know, as the text of a JSON object, or empty where
        * there are none. A later version may add a field to setlists while this one is still installed on another of
        * the user's devices: kept here, that field survives this version's next save of the setlist instead of being
        * dropped from the file and synced away from every device. Opaque to everything but the storage layer.
        */
       val unknownFields: String = "",
   ) {

       data class Entry(
           val songFileName: String,
           val transposition: Int = 0,
           /** The same as [Setlist.unknownFields], for one entry. */
           val unknownFields: String = "",
       )
   }
   ```

   Every copy of an entry in the app goes through `copy` (reorder `CampfireViewModel.kt:1585`, transposition
   `:1258`, song rename `RenameSongFileUseCaseImpl.kt:55`, import remap `ImportFilesUseCaseImpl.kt:69`), so the
   extras follow the entry. A new entry has none.

2. `model/SetlistDocument.kt`: add a transient member to both documents (import `kotlinx.serialization.Transient`
   and `kotlinx.serialization.json.JsonObject`):

   ```kotlin
       val songs: List<SetlistSongDocument> = emptyList(),
       /** What the file held besides the fields above, filled and written by [SetlistDocumentFormat] rather than the serializer. */
       @Transient val unknownFields: JsonObject = JsonObject(emptyMap()),
   )

   @Serializable
   internal data class SetlistSongDocument(
       val file: String = "",
       val transposition: Int = 0,
       /** The same as [SetlistDocument.unknownFields], for one entry. */
       @Transient val unknownFields: JsonObject = JsonObject(emptyMap()),
   )
   ```

   Update the document KDoc's first sentence to "The on-disk shape of a `*.setlist.json` file, as far as this version
   knows it."

3. New file `model/SetlistDocumentFormat.kt` (MPL header copied from a sibling), next to
   `UserPreferencesDocumentFormat.kt`:

   ```kotlin
   package com.pandulapeter.campfire.data.source.local.implementation.model

   import kotlinx.serialization.json.Json
   import kotlinx.serialization.json.JsonArray
   import kotlinx.serialization.json.JsonElement
   import kotlinx.serialization.json.JsonObject
   import kotlinx.serialization.json.decodeFromJsonElement
   import kotlinx.serialization.json.encodeToJsonElement
   import kotlinx.serialization.json.jsonObject

   /**
    * How a `*.setlist.json` file is read and written. The fields this version knows go through the serializer. Every
    * other member of the document and of each song is kept in `unknownFields` and written back after them, so a
    * field added by a later version, or by hand, outlives this version changing the setlist. The file is shared with
    * those versions through an export or a sync run.
    */
   internal object SetlistDocumentFormat {

       /** Pretty printed because these files are meant to survive an export and be readable (and editable) outside the app. */
       private val json = Json {
           ignoreUnknownKeys = true
           prettyPrint = true
           // A null where a value belongs says nothing, so the field falls back on its default like a missing one does.
           coerceInputValues = true
       }

       private val DOCUMENT_KEYS = SetlistDocument.serializer().descriptor.elementNames.toSet()
       private val SONG_KEYS = SetlistSongDocument.serializer().descriptor.elementNames.toSet()
       private const val SONGS_KEY = "songs"

       /** Throws on a text that is not a setlist document, as the serializer does. */
       fun decode(text: String): SetlistDocument {
           val element = json.parseToJsonElement(text)
           val document = json.decodeFromJsonElement<SetlistDocument>(element)
           // The songs decode one for one from the array, so the index is what pairs each with its own leftovers.
           val songs = (element.jsonObject[SONGS_KEY] as? JsonArray).orEmpty()
           return document.copy(
               unknownFields = element.jsonObject.without(DOCUMENT_KEYS),
               songs = document.songs.mapIndexed { index, song ->
                   song.copy(unknownFields = (songs.getOrNull(index) as? JsonObject)?.without(SONG_KEYS) ?: song.unknownFields)
               },
           )
       }

       fun encode(document: SetlistDocument): String {
           val songs = JsonArray(document.songs.map { song -> JsonObject(json.encodeToJsonElement(song).jsonObject + song.unknownFields) })
           val known = json.encodeToJsonElement(document).jsonObject
           // Known first, in the serializer's order, and "songs" replaced where it stands rather than moved to the end. An
           // empty list is left out, as the serializer leaves out every field at its default.
           val withSongs = if (document.songs.isEmpty()) known else known + (SONGS_KEY to songs)
           return json.encodeToString(JsonElement.serializer(), JsonObject(withSongs + document.unknownFields))
       }

       private fun JsonObject.without(keys: Set<String>) = JsonObject(filterKeys { it !in keys })
   }
   ```

   Notes for the executor:
   - `decodeFromJsonElement` throws `SerializationException` / `IllegalArgumentException` on a document that is not
     an object, exactly where `decodeFromString` did, so the callers' `catch (Exception)` keep their behaviour.
     `element.jsonObject` is only reached after that decode succeeded, so it cannot throw.
   - `encodeDefaults` stays false (the default), as it is today, so a field at its default is still left out of the
     file. That is why `songs` is only put back when the list is not empty: `known` has no `"songs"` then, and adding
     it would write `"songs": []` where today's files have nothing.
   - `DOCUMENT_KEYS` does not contain `unknownFields`: `@Transient` properties are not in the descriptor.
   - Keep the `json` configuration identical to the one being removed from `SetlistLocalSourceImpl`.

4. `mapper/SetlistMappers.kt`: map the leftovers to and from the model's text:

   ```kotlin
   internal fun SetlistDocument.toModel(fileName: String) = Setlist(
       ...,
       entries = songs.filter { it.file.isNotBlank() }.distinctBy { it.file }.map {
           Setlist.Entry(songFileName = it.file, transposition = it.transposition, unknownFields = it.unknownFields.toFieldsText())
       },
       unknownFields = unknownFields.toFieldsText(),
   )

   internal fun Setlist.toDocument() = SetlistDocument(
       ...,
       songs = entries.distinctBy { it.songFileName }.map {
           SetlistSongDocument(file = it.songFileName, transposition = it.transposition, unknownFields = it.unknownFields.toFields())
       },
       unknownFields = unknownFields.toFields(),
   )

   private fun JsonObject.toFieldsText() = if (isEmpty()) "" else toString()

   /** The model's text is only ever one this mapper wrote, but it is a public field, and a save is no place to fail. */
   private fun String.toFields() = if (isEmpty()) JsonObject(emptyMap()) else try {
       Json.parseToJsonElement(this) as? JsonObject ?: JsonObject(emptyMap())
   } catch (_: IllegalArgumentException) {
       JsonObject(emptyMap())
   }
   ```

   (imports `kotlinx.serialization.json.Json`, `kotlinx.serialization.json.JsonObject`; the existing comments
   stay.)

5. `source/SetlistLocalSourceImpl.kt`: replace the three `json.decodeFromString<SetlistDocument>(x)` with
   `SetlistDocumentFormat.decode(x)`, and `json.encodeToString(setlist.toDocument())` with
   `SetlistDocumentFormat.encode(setlist.toDocument())`. Delete the companion's `json` (and the now-empty companion
   object) and the unused `SetlistDocument` / `Json` imports; import `SetlistDocumentFormat`.

6. `ImportPlanner.holdsTheSameAs` (`domain/implementation/.../ImportPlanner.kt:111-112`): add
   `&& unknownFields == other.unknownFields`, so a setlist that differs from the library's copy only by a field this
   version does not know is not disregarded as the same. Entries already compare their own through `Entry.equals`.
   The texts come from the same encoder over maps kept in file order, so two identical files give identical texts.

Every path is covered. In-place saves, renames (`moveFile` writes the renamed model under the new name) and duplicate
(`CampfireViewModel.kt:1605` copies the entries with their extras) all keep them. Imports, NEW and REPLACE alike,
write the incoming document's own extras, since `parseSetlist` decodes through the same format. Export and sync
never re-encode; they copy the file's bytes.

## Tests
- `data/source/local/implementation/src/commonTest/.../model/SetlistDocumentFormatTest.kt` (new, MPL header):

  ```kotlin
  internal class SetlistDocumentFormatTest {

      @Test
      fun fieldsThisVersionDoesNotKnowSurviveARewrite() {
          val text = """{"title":"Summer","venue":{"city":"Pécs"},"songs":[{"file":"a.cho","note":"capo 2"},{"file":"b.cho"}]}"""

          val setlist = SetlistDocumentFormat.decode(text).toModel("summer.setlist.json")
          val changed = setlist.copy(isArchived = true, entries = setlist.entries.reversed())
          val rewritten = Json.parseToJsonElement(SetlistDocumentFormat.encode(changed.toDocument())).jsonObject

          assertEquals(JsonObject(mapOf("city" to JsonPrimitive("Pécs"))), rewritten["venue"])
          val songs = rewritten.getValue("songs").jsonArray.map { it.jsonObject }
          assertEquals(listOf("b.cho", "a.cho"), songs.map { it.getValue("file").jsonPrimitive.content })
          assertEquals(JsonPrimitive("capo 2"), songs[1]["note"])
          assertNull(songs[0]["note"])
      }

      @Test
      fun aDocumentWithNothingUnknownIsWrittenAsBefore() {
          // What the serializer alone wrote, which is what every setlist file in the field looks like.
          val serializer = Json { prettyPrint = true }
          listOf(
              SetlistDocument(title = "Summer", priority = 3, songs = listOf(SetlistSongDocument(file = "a.cho", transposition = 2))),
              SetlistDocument(title = "Empty"),
          ).forEach { document -> assertEquals(serializer.encodeToString(document), SetlistDocumentFormat.encode(document)) }
      }

      @Test
      fun aKnownFieldIsNeverKeptAsUnknown() {
          val document = SetlistDocumentFormat.decode("""{"title":"Summer","isArchived":null,"songs":[{"file":"a.cho","transposition":1}]}""")

          assertTrue(document.unknownFields.isEmpty())
          assertTrue(document.songs.single().unknownFields.isEmpty())
      }
  }
  ```

  Imports: `kotlinx.serialization.json.Json`, `JsonObject`, `JsonPrimitive`, `jsonArray`, `jsonObject`,
  `jsonPrimitive`, `kotlin.test.Test`, `assertEquals`, `assertNull`, `assertTrue`, and the `toModel` / `toDocument`
  mappers.
- `SetlistMappersTest.kt`: the two existing tests keep passing. In the first one, the first mention's extras win,
  like its transposition. Add one assertion to it with an extra on the first `a.cho` document entry
  (`unknownFields = JsonObject(mapOf("note" to JsonPrimitive("x")))`), expecting `unknownFields = """{"note":"x"}"""`
  on the model entry.
- `ImportPlannerTest.kt`: a setlist equal to the library's but with `unknownFields = """{"venue":"x"}"""` is `NEW`
  (or `CONFLICTING` when it has the same file name), not `IDENTICAL`.

Run `./gradlew :data:source:local:implementation:desktopTest :domain:implementation:desktopTest :data:repository:implementation:desktopTest`
(in a worktree; the repository tests construct `Setlist` and must still compile).

## Verify
1. Desktop: in the library folder, add `"venue": "Pécs"` at the top level and `"note": "capo 2"` to one entry of a
   setlist file. Reorder the setlist, transpose one of its songs, archive it, and edit its title so the file is
   renamed. The renamed file still carries both keys.
2. Export that setlist and import it into an empty library (web build): the imported file carries both keys.
3. A setlist created in the app is written exactly as before (compare with a file from the current build).
4. Compile `:app:desktop:run`, `:app:android:assembleDebug`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`,
   `:app:web:wasmJsBrowserDevelopmentRun`.

## Docs
- `documentation/file-format.md`, after "every field is defaulted, so a hand-written file can leave out anything it
  has nothing to say about.": add "Members Campfire does not know are kept: they are written back when the app
  changes the setlist, so a field added by hand or by a later version survives an older one."
- `data/model/CLAUDE.md`, the `Setlist` description in the `domain/` bullet: after "…what the setlists screen's
  search reads besides the title)" add ", and `unknownFields` on the setlist and on each entry — the members of the
  file this version does not know, kept as opaque JSON text so that a later version's field survives this one's
  next save".
- `data/source/local/implementation/CLAUDE.md`: where the setlist files are described (search for
  `setlist.json`), add "`SetlistDocumentFormat` reads and writes them, keeping the members the document does not
  know in `unknownFields` and writing them back after the known ones."

## Touches
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/Setlist.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/model/SetlistDocument.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/model/SetlistDocumentFormat.kt` (new)
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/SetlistMappers.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SetlistLocalSourceImpl.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlanner.kt`
- tests: `SetlistDocumentFormatTest.kt` (new), `SetlistMappersTest.kt`, `ImportPlannerTest.kt`
- `documentation/file-format.md`, `data/model/CLAUDE.md`, `data/source/local/implementation/CLAUDE.md`

## Depends on
None. 20 also edits `ImportPlanner.planSetlists` (a different line), and 19, 21, 28 and 50 edit `documentation/file-format.md`,
so run them one after another.
