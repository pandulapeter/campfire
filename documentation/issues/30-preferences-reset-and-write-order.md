# 30 · One bad field in `preferences.json` resets every setting and every saved transposition, an unreadable file is replaced by defaults, and two quick changes can be saved in the wrong order

**Severity:** data loss of settings and library transpositions (all platforms; unlikely — it takes a hand edit, a
torn file, a read that fails once, or two saves that overlap on slow storage) · **Area:**
`:data:source:local:implementation` (`UserPreferencesLocalSourceImpl`, `UserPreferencesDocument`,
`SetlistLocalSourceImpl`), `:data:source:local:api` (`UserPreferencesLocalSource`), `:data:repository:implementation`
(`BaseLocalDataRepository.writeData`)

## Symptom

Three ways of losing the same document, which holds every setting and the transposition of every song that was
transposed from the library.

1. **One field of the wrong shape.** Quit the desktop app, open `preferences/preferences.json` and change one value so
   that it no longer fits — `"fontScale": "big"`, `"isLyricsOnlyModeEnabled": "yes"`, or `"2x"` for one entry of
   `"transpositions"`. Start the app: it is in the system's theme and language, the default sort order, and *no* song
   is transposed any more. Change any setting: the file is rewritten from those defaults and the old values are gone
   for good. (A `null` does the same today. An unknown *enum* value does not: see Cause.)
2. **A file that is there but cannot be read this once** — a transient IO error, iOS data protection before the first
   unlock, a permission problem on a desktop data folder. The app starts on defaults exactly as above, reports
   nothing, and the first toggle in Settings writes the defaults plus that toggle over a file that was perfectly good.
3. **Two saves that overlap.** Tap transpose up twice quickly (or pinch the text size while a transposition is being
   saved) on storage where a write takes tens of milliseconds — the web build's OPFS, an old phone's flash with
   `force(true)`. Now and then the app shows +2 until it is restarted and +1 afterwards; in the narrower case it
   jumps back to +1 on screen for a moment while the second write is still running.

## Cause

**(a) and (b): the local source folds everything into defaults.**
`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/UserPreferencesLocalSourceImpl.kt:28-36, 49-52`:

```kotlin
/** A document that cannot be parsed is treated as no document at all, so the app starts on its defaults. */
override suspend fun loadUserPreferences(): UserPreferences = try {
    fileStorage.readText(StorageDirectory.PREFERENCES, FILE_NAME)?.let { json.decodeFromString<UserPreferencesDocument>(it) }
} catch (exception: CancellationException) {
    throw exception
} catch (exception: Exception) {
    println("Could not read the preferences: ${exception.message}")
    null
}.let { it ?: UserPreferencesDocument() }.toModel()
…
val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}
```

Three different things end in `UserPreferencesDocument()`: the file is absent (right: that *is* the defaults), the
file does not decode (a `SerializationException` for the whole document because of one field), and
`FileStorage.readText` threw `LibraryStorageException` because the file exists and could not be read — which all four
actuals do (`JvmFileStorage.kt:57-59` through `failingAsStorage`, `FileStorage.ios.kt:119-127`,
`FileStorage.wasmJs.kt:74-78`). The result is returned as a successful read, so `BaseLocalDataRepository` publishes
`DataState.Idle(defaults)`, the view model's `userPreferences.value` is non-null, and the next
`updateUserPreferences { … }` (`CampfireViewModel.kt:1426-1428`) saves defaults-plus-one-change over the file.

What is already tolerant, and needs no change: every enum is stored as its `id` string and mapped with
`entries.firstOrNull { it.id == … } ?: <default>` (`mapper/UserPreferencesMappers.kt:22-32`), so a value a newer or
older version wrote falls back for that one field; unknown keys are ignored; missing keys are defaulted. The hole is a
value of the wrong JSON *type* (or a `null`), which no default covers.

The same `Json` configuration is in `SetlistLocalSourceImpl.kt:115-118`. There a document that does not decode is
skipped and left on disk, as documented, so a hand-edited `"description": null` makes the setlist vanish from the app
rather than lose anything. It is included here because the cure is the same one line.

**The first-run rule is not part of the problem, and must stay as it is.** `IsFirstRunUseCaseImpl` asks
`hasStoredUserPreferences()`, which is `fileStorage.exists(…)` — about the document, not about what is in it. A corrupt,
empty or unreadable `preferences.json` exists, so it is never a first run and the demo library is never planted into
a library somebody has been using. Verified in `UserPreferencesLocalSourceImpl.kt:44`, `IsFirstRunUseCaseImpl.kt:21`
and `CampfireViewModel.plantDemoLibraryOnFirstRun` (`:1111-1130`).

**(c): `writeData` neither orders the writes nor leaves the state alone afterwards.**
`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/base/BaseLocalDataRepository.kt:107-120`:

```kotlin
protected suspend fun writeData(data: T, persist: suspend (T) -> Unit) = _dataState.run {
    value = DataState.Idle(data)
    value = try {
        persist(data)
        DataState.Idle(data)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println(exception.message)
        …
        DataState.Failure(data)
    }
}
```

Every save is its own `viewModelScope.launch` (`CampfireViewModel.kt:688, 1009, 1427`; also `DeleteSongUseCaseImpl.kt:38`
and `RenameSongFileUseCaseImpl.kt:57`). Two of them both reach `persist`; each platform's atomic write has its own
temporary file and the last rename wins, so the *older* document can be the one left on disk. And the second
assignment is a get-then-set across a suspension: when save A's `persist` returns after save B has published, A puts
`Idle(A)` back, and anything that builds its next save on `userPreferences.value` in that window (every setter in the
view model does) drops B's change for good.

`writeData` has exactly one caller: `UserPreferencesRepositoryImpl.saveUserPreferences` (`:29-31`).
`SongRepositoryImpl` and `SetlistRepositoryImpl` extend the same base class but only ever call `updateData` and
`reloadData` — their files are written one by one under their own locks — so nothing else changes behaviour.

## Fix

Steps 1–4 are (a) and (b) and live in the local source; step 5 is (c) and lives in the repository. The two halves
do not depend on each other.

1. **New file**
   `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/model/UserPreferencesDocumentFormat.kt`
   (MPL header copied from `UserPreferencesDocument.kt`). It takes the `Json` instance out of the local source so that
   the tolerant read is a pure function the tests can reach:

   ```kotlin
   package com.pandulapeter.campfire.data.source.local.implementation.model

   import kotlinx.serialization.json.Json
   import kotlinx.serialization.json.JsonElement
   import kotlinx.serialization.json.JsonObject
   import kotlinx.serialization.json.JsonPrimitive
   import kotlinx.serialization.json.decodeFromJsonElement
   import kotlinx.serialization.json.intOrNull

   /** What [UserPreferencesDocumentFormat.decode] made of a text. */
   internal data class DecodedUserPreferencesDocument(
       val document: UserPreferencesDocument,
       /**
        * False where something in the text had to be left out to get a document at all. What was left out is lost by the
        * next save, which is why the caller keeps a copy of a text that was not intact.
        */
       val isIntact: Boolean,
   )

   /**
    * How `preferences.json` is written and read. Reading is per field: the document holds every setting and every saved
    * transposition, and one value of the wrong shape - a hand edit, a field whose type a later version changed - is no
    * reason to lose the rest of them.
    */
   internal object UserPreferencesDocumentFormat {

       private val json = Json {
           ignoreUnknownKeys = true
           prettyPrint = true
           // A null where a value belongs says nothing, so the field falls back on its default like a missing one does.
           coerceInputValues = true
       }

       fun encode(document: UserPreferencesDocument) = json.encodeToString(document)

       /** Never throws: a text that is not a JSON object at all decodes to the defaults, and is not intact. */
       fun decode(text: String) = try {
           DecodedUserPreferencesDocument(document = json.decodeFromString(text), isIntact = true)
       } catch (_: IllegalArgumentException) {
           DecodedUserPreferencesDocument(document = decodeFieldByField(text), isIntact = false)
       }

       private fun decodeFieldByField(text: String): UserPreferencesDocument {
           val fields = parseObject(text) ?: return UserPreferencesDocument()
           return json.decodeFromJsonElement(JsonObject(fields.mapNotNull { (key, value) -> readableField(key, value) }.toMap()))
       }

       private fun parseObject(text: String) = try {
           json.parseToJsonElement(text) as? JsonObject
       } catch (_: IllegalArgumentException) {
           null
       }

       /**
        * The field if a document holding nothing else decodes, which asks the serializer itself instead of repeating
        * the type of every field here - a field added to the document later is covered without being named. The
        * transpositions are the one field looked into, because they are the one that is a collection of the user's
        * own choices: a single entry that is not a number costs that entry and not the map.
        */
       private fun readableField(key: String, value: JsonElement): Pair<String, JsonElement>? {
           val field = if (key == TRANSPOSITIONS_KEY && value is JsonObject) {
               JsonObject(value.filterValues { it is JsonPrimitive && it.intOrNull != null })
           } else {
               value
           }
           return try {
               json.decodeFromJsonElement<UserPreferencesDocument>(JsonObject(mapOf(key to field)))
               key to field
           } catch (_: IllegalArgumentException) {
               null
           }
       }

       private const val TRANSPOSITIONS_KEY = "transpositions"
   }
   ```

   Notes for the implementer: `SerializationException` is an `IllegalArgumentException`, and `decodeFromString` is
   documented to throw either, hence the wider catch. `encodeToString` / `decodeFromString` are members of `Json` in
   kotlinx.serialization 1.11; `decodeFromJsonElement` is the extension imported above. Do **not** add
   `isLenient = true` (it would accept unquoted strings and hide real damage), and do not add `explicitNulls = false`
   as the report suggests: it only matters for nullable properties, and the document has none. Do **not** change any
   default in `UserPreferencesDocument` or the enum mapping in `UserPreferencesMappers.kt` — the unknown-enum tolerance
   is already there and is pinned by a new test below.

2. **`…/source/UserPreferencesLocalSourceImpl.kt`** — tell the three cases apart. Replace `loadUserPreferences`,
   `saveUserPreferences` and the companion. The import of `kotlinx.serialization.json.Json` goes, one of
   `…implementation.model.UserPreferencesDocumentFormat` comes; `CancellationException` and `UserPreferencesDocument`
   are both still used:

   ```kotlin
   /**
    * Only a document that is *absent* means the defaults. One that is there and cannot be read throws, so that the
    * repository reports a failed read and tries again, rather than handing out defaults the next save would write
    * over a file that may be perfectly good. One that can be read and does not decode as it is gives up only the
    * fields that are wrong, and is copied aside first, since the next save is the end of whatever was in them.
    */
   override suspend fun loadUserPreferences(): UserPreferences {
       val text = fileStorage.readText(StorageDirectory.PREFERENCES, FILE_NAME) ?: return UserPreferencesDocument().toModel()
       val decoded = UserPreferencesDocumentFormat.decode(text)
       if (!decoded.isIntact && text.isNotBlank()) keepUnreadableDocument()
       return decoded.document.toModel()
   }

   override suspend fun saveUserPreferences(userPreferences: UserPreferences) = fileStorage.writeText(
       directory = StorageDirectory.PREFERENCES,
       name = FILE_NAME,
       text = UserPreferencesDocumentFormat.encode(userPreferences.toDocument()),
   )

   /**
    * The bytes rather than the text that was read, so the copy is the file and not a decoding of it. Failing to keep
    * it is no reason to fail the read: the app would then not start on the settings it *could* read.
    */
   private suspend fun keepUnreadableDocument() = try {
       fileStorage.readBytes(StorageDirectory.PREFERENCES, FILE_NAME)?.let {
           fileStorage.writeBytes(StorageDirectory.PREFERENCES, UNREADABLE_FILE_NAME, it)
       }
   } catch (exception: CancellationException) {
       throw exception
   } catch (exception: Exception) {
       println("Could not keep a copy of the preferences: ${exception.message}")
   }

   private companion object {
       const val FILE_NAME = "preferences.json"
       const val UNREADABLE_FILE_NAME = "preferences.json.bad"
   }
   ```

   There is one `.bad` file and a later one replaces it: the copy is made at the moment of the read (not "before the
   first overwrite", as the report words it — there is no later moment at which the source still knows the file was
   damaged), and a document that stays damaged because nothing is saved copies the same bytes again on the next
   launch, which is harmless. An empty or blank file (what a torn write leaves on the web until plan 12) is not
   copied: there is nothing in it to keep. `hasStoredUserPreferences()` stays `exists(…, FILE_NAME)` — untouched, and
   the `.bad` file never answers it.

3. **`data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/UserPreferencesLocalSource.kt`**
   — the contract changes, so the KDoc of `loadUserPreferences` becomes:

   ```kotlin
   /**
    * Never null: a document that has not been written yet means the defaults, which are defined in one place, next to
    * the document itself, so that a new install and a document written by an older version can never disagree about
    * them. A document that is there and does not decode gives up the fields that are wrong and keeps the rest.
    *
    * Throws [LibraryStorageException] for a document that is there and could not be read: that is not an answer, and
    * defaults handed out in its place would be written over the document by the next save.
    */
   suspend fun loadUserPreferences(): UserPreferences
   ```

   Nothing else has to change for (b): `BaseLocalDataRepository.readOnce` already turns the exception into
   `DataState.Failure(null)` with `hasReadFailed = true`, so `loadUserPreferencesIfNeeded()` reads again the next time
   it is asked (the lists' **Retry**, which is `refresh()` -> `LoadScreenDataUseCase`, and every delete or rename).
   The view model is already written for that state and is **not** edited: `arePreferencesLoaded` turns true on a
   failed read so the app opens (in the defaults, in memory only), `userPreferences.value` stays null so every setter
   and the font scale save are no-ops instead of overwriting the file, and `GetScreenDataUseCase` reports `Failure`,
   which the lists show as the error placeholder with Retry.

   The demo library: a first run is an *absent* document, which still returns the defaults without throwing, so
   `plantDemoLibraryOnFirstRun`'s `userPreferences.filterNotNull().first()` still completes and still writes the
   document at the end. An unreadable or damaged document *exists*, so `isFirstRun()` is false and nothing is planted.
   Do not make `hasStoredUserPreferences` look at the content or at the `.bad` file.

4. **`…/source/SetlistLocalSourceImpl.kt:115-118`** — add the same line to its `Json`, with the same comment:

   ```kotlin
   val json = Json {
       ignoreUnknownKeys = true
       prettyPrint = true
       // A null where a value belongs says nothing, so the field falls back on its default like a missing one does.
       coerceInputValues = true
   }
   ```

   Nothing more for setlists: a setlist that still does not decode stays skipped and untouched on disk, which is the
   documented behaviour and loses nothing.

5. **`…/base/BaseLocalDataRepository.kt`** — serialize and order the writes. Add two members next to `mutex`:

   ```kotlin
   /**
    * Held while the storage is written to, so that the writes reach it one at a time. A lock of its own rather than
    * [mutex]: a read is no reason for a change to wait, and a change is published before it takes this one.
    */
   private val writeMutex = Mutex()

   /** What the last write that succeeded put into the storage. Only touched while [writeMutex] is held. */
   private var lastPersistedData: T? = null
   ```

   and replace `writeData` with its final form (the first KDoc paragraph is the existing one, kept):

   ```kotlin
   /**
    * Publishes [data], then persists it as a whole.
    *
    * It is published as [DataState.Idle] right away rather than as a [DataState.Loading] that the write then
    * resolves: what is being written is already what every reader should be showing, and it stays that way even
    * when the write fails. A [DataState.Loading] here would say "nothing has been read yet" to whoever reads this
    * state for that — and a write is a suspending call, so it would say it for as long as the storage takes.
    *
    * It is also published *before* the storage is waited for, because the callers build each change on the state
    * they find: a change that stayed unpublished while an earlier one was still being written would be missing from
    * the next one. The storage is then written to one call at a time, and what a call writes is whatever is
    * published by the time the storage is free rather than what it was called with. That is what keeps the last
    * change the last thing written whichever thread each call came from, and it lets a burst of changes end in one
    * write instead of one each. A write that succeeded publishes nothing: there is nothing to say that the first
    * publish did not, and saying it again would put this call's data back over a change made since.
    */
   protected suspend fun writeData(data: T, persist: suspend (T) -> Unit) {
       _dataState.value = DataState.Idle(data)
       writeMutex.withLock {
           val latestData = _dataState.value.data ?: data
           if (latestData != lastPersistedData) {
               try {
                   persist(latestData)
                   lastPersistedData = latestData
               } catch (exception: CancellationException) {
                   throw exception
               } catch (exception: Exception) {
                   println(exception.message)
                   // The change is kept in memory even though it could not be written: undoing it under the user would
                   // be more surprising than a preference that is lost when the app is restarted. Only if it is still
                   // the change on show, though - a newer one has a write of its own coming, and its own answer.
                   _dataState.update { if (it.data == latestData) DataState.Failure(latestData) else it }
               }
           }
       }
   }
   ```

   Why each detail is there — do not "simplify" these away:
   - **Equality, not identity** (`!=` / `==`, never `===`): `MutableStateFlow` does not replace its value with an
     equal one, so after publishing a second, equal document `_dataState.value.data` is still the *first* instance.
   - **`lastPersistedData` starts null and is set only by a write that succeeded**, so the first-run save — the
     defaults, equal to what was just "read" from an absent file — is still written (that write is what makes the
     next launch not a first run), and a write that failed is retried by the next call.
   - The `Mutex` is fair (FIFO), so with every caller on the main thread "write your own data in turn" would already
     be ordered; writing the *published* data instead makes the order independent of the callers' threads
     (`DeleteSongUseCase` and `RenameSongFileUseCase` also save) at the cost of one read of the state.
   - The signature is unchanged, so `UserPreferencesRepositoryImpl` is **not** edited. `updateData`, `read`,
     `readOnce`, `hasReadFailed` and `mutex` are not touched (plan 32 edits `readOnce`).

## Tests

- `:data:source:local:implementation`, `commonTest`, new class `model/UserPreferencesDocumentFormatTest` (camelCase
  names like the rest of that `commonTest`):
  - `readsWhatItWrote` — `decode(encode(document))` for a document with every field changed from its default (and
    two transpositions) → equal document, `isIntact`.
  - `readsADocumentWithMissingAndUnknownFields` — `{"fontScale": 1.5, "somethingNew": [1, 2]}` → `fontScale == 1.5f`,
    the rest defaults, `isIntact`.
  - `treatsANullAsAMissingField` — `{"fontScale": null, "transpositions": null, "uiMode": "dark"}` → defaults for the
    two, `uiMode == "dark"`, `isIntact` (that is `coerceInputValues`; a null carries nothing, so nothing is lost).
  - `givesUpOnlyTheFieldOfTheWrongShape` — `{"fontScale": "big", "isLyricsOnlyModeEnabled": true, "language": "hu",
    "transpositions": {"a.cho": 2}}` → `fontScale == 1f`, the other three as written, `!isIntact`.
  - `givesUpOnlyTheTranspositionThatIsNotANumber` — `{"transpositions": {"a.cho": 2, "b.cho": "2x", "c.cho": -3,
    "d.cho": 1.5, "e.cho": [1]}}` → `mapOf("a.cho" to 2, "c.cho" to -3)`, `!isIntact`. This also pins
    `TRANSPOSITIONS_KEY` against a rename of the property.
  - `givesUpTranspositionsThatAreNotAnObject` — `{"transpositions": [1, 2], "themeColor": "forest"}` → empty map,
    `themeColor == "forest"`, `!isIntact`.
  - `readsTextThatIsNotAnObjectAsTheDefaults` — `""`, `"   "`, `"{\"fontScale\": 1."` (torn), `"[1, 2]"`, `"hello"` →
    `UserPreferencesDocument()`, `!isIntact`, no exception.
- Same module, `commonTest`, new class `mapper/UserPreferencesMappersTest`:
  - `anUnknownEnumIdFallsBackForThatFieldOnly` — `UserPreferencesDocument(sortingMode = "by_mood", uiMode =
    <UiMode.DARK.id>, tagMatchMode = "sometimes").toModel()` → `BY_ARTIST`, `DARK`, `ANY`. (Pins the tolerance that is
    already there; use the real ids from `UserPreferences`.)
- Same module, `desktopTest`, new class `source/UserPreferencesLocalSourceTest` (the `RenameTest` pattern: a temp
  directory, `JvmFileStorage(root)`, `runBlocking`, backticked names, `root.deleteRecursively()` in `@AfterTest`):
  - `` `a missing document is the defaults and keeps no copy` `` — nothing on disk → `UserPreferencesDocument().toModel()`,
    and neither `preferences.json` nor `preferences.json.bad` exists afterwards; `hasStoredUserPreferences()` is false.
  - `` `a damaged document keeps what it can and is copied aside` `` — write `{"fontScale": "big", "transpositions":
    {"a.cho": 2}}` → the model has `transpositions == mapOf("a.cho" to 2)`; `readBytes(PREFERENCES,
    "preferences.json.bad")` equals the bytes written; `preferences.json` itself is unchanged;
    `hasStoredUserPreferences()` is true.
  - `` `a document that is intact is not copied` `` and `` `an empty document is not copied` `` (write `""`).
  - `` `a document that cannot be read is not the defaults` `` — make `root/preferences/preferences.json` unreadable the
    way `JvmFileStorageTest` provokes a `LibraryStorageException` (if it has no such case: create the file, then
    `setReadable(false)`, and skip the assertion when `canRead()` is still true, which is root on CI and Windows) →
    `assertFailsWith<LibraryStorageException>`; no `.bad` file.
- `:data:repository:implementation`, `commonTest`, `base/BaseLocalDataRepositoryTest` (backticked names, as there). Add
  to `TestRepository`: `suspend fun write(data: List<String>, persist: suspend (List<String>) -> Unit) = writeData(data,
  persist)`. Each case records what reached the storage in a `persisted` list and holds the first write open with a
  `CompletableDeferred<Unit>` awaited inside its `persist`; the writes are `launch`ed and `runCurrent()` is called
  between the steps.
  - `` `writes reach the storage one at a time and in order` `` — write `a` (held), write `b`; assert `persisted ==
    [a]` and that `b`'s `persist` has not started; release → `persisted == [a, b]`, last state `Idle(b)`.
  - `` `a write that finishes late does not put its data back` `` — same steps; the recorded states after the initial
    `Loading(null)` are exactly `[Idle(a), Idle(b)]` (today a third `Idle(a)` and a fourth `Idle(b)` appear).
  - `` `changes made while the storage is busy end in one write` `` — write `a` (held), `b`, `c`; release → `persisted ==
    [a, c]`, last state `Idle(c)`.
  - `` `a change is published before the storage is free` `` — write `a` (held), write `b`; before releasing, the last
    state is already `Idle(b)`.
  - `` `a write that fails keeps the change and reports it` `` — `persist` throws `IllegalStateException` → last state
    `Failure(a)`; a following `write(a) { persisted += it }` does write (`persisted == [a]`) and ends `Idle(a)`.
  - `` `a write that fails says nothing about a newer change` `` — write `a` (held, then throws), write `b`; release →
    no `Failure` among the states, `persisted == [b]`, last state `Idle(b)`.
  - `` `the same data is written once it has not been written before` `` — load (`Idle([a, b])` from the source), then
    `write([a, b])` → `persisted == [[a, b]]`: the first-run save. A second identical `write` adds nothing.

## Verify

1. The unit test command from the README, then the three compile checks (everything edited is common code).
2. `./gradlew :app:desktop:run` once, transpose two songs from the library, pick a theme and Hungarian, quit. In the
   desktop data folder edit `preferences/preferences.json`: set `"fontScale": "big"` and one transposition to `"2x"`.
   Start the app: Hungarian, the theme, and the *other* transposition are all still there; the text size is the
   default. `preferences/preferences.json.bad` holds the edited text. Change a setting and check that
   `preferences.json` is valid again and still carries the good transposition.
3. Replace the file's content with `{"fontScale": 1.` (torn). Start: defaults, the library is intact and **no demo
   songs are planted** (delete every song first to make that visible: the library must stay empty). `.bad` holds the
   torn text.
4. macOS/Linux: `chmod 000 preferences/preferences.json`, start the app: it opens in defaults, both lists show the
   error state with Retry, and changing the theme in Settings does nothing. `chmod 644` it, press Retry: the saved
   theme, language and transpositions are back, and the file was never rewritten (compare its modification time).
5. Delete the whole data folder and start: the two demo songs and the setlist are planted and `preferences.json` is
   written — the first-run rule as the root `CLAUDE.md` describes it. Delete the three, restart: they stay deleted.
6. Web (`:app:web:wasmJsBrowserDevelopmentRun`): open a song from the library and press transpose up five times as
   fast as possible, reload the page: +5. Pinch/step the text size right after a transposition and reload: both kept.

## Docs

- `data/source/local/api/CLAUDE.md`, the `UserPreferencesLocalSource` bullet: "never returns null, because a missing
  or unreadable document means the defaults" becomes "never returns null, because a missing document means the
  defaults, which are defined once next to the document itself; a document that is there and cannot be read throws
  `LibraryStorageException` instead, since defaults handed out in its place would be saved over it, and one that does
  not decode gives up only the fields that are wrong".
- `data/source/local/implementation/CLAUDE.md`, the **`model/` + `mapper/`** bullet: after "keeps whatever it does
  carry instead of failing to parse" add: "A `null` is read as a missing field (`coerceInputValues`), in both
  documents. The preferences go further, since they are the one document the app overwrites as a whole:
  `UserPreferencesDocumentFormat` reads them field by field when they do not decode as they are — one transposition
  that is not a number costs that entry, not the map — and the local source copies such a file to
  `preferences.json.bad` before anything can be saved over it. A setlist that does not decode is skipped and left
  alone, as before."
- `data/repository/implementation/CLAUDE.md`, the `writeData` paragraph: after the sentence ending "for as long as the
  storage takes to answer." add: "The publish also comes before the storage is waited for, since every caller builds
  its change on the state it finds; the writes then go through a lock of their own one at a time, each writing what
  is published by then rather than what it was called with, so the last change is the last thing on disk and a burst
  of changes ends in one write. A write that succeeded publishes nothing more, and one that failed turns the state
  into a `Failure` only while its data is still the data on show. `commonTest` covers this too."
- Root `CLAUDE.md`, the library layout block: add a line under `preferences/preferences.json`:
  `preferences/preferences.json.bad     the last preferences document that did not decode, kept before it is saved over`.
- Plan 25 (Android backup) names `preferences/preferences.json` exactly, so the `.bad` file is in no backup; that is
  intended and needs no edit there.

## Touches

- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/model/UserPreferencesDocumentFormat.kt` (new)
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/UserPreferencesLocalSourceImpl.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SetlistLocalSourceImpl.kt`
- `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/UserPreferencesLocalSource.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/base/BaseLocalDataRepository.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/model/UserPreferencesDocumentFormatTest.kt` (new)
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/UserPreferencesMappersTest.kt` (new)
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/UserPreferencesLocalSourceTest.kt` (new)
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/base/BaseLocalDataRepositoryTest.kt`
- `data/source/local/api/CLAUDE.md`
- `data/source/local/implementation/CLAUDE.md`
- `data/repository/implementation/CLAUDE.md`
- `CLAUDE.md`

## Depends on

Nothing. Shares `BaseLocalDataRepository.kt` and `BaseLocalDataRepositoryTest.kt` with plan 32 (which edits `readOnce`
and adds its own cases; no overlap, either order, one after the other) and `SetlistLocalSourceImpl.kt` with plan 27
(line 38, the listing filter; this plan only touches the `Json` block at the bottom). Plan 12 is what stops the web
build leaving an empty `preferences.json` behind a failed write; this plan only makes sure such a file is read as the
defaults without being copied.
