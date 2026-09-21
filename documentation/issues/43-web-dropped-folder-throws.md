# 43 · Web: dropping a folder (or a file the browser cannot read) on the page kills drag and drop, and can freeze the app

**Severity:** crash (web; likely — dragging the folder of songs is the obvious first thing to try) · **Area:** `:presentation` `wasmJsMain` (`FilePicker.wasmJs.kt`: `droppedFiles`, `toImportedFiles`, `listenForDrops`, `fileBytes`), with a small parity step in `desktopMain` (`readAsImportedFiles`)

## Symptom
1. Open the web build. Drag a *folder* of `.cho` files from Finder / Explorer onto the page (or a mix of files and a
   folder, or a file that is deleted or on an unmounted volume by the time it is read).
2. Nothing is imported and no message is shown.
3. From then on every drop is swallowed for the rest of the session: the page still prevents the browser's default
   (the listeners stay installed), but nobody is reading the drops any more. Because the exception leaves a
   `LaunchedEffect`, it also fails the Recomposer's effect job, so the page may stop recomposing altogether until it
   is reloaded.

The desktop build does not throw on a dropped folder, but ignores it just as silently (`file.isFile`), although its own
comment calls a drop "the shortest path there is from a folder of songs to a library".

## Cause
`presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.wasmJs.kt`.

The drop listener takes `event.dataTransfer.files` as it is (`:138`). For a dropped directory that list holds a `File`
whose `arrayBuffer()` rejects (`NotFoundError` in Chromium, a `NotReadableError` elsewhere). Nothing on the way catches
it:

```kotlin
// :89
private fun fileBytes(file: JsAny): Promise<Int8Array?> =
    js("file.arrayBuffer().then(function (buffer) { return new Int8Array(buffer); })")

// :112
internal fun droppedFiles(): Flow<List<ImportedFile>> = flow {
    listenForDrops()
    while (true) {
        emit(nextDrop().await<JsArray<JsAny>?>()?.toImportedFiles().orEmpty())
    }
}

// :119
private suspend fun JsArray<JsAny>.toImportedFiles() = (0 until length).mapNotNull { index ->
    get(index)?.let { file ->
        ImportedFile(
            name = fileName(file).toString(),
            bytes = fileBytes(file).await<Int8Array?>()?.toByteArray() ?: ByteArray(0),
        )
    }
}
```

The rejection comes out of `await`, fails the `flow { }`, and leaves
`LaunchedEffect(filesToImport) { filesToImport.collect(viewModel::importFiles) }`
(`presentation/src/commonMain/.../ui/CampfireApp.kt:143`) uncaught. One unreadable item also loses every readable
file that was dropped with it.

Two details the fix depends on, both checked against the sources in the Gradle cache:

- `kotlin.js.JsException` extends **`Throwable`, not `Exception`** (kotlin-stdlib-wasm-js 2.4.20,
  `wasmJsMain/kotlin/js/JsException.kt:20`). Anything a `js(...)` block throws *synchronously* arrives as one, so a
  `catch (exception: Exception)` does not catch it — including the one in `CampfireViewModel.importFiles(filePicker)`
  that the picker path relies on.
- A *rejected promise* is different: `Promise<T>.await()` of kotlinx-coroutines 1.11.0 (`webMain/Promise.kt:79`,
  `wasmJsMain/Promise.wasm.kt` `toThrowable`) turns a rejection that is not a Kotlin throwable into a plain
  `Exception("Non-Kotlin exception …")`. So today the picker path does survive a rejecting `arrayBuffer()` (it shows
  "Import failed" and loses the whole selection), while the drop path has nothing at all. Which of the two types
  arrives has changed between coroutines versions, so the code below catches `Throwable` and says why.

## Fix
All of steps 1–4 are in `FilePicker.wasmJs.kt`. Do not touch `CampfireApp.kt`: once the web flow cannot fail, none of
the four `filesToImport` flows can (Android and iOS are `Channel.receiveAsFlow()`, desktop a `MutableStateFlow`), and
`viewModel::importFiles` only does a `trySend`.

1. **`fileBytes` never rejects.** Replace the function (and give it the KDoc it lacks):

   ```kotlin
   /**
    * Resolves with null instead of rejecting for a file the browser cannot read: a dropped directory where the
    * browser hands one over as a `File`, or a file that was moved or deleted after it was chosen.
    */
   private fun fileBytes(file: JsAny): Promise<Int8Array?> = js(
       """file.arrayBuffer().then(
           function (buffer) { return new Int8Array(buffer); },
           function () { return null; }
       )"""
   )
   ```

2. **`toImportedFiles` protects each file on its own.** Replace it with:

   ```kotlin
   private suspend fun JsArray<JsAny>.toImportedFiles() = (0 until length).mapNotNull { index -> get(index)?.toImportedFile() }

   /**
    * One unreadable file must not lose the ones that came with it, and a drop has no caller to catch for it: whatever
    * is thrown here ends [droppedFiles] for the rest of the session. A file that cannot be read arrives empty
    * instead of being left out, because an empty file is one the import reports as skipped - so the user is told,
    * where leaving it out would say nothing at all.
    *
    * `Throwable` rather than `Exception`: what a `js(...)` block throws arrives as a `JsException`, which is not an
    * `Exception`, and what a rejected promise arrives as is the coroutines library's business.
    */
   private suspend fun JsAny.toImportedFile(): ImportedFile? {
       val name = try {
           fileName(this).toString()
       } catch (_: Throwable) {
           return null
       }
       val bytes = try {
           fileBytes(this).await<Int8Array?>()?.toByteArray()
       } catch (exception: CancellationException) {
           throw exception
       } catch (exception: Throwable) {
           println("Could not read \"$name\": ${exception.message}")
           null
       }
       return ImportedFile(name = name, bytes = bytes ?: ByteArray(0))
   }
   ```

   Add `import kotlinx.coroutines.CancellationException`. Keep the `?: ByteArray(0)`: it is what turns an unreadable
   `song.cho` into "… · 1 skipped" in the import result (`PrepareImportUseCaseImpl` skips a song file that splits into
   no parts, a setlist that does not parse, an archive that does not unpack and any other extension). Do **not** change
   it to `mapNotNull`-and-drop as the other platforms do; on those the user at least picked from a dialog that listed
   only readable files.

   Plan 15 adds a size cap on this same path. Its hunk is a check of `file.size` *before* `fileBytes(...)` is called,
   inside `toImportedFile`; this plan's hunk is the `try` around the read. They do not overlap whichever lands first:
   if 15 is already in, keep its check and wrap what follows it.

3. **`droppedFiles` ends quietly instead of throwing.** `nextDrop()` only ever resolves and step 2 removed the one
   thing that threw inside the loop, so this is the belt: should the JS side ever be broken (a browser extension
   deleting `window.__campfireDrops`), drag and drop stops working and the app carries on.

   ```kotlin
   internal fun droppedFiles(): Flow<List<ImportedFile>> = flow {
       listenForDrops()
       while (true) {
           emit(nextDrop().await<JsArray<JsAny>?>()?.toImportedFiles().orEmpty())
       }
   }.catch { exception ->
       // The collector is a LaunchedEffect at the root of the app, and what leaves one of those takes the
       // composition with it. Losing drag and drop is the smaller loss.
       println("Could not read the dropped files: ${exception.message}")
   }
   ```

   Add `import kotlinx.coroutines.flow.catch`. Do not put a `try`/`catch` around the body of the `while` instead: if
   `nextDrop()` itself were what failed, that loop would spin.

4. **`listenForDrops` tells folders from files and walks a folder one level deep.** Replace the function and extend
   its KDoc. Everything that touches `event.dataTransfer.items` runs synchronously inside the handler, because the
   browser empties that list as soon as the handler returns; only the `FileSystemEntry` objects taken from it stay
   usable afterwards. The queue now holds *promises* of file arrays, which keeps two quick drops in order even when
   the first is a large folder; `nextDrop()` needs no change, since resolving a promise with a promise adopts it.

   ```kotlin
   /**
    * A dropped folder is opened one level deep: its own files are taken, in name order, and the folders inside it
    * are not. That is what "my folder of songs" is, and it keeps a home directory dropped by accident from being read
    * into memory. Names starting with a dot are left out of a folder, as they are out of an archive - nobody chose
    * those, and the `._` companions macOS writes next to every file on a foreign volume carry a song's extension.
    *
    * `webkitGetAsEntry` and `getAsFile` are only answered while the `drop` handler is running, so every item is asked
    * before the handler returns and the reading happens afterwards. Nothing here rejects: an entry that cannot be
    * read resolves to nothing, and a browser without `webkitGetAsEntry` hands a folder over as a `File` that
    * [fileBytes] then reports as unreadable.
    */
   private fun listenForDrops(): Unit = js(
       """(function () {
           if (window.__campfireDropsReady) return;
           window.__campfireDropsReady = true;
           window.__campfireDrops = [];
           window.__campfireDropResolve = null;
           function stop(event) { event.preventDefault(); event.stopPropagation(); }
           function fileOf(entry) {
               return new Promise(function (resolve) {
                   entry.file(resolve, function () { resolve(null); });
               });
           }
           function childrenOf(directory) {
               return new Promise(function (resolve) {
                   var reader = directory.createReader();
                   var children = [];
                   function read() {
                       reader.readEntries(function (batch) {
                           if (batch.length === 0) { resolve(children); return; }
                           children = children.concat(Array.prototype.slice.call(batch));
                           read();
                       }, function () { resolve(children); });
                   }
                   read();
               });
           }
           function filesIn(directory) {
               return childrenOf(directory).then(function (children) {
                   return Promise.all(children
                       .filter(function (child) { return child.isFile && child.name.charAt(0) !== '.'; })
                       .sort(function (first, second) { return first.name < second.name ? -1 : first.name > second.name ? 1 : 0; })
                       .map(fileOf));
               });
           }
           function collect(dataTransfer) {
               var pending = [];
               var items = dataTransfer.items;
               if (items && items.length > 0) {
                   for (var i = 0; i < items.length; i++) {
                       if (items[i].kind !== 'file') continue;
                       var entry = items[i].webkitGetAsEntry ? items[i].webkitGetAsEntry() : null;
                       if (entry && entry.isDirectory) {
                           pending.push(filesIn(entry));
                       } else {
                           var file = items[i].getAsFile();
                           if (file) pending.push(Promise.resolve([file]));
                       }
                   }
               } else if (dataTransfer.files && dataTransfer.files.length > 0) {
                   pending.push(Promise.resolve(Array.prototype.slice.call(dataTransfer.files)));
               }
               if (pending.length === 0) return null;
               return Promise.all(pending).then(function (groups) {
                   var files = [];
                   groups.forEach(function (group) {
                       group.forEach(function (file) { if (file) files.push(file); });
                   });
                   return files;
               }, function () { return []; });
           }
           window.addEventListener('dragover', stop);
           window.addEventListener('drop', function (event) {
               stop(event);
               var files = event.dataTransfer ? collect(event.dataTransfer) : null;
               if (!files) return;
               if (window.__campfireDropResolve) {
                   var resolve = window.__campfireDropResolve;
                   window.__campfireDropResolve = null;
                   resolve(files);
               } else {
                   window.__campfireDrops.push(files);
               }
           });
       })()"""
   )
   ```

   `readEntries` is called until it answers with an empty batch because Chromium hands out at most 100 entries per
   call. A drop with no file in it (dragged text, a link) queues nothing. Keep the first paragraph of the existing
   KDoc on `droppedFiles` as it is.

5. **Desktop parity (separable; same behaviour, different file).** In
   `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.desktop.kt`,
   make `readAsImportedFiles` open a directory one level deep too, so that the same gesture means the same thing in
   both builds that have it:

   ```kotlin
   /**
    * Reads whatever of the given paths can be read, which is how a file reaches the app without a dialog: as a command
    * line argument from an "open with", or as a drop onto the window. A folder stands for the files directly inside
    * it - not for the folders in there, and not for the hidden files nobody chose. Anything unreadable is left out,
    * and anything the import does not recognise is reported by it as skipped.
    */
   fun List<String>.readAsImportedFiles() = flatMap { path ->
       val file = File(path)
       if (file.isDirectory) {
           file.listFiles { child -> child.isFile && !child.name.startsWith(".") }.orEmpty().sortedBy { it.name }
       } else {
           listOf(file)
       }
   }.mapNotNull { file ->
       try {
           if (file.isFile) ImportedFile(name = file.name, bytes = file.readBytes()) else null
       } catch (exception: Exception) {
           println("Could not read \"${file.path}\": ${exception.message}")
           null
       }
   }
   ```

   `listFiles` returns null for a directory that cannot be listed, hence the `orEmpty()`. Plan 15 caps the size of
   what this function reads and moves the drop's read off the AWT event thread, and plan 44 changes who calls it; both
   leave the directory branch alone. If this step collides with either in scheduling, drop it from this plan and do
   it after them — steps 1–4 are the bug fix.

## Tests
None (UI is untested; `:presentation` has no test source set).

## Verify
`./gradlew :app:web:wasmJsBrowserDevelopmentRun`, then in Chrome, Firefox and Safari:
1. Drop a folder holding a few `.cho` files, a `.DS_Store`, a `._song.cho` and a sub-folder: the songs are imported,
   the message counts them, the dot files and the sub-folder are not mentioned.
2. Drop a folder of more than 100 songs: all of them arrive (the `readEntries` loop).
3. Drop two files and a folder together: everything arrives in one import.
4. Drop an empty folder, then a single `.cho`: the second drop still imports (the flow is alive).
5. Drag a piece of selected text or a link from another tab onto the page: nothing happens, nothing is logged.
6. In the DevTools console run `File.prototype.arrayBuffer = function () { return Promise.reject(new Error('x')); }`
   and drop `song.cho`: the snackbar says "… · 1 skipped", and after reloading, drops work as before. With the same
   override, *Import files* from the picker behaves the same way instead of answering "Import failed".
7. Desktop (`./gradlew :app:desktop:run`): drop the same folder on the window; the songs directly in it are imported.

Compile: `./gradlew :app:web:wasmJsBrowserDistribution :app:desktop:compileKotlin`.

## Docs
- `presentation/CLAUDE.md`, the `wasmJsMain/ui/CampfireWebApp.kt` bullet: "files dropped anywhere on the page are
  imported" becomes "files dropped anywhere on the page are imported, a dropped folder standing for the files directly
  inside it; a file the browser cannot read arrives empty, so the import reports it as skipped rather than the drop
  failing". The `desktopMain/ui/CampfireDesktopApp.kt` bullet gains "(a folder standing for the files directly inside
  it)" after "accepts dropped files" if step 5 is done.
- `app/web/CLAUDE.md:81` ("Files reach it through the picker … or by being dropped on the page."): add "— files or a
  folder, which is opened one level deep".
- Root `CLAUDE.md`, Web section, the `js(...)` bullet stays true (callbacks still come back as promises).

## Touches
- `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.wasmJs.kt`
- `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.desktop.kt` (step 5 only)
- `presentation/CLAUDE.md`
- `app/web/CLAUDE.md`

## Depends on
Nothing. Shares `toImportedFiles` / `readAsImportedFiles` with plan 15 (size caps) and `readAsImportedFiles`' callers
with plan 44; the hunks are separable as described in steps 2 and 5, so they only need to be scheduled one after
another, in any order.
