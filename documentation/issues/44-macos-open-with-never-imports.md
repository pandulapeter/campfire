# 44 · macOS: "Open with Campfire" and double-clicking a `.cho` start the app and import nothing

**Severity:** wrong behaviour (desktop, macOS only; every time — and it is a declared document type, which App Review tries) · **Area:** `:app:desktop` (`CampfireDesktopApplication.kt`, new `OpenedFiles.kt`)

## Symptom
1. Install the macOS build (the `.dmg` from a release, or `./gradlew :app:desktop:createDistributable`).
2. In Finder, right-click a `.cho` / `.chopro` / `.chordpro` / `.crd` / `.chord` / `.pro` file → Open With → Campfire
   (or make Campfire the default and double-click).
3. Campfire starts, or comes to the front if it was running. No song is imported and no message is shown — both on
   a cold start and with the app already open.

On Windows the same gesture works, because there the file really is a command-line argument.

## Cause
`app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt:39-42`: the command line is the
only source of files.

```kotlin
fun main(args: Array<String>) {
    startCampfireDependencyGraph()
    application {
        val filesToImport = remember { MutableStateFlow(args.toList().readAsImportedFiles()) }
```

`app/desktop/build.gradle.kts:55` (`macOS { … chordProFileAssociations() }`) makes jpackage write the six extensions
into the bundle's `CFBundleDocumentTypes`, so Finder offers Campfire — but macOS never passes an opened document to an
application bundle as an argument. It sends an `odoc` Apple event, which the JDK delivers only to a handler registered
with `java.awt.Desktop.setOpenFileHandler`. The repository has none (`grep -rn "setOpenFileHandler\|OpenFilesHandler"`
finds nothing), so the event is received by AWT and thrown away.

What the JDK does with such an event, checked in the JDK 21 sources (`java.desktop/com/apple/eawt/_AppEventHandler.java`,
`_OpenFileDispatcher extends _QueuingAppEventDispatcher`): events that arrive **before the first handler is set are
queued and delivered the moment one is**, and after that first `setHandler` the queue is gone for good
(`queuedEvents = null`) — an event that arrives while the handler is `null` again is dropped. So the cold-start file is
not lost as long as a handler is registered at some point, and the handler must be registered **once and never taken
away**.

Two smaller things in the same lines:
- `readAsImportedFiles()` runs inside `remember { }`, that is during the first composition on the AWT event thread.
- A `MutableStateFlow` replays its value to every new collector, so the arguments would be imported again if
  `LaunchedEffect(filesToImport)` in `CampfireApp` were ever restarted. A channel has neither problem.

## Fix
1. **New file** `app/desktop/src/main/java/com/pandulapeter/campfire/OpenedFiles.kt` (MPL header copied from
   `CampfireDesktopApplication.kt`):

   ```kotlin
   package com.pandulapeter.campfire

   import com.pandulapeter.campfire.data.model.domain.ImportedFile
   import com.pandulapeter.campfire.presentation.ui.platform.readAsImportedFiles
   import kotlinx.coroutines.Dispatchers
   import kotlinx.coroutines.channels.Channel
   import kotlinx.coroutines.flow.Flow
   import kotlinx.coroutines.flow.flowOn
   import kotlinx.coroutines.flow.map
   import kotlinx.coroutines.flow.receiveAsFlow
   import java.awt.Desktop

   /**
    * Every file the operating system asks Campfire to open, whichever way the request arrives: as a command line
    * argument, which is how Windows and Linux start an application for a file, or as the Apple event macOS sends
    * instead - to an application that is starting and to one that is already running alike. [open] is the one way in,
    * so whatever else learns of a file to open (a second instance handing its arguments to this one) calls it too.
    *
    * A channel rather than a state: a request is imported once, by whoever collects [files], and it waits there for
    * as long as nobody does - the first requests arrive before the window exists.
    */
   internal object OpenedFiles {

       private val requests = Channel<List<String>>(Channel.UNLIMITED)

       /** The requested files that could be read, one list per request, read off the thread that collects them. */
       val files: Flow<List<ImportedFile>> = requests.receiveAsFlow().map { it.readAsImportedFiles() }.flowOn(Dispatchers.IO)

       /** Safe to call from any thread, and before the window exists. */
       fun open(paths: List<String>) {
           if (paths.isNotEmpty()) requests.trySend(paths)
       }

       /**
        * Only macOS sends these events, and only there is this worth the price: asking `Desktop` anything starts the
        * AWT toolkit, which on Linux reads the display scale before Compose has had the chance to set it.
        *
        * The handler is never taken away again. The JDK holds on to the events that arrive before the first handler is
        * registered - the file the app was started for - but drops every one that finds the handler gone after that.
        */
       fun listenForSystemRequests() {
           if ("mac" !in System.getProperty("os.name").lowercase()) return
           val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop().takeIf { it.isSupported(Desktop.Action.APP_OPEN_FILE) } else null
           desktop?.setOpenFileHandler { event -> open(event.files.map { it.absolutePath }) }
       }
   }
   ```

   `Channel.UNLIMITED` makes `trySend` always succeed, so nothing is dropped between the handler (AWT event thread)
   and the collector. An empty request is not queued; a request whose every path is unreadable still emits an empty
   list, which `CampfireViewModel.importFiles` ignores, as it does today.

2. **`CampfireDesktopApplication.kt`**, `main`:

   ```kotlin
   /**
    * @param args Paths handed over by the operating system, which is how "open with" reaches a desktop application on
    *   Windows and Linux: it launches the app with the file as an argument. macOS sends an event instead, see
    *   [OpenedFiles].
    */
   fun main(args: Array<String>) {
       OpenedFiles.listenForSystemRequests()
       OpenedFiles.open(args.toList())
       startCampfireDependencyGraph()
       application {
           // (the `filesToImport` line is deleted)
           …
                   CampfireDesktopApp(
                       viewModel = currentViewModel,
                       filesToImport = OpenedFiles.files,
                   )
   ```

   Remove the now unused imports `kotlinx.coroutines.flow.MutableStateFlow` and
   `com.pandulapeter.campfire.presentation.ui.platform.readAsImportedFiles`; `remember` is still used. Registering
   before `startCampfireDependencyGraph()` costs nothing and keeps the order obvious; because of the JDK's own queue
   it would also work later, but it must not move into a `DisposableEffect` with an `onDispose { setOpenFileHandler(null) }`
   the way the quit handler next to it is written — see the KDoc above.

3. **Do not change** `CampfireDesktopApp`'s drop target (it has the view model at hand and calls `importFiles`
   directly; plan 15 moves that read off the event thread), `readAsImportedFiles` itself, or `build.gradle.kts`: the
   associations are already declared, `java.awt.desktop.*` is part of `java.desktop`, which Compose Desktop's default
   runtime modules include, and nothing needs adding to `modules(...)`.

4. **ProGuard.** No keep rule is needed: `java.awt.desktop.OpenFilesHandler` is a JDK (library) class that ProGuard
   neither renames nor removes, the lambda implementing it is reachable from `main`, and the `setQuitHandler` lambda
   in the same file already goes through the release build the same way. It still has to be *checked* on a release
   build, as `app/desktop/CLAUDE.md` demands of everything (see Verify).

5. **Plan 49 (single instance) plugs in here.** On Windows and Linux every "open with" while Campfire is running
   starts a second process; plan 49 makes that process hand its arguments to the first over a loopback socket and
   exit. The receiving end of that socket calls `OpenedFiles.open(paths)` — it is thread-safe and needs neither the
   window nor the view model — and brings the window forward. In `main`, 49's lock check goes **before**
   `OpenedFiles.open(args.toList())` (a second instance must not read its own arguments) and may go before or after
   `listenForSystemRequests()`; nothing else in this plan constrains it. macOS never starts a second instance of a
   bundle for a document, so the Apple event path needs nothing from 49.

## Tests
None (`:app:desktop` has no tests; the UI is untested).

## Verify
On a Mac:
1. `./gradlew :app:desktop:createDistributable`; the bundle is
   `app/desktop/build/compose/binaries/main/app/Campfire.app`.
2. Cold start: with Campfire not running, `open -a app/desktop/build/compose/binaries/main/app/Campfire.app ~/song.cho`
   (`open -a` sends the same `odoc` event Finder does). The app starts and the snackbar reports one imported song.
3. Already running: run the same command with another file; the window comes forward and the song is imported. Run
   it with two files at once: one import, two songs.
4. While the editor is open with unsaved text, open a file: the import runs, the editor is untouched.
5. Finder: right-click a `.chopro` → Open With → Campfire.
6. Release build: `./gradlew :app:desktop:createReleaseDistributable`, then repeat 2 and 3 against
   `app/desktop/build/compose/binaries/main-release/app/Campfire.app` — this is the ProGuard check.
7. Arguments still work everywhere: `./gradlew :app:desktop:run --args="/path/to/song.cho"`.
8. On Linux, if a HiDPI machine is at hand: the window's scale is unchanged (the reason for the `os.name` guard).

Compile: `./gradlew :app:desktop:compileKotlin`.

## Docs
`app/desktop/CLAUDE.md`, the paragraph starting "`main` takes `args` because that is how "open with" reaches a desktop
application": replace with

> `main` takes `args` because that is how "open with" reaches a desktop application on Windows and Linux: the system
> launches the app with the file as an argument. macOS sends an `odoc` Apple event instead — to a starting app and to
> a running one alike — which the JDK hands to the handler `OpenedFiles` registers with `Desktop.setOpenFileHandler`
> as the first thing `main` does, on macOS only (asking `Desktop` anything starts AWT, which on Linux would read the
> display scale before Compose sets it) and for the life of the process (the JDK queues the events that precede the
> first handler and drops the ones that find it removed). Both end in `OpenedFiles.open(paths)`, a channel that is
> read off the event thread and imported by `CampfireApp`; anything else that learns of a file to open calls the same
> function. Files dropped onto the window take their own path (`Modifier.dragAndDropTarget` in `CampfireDesktopApp`).

`documentation/features.md:45` ("Open with") stays true and becomes true for macOS.

## Touches
- `app/desktop/src/main/java/com/pandulapeter/campfire/OpenedFiles.kt` (new)
- `app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt`
- `app/desktop/CLAUDE.md`

## Depends on
Nothing. Plan 49 depends on this one (it calls `OpenedFiles.open`), and plan 15 changes `readAsImportedFiles`, which
this plan only calls.
