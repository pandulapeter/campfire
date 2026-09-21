# 49 · Two copies of Campfire can run on one library and quietly undo each other

**Severity:** wrong behaviour (desktop on Windows and Linux: every "open with" while Campfire is running; web: every second tab) · **Area:** `:app:desktop` (`CampfireDesktopApplication.kt`, new `SingleInstance.kt`), `:presentation` (`Platform.desktop.kt`), `app/web` (`index.html`) · **Decision:** desktop = a lock file held with `FileChannel.tryLock()`, the second process hands its file arguments to the first over a loopback socket, asks it to come forward and exits; web = a Web Lock taken before the app is downloaded, a second tab shows an "already open in another tab" page with a Retry button

## Symptom
Desktop (Windows, Linux): Campfire is open. The user double-clicks a `.cho` in the file manager — or selects five
and presses Enter, which starts five processes. Every one of them opens a window of its own on the same data
directory, imports its file, and runs `restoreSync()`. From then on:

- each process writes the whole `preferences.json` from its own cached copy, so a transposition saved in window B
  takes back the theme, sort order or filter changed in window A a minute earlier, and the other way round;
- if A is in the middle of a sync run, B finds the "a run was going" marker, reports the run as **interrupted**, and
  rewrites `sync-index.json` from its stale snapshot while A keeps writing it; both then run the same plan;
- a song imported in B is not in A's list until A's window regains focus.

Web: open the app in a second tab. Tab A never learns about a song created in tab B (the web build never rescans,
because nothing else can write to OPFS — except another tab), and an editor in A saves over what B wrote.

Nothing is corrupted (every write is a uniquely named temp file moved into place), which is why it has gone
unnoticed: it is two views undoing each other.

## Cause
Nothing anywhere asks whether the library already has an owner. `app/desktop/.../CampfireDesktopApplication.kt:39-42`
starts Koin and opens the window unconditionally:

```kotlin
fun main(args: Array<String>) {
    startCampfireDependencyGraph()
    application {
        val filesToImport = remember { MutableStateFlow(args.toList().readAsImportedFiles()) }
```

and `app/web/src/wasmJsMain/resources/index.html:338` loads the app with a static tag, so the 16 MB download starts
before any script could decide otherwise:

```html
<script type="application/javascript" src="campfire.js" onerror="window.campfireLoadFailed()"></script>
```

Every repository (`BaseLocalDataRepository`) reads once and caches, which is correct for one owner and is what makes
two owners disagree.

macOS is mostly covered by the system: LaunchServices keeps one instance of a bundle and sends the running one the
open-file event (plan 44). A second process there takes `open -n Campfire.app` or starting the binary inside the
bundle by hand — rare, but the same lock covers it for free.

## Fix

### Desktop

The design, in one paragraph: the first thing `main` does is take an exclusive lock on `<data directory>/instance.lock`
and keep it for the life of the process. The process that gets it (the **primary**) opens a server socket on
`127.0.0.1`, port chosen by the operating system, and writes the port and a random token into
`<data directory>/instance.endpoint`, readable by the user only. A process that does not get the lock (a
**secondary**) reads that file, sends the token and its file arguments, waits for `OK`, and exits without having
started Koin, AWT or anything that touches the library. The primary feeds the received paths into the entry point
plan 44 creates for macOS open-file events and brings its window forward.

Both files live in the data directory's root, next to `library/` and `preferences/` — not inside `library/`, which
is the folder the Settings "Location" row opens and the one sync and export read.

1. **`presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.desktop.kt`** —
   make `desktopDataDirectory()` public, so `:app:desktop` does not become the third place that derives the path
   (`readAsImportedFiles` in the same source set is already public for the same consumer). Replace the `private fun`
   line and give it KDoc; the body stays as it is:

   ```kotlin
   /**
    * Where the desktop build keeps everything it owns: the library, the preferences, and the two files
    * `:app:desktop` uses to keep a second process from opening the same library.
    *
    * Derived the same way the storage derives it; the two have to agree, so keep this in step with
    * `FileStorage.desktop.kt` in `:data:source:local:implementation`.
    */
   fun desktopDataDirectory(): File {
   ```

2. **New file `app/desktop/src/main/java/com/pandulapeter/campfire/SingleInstance.kt`** (MPL header copied from
   `CampfireDesktopApplication.kt`):

   ```kotlin
   package com.pandulapeter.campfire

   import java.io.File
   import java.io.IOException
   import java.net.InetAddress
   import java.net.InetSocketAddress
   import java.net.ServerSocket
   import java.net.Socket
   import java.net.URLDecoder
   import java.net.URLEncoder
   import java.nio.channels.FileChannel
   import java.nio.channels.FileLock
   import java.nio.file.Files
   import java.nio.file.StandardOpenOption
   import java.nio.file.attribute.PosixFilePermissions
   import java.security.MessageDigest
   import java.security.SecureRandom
   import kotlin.concurrent.thread

   /**
    * Decides whether this process is the one that opens the library in [dataDirectory], and hands [paths] to the one
    * that already has if it is not.
    *
    * Every repository reads its files once and writes them whole from what it remembers, which is right for one
    * process and makes two of them take back each other's changes. So the first process locks a file for as long as
    * it lives and listens on the loopback interface, and every later one - which is what "open with" starts on
    * Windows and Linux while Campfire is running, once per selected file - sends it what it was asked to open and
    * leaves. The lock is the operating system's, so it goes with the process however that ends: the lock *file* being
    * there says nothing, and a crash leaves nothing to clean up.
    *
    * This has to run before Koin is started, since nothing of a second process may touch the library.
    *
    * @param paths Absolute paths: the two processes do not share a working directory.
    * @param onActivated Called on a background thread each time another process handed over, with the paths it was
    *   started with - none when it was started with none, which still means "come forward".
    * @return False if the running instance took over and this process should exit. True means carry on starting,
    *   which is also the answer when the lock cannot be asked for at all (a read-only or a network home directory) or
    *   when the running instance does not answer: an app that starts twice is the lesser evil next to one that does
    *   not start.
    */
   internal fun claimSingleInstance(
       dataDirectory: File,
       paths: List<String>,
       onActivated: (paths: List<String>) -> Unit,
   ): Boolean {
       val lock = try {
           dataDirectory.mkdirs()
           val channel = FileChannel.open(File(dataDirectory, LOCK_FILE_NAME).toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
           channel.tryLock() ?: run {
               channel.close()
               null
           }
       } catch (exception: Exception) {
           println("Could not ask for the single instance lock: ${exception.message}")
           return true
       }
       if (lock == null) return !handOver(dataDirectory, paths)
       heldLock = lock
       startListening(dataDirectory, onActivated)
       return true
   }

   /**
    * Referenced for the life of the process on purpose: a lock belongs to its channel, and a channel nobody holds is
    * closed by the garbage collector, which would let the next process in while this one is still running.
    */
   private var heldLock: FileLock? = null

   private fun startListening(dataDirectory: File, onActivated: (paths: List<String>) -> Unit) {
       val endpointFile = File(dataDirectory, ENDPOINT_FILE_NAME)
       try {
           // Whatever is there was written by a process that is gone, and a newcomer that read it would knock on a
           // port that is closed, or somebody else's by now.
           endpointFile.delete()
           val serverSocket = ServerSocket(0, BACKLOG, InetAddress.getByName(LOOPBACK_ADDRESS))
           val token = ByteArray(TOKEN_BYTES).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
           endpointFile.writeForOwnerOnly("${serverSocket.localPort}\n$token\n")
           Runtime.getRuntime().addShutdownHook(Thread { endpointFile.delete() })
           // A daemon, so that it is never the thread that keeps a closed application alive.
           thread(isDaemon = true, name = "campfire-single-instance") { serverSocket.serve(token, onActivated) }
       } catch (exception: Exception) {
           // The lock is held all the same, so a later process finds nobody to talk to and starts next to this one.
           println("Could not listen for other instances: ${exception.message}")
           endpointFile.delete()
       }
   }

   /**
    * The loopback interface is shared by every user of the machine and reachable from any web page a browser has
    * open, so the port alone must not be enough to make Campfire import a path: the token is, and it is in a file
    * only this user can read. On Windows the application data folder is the user's own already.
    */
   private fun File.writeForOwnerOnly(text: String) {
       val path = toPath()
       if ("posix" in path.fileSystem.supportedFileAttributeViews()) {
           Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
       }
       writeText(text)
   }

   private fun ServerSocket.serve(token: String, onActivated: (paths: List<String>) -> Unit) {
       val expectedHeader = "$PROTOCOL_HEADER $token".toByteArray()
       while (!isClosed) {
           try {
               accept().use { connection ->
                   connection.soTimeout = CONNECTION_TIMEOUT_MILLIS
                   // The sender closes its half once it has said everything, so the end of the stream is the end of
                   // the request, and the cap is what a stranger gets to make this process hold.
                   val lines = connection.getInputStream().readNBytes(MAX_REQUEST_BYTES).decodeToString().lines()
                   if (MessageDigest.isEqual(expectedHeader, lines.first().toByteArray())) {
                       onActivated(lines.drop(1).filter(String::isNotEmpty).map { URLDecoder.decode(it, Charsets.UTF_8) })
                       connection.getOutputStream().write("$ACKNOWLEDGEMENT\n".toByteArray())
                   }
               }
           } catch (exception: IOException) {
               // A connection that never spoke or left early. The next one is no worse for it.
           } catch (exception: IllegalArgumentException) {
               println("Another instance sent a path that could not be read: ${exception.message}")
           }
       }
   }

   /**
    * Tried more than once because the running instance may be one that started a moment ago - five files opened
    * together are five processes, and the four that lost the lock can be here before the winner has written its
    * port. The file is read again each time for the same reason.
    */
   private fun handOver(dataDirectory: File, paths: List<String>): Boolean {
       // Encoded so that a path with a line break in it is still one line of the request.
       val encodedPaths = paths.joinToString("\n") { URLEncoder.encode(it, Charsets.UTF_8) }
       repeat(HAND_OVER_ATTEMPTS) {
           try {
               val (port, token) = File(dataDirectory, ENDPOINT_FILE_NAME).readLines()
               Socket().use { socket ->
                   socket.connect(InetSocketAddress(InetAddress.getByName(LOOPBACK_ADDRESS), port.toInt()), CONNECTION_TIMEOUT_MILLIS)
                   socket.soTimeout = CONNECTION_TIMEOUT_MILLIS
                   socket.getOutputStream().write("$PROTOCOL_HEADER $token\n$encodedPaths".toByteArray())
                   socket.shutdownOutput()
                   if (socket.getInputStream().bufferedReader().readLine() == ACKNOWLEDGEMENT) return true
               }
           } catch (exception: Exception) {
               // No file yet, half a file, a closed port, or something on that port that is not Campfire.
           }
           Thread.sleep(HAND_OVER_RETRY_MILLIS)
       }
       println("The running instance did not answer, starting next to it.")
       return false
   }

   private const val LOCK_FILE_NAME = "instance.lock"
   private const val ENDPOINT_FILE_NAME = "instance.endpoint"
   private const val LOOPBACK_ADDRESS = "127.0.0.1"
   private const val PROTOCOL_HEADER = "CAMPFIRE 1"
   private const val ACKNOWLEDGEMENT = "OK"
   private const val BACKLOG = 16
   private const val TOKEN_BYTES = 32
   private const val MAX_REQUEST_BYTES = 1 shl 20
   private const val CONNECTION_TIMEOUT_MILLIS = 2_000
   private const val HAND_OVER_ATTEMPTS = 20
   private const val HAND_OVER_RETRY_MILLIS = 250L
   ```

   The protocol, spelled out (UTF-8, `\n` line ends, one request per connection):

   ```
   secondary -> primary   CAMPFIRE 1 <64 hex characters: the token from instance.endpoint>
                          <absolute path, URL-encoded so a path with a line break in it stays one line>   (0..n)
                          <the secondary closes its sending half>
   primary -> secondary   OK          (only if the first line matched; otherwise the connection is just closed)
   ```

   `instance.endpoint` is two lines: the port, then the token. An HTTP request a web page aims at the port starts
   with `POST / HTTP/1.1`, not with the header, so it is dropped like any other stranger.

   Things that are deliberate and should not be "fixed":
   - The lock file is never deleted, and its existence is never tested. Only `tryLock()` decides.
   - `tryLock()` returning `null` is the only "somebody else has it" answer. An exception (`IOException` on a
     read-only or NFS home, `UnsupportedOperationException`) means the question could not be asked: start anyway.
   - The acknowledgement is written *after* `onActivated`, so `OK` means the paths are queued. A secondary that
     misses the `OK` retries, and the import disregards a file that is already in the library with the same content,
     so a path delivered twice costs nothing.
   - No coroutine here: this runs before anything else exists, and the listener is a blocked `accept`, which a
     coroutine cannot cancel anyway (see `DesktopSyncAuthenticator`).

3. **`app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt`** — plan 44 leaves `main`
   with `OpenedFiles` (`app/desktop/.../OpenedFiles.kt`): one process-wide channel of *paths*, fed by `args` and by
   the macOS `OpenFilesHandler` through `OpenedFiles.open(paths)`, read off the event thread and handed to
   `CampfireDesktopApp` as `filesToImport`. `open` is thread-safe and ignores an empty list, and plan 44 names this
   plan as its third caller. Do not build a second pipeline.

   ```kotlin
   fun main(args: Array<String>) {
       val activations = Channel<Unit>(Channel.CONFLATED)
       val isFirstInstance = claimSingleInstance(
           dataDirectory = desktopDataDirectory(),
           paths = args.map { File(it).absolutePath },
           onActivated = { paths ->
               OpenedFiles.open(paths)
               activations.trySend(Unit)
           },
       )
       // Nothing has been started yet, so there is nothing to wind down - and Koin must not be, since its singletons
       // are what would read the library a second time.
       if (!isFirstInstance) exitProcess(0)
       OpenedFiles.listenForSystemRequests()
       OpenedFiles.open(args.toList())
       startCampfireDependencyGraph()
       application {
           val windowState = rememberWindowState()
           …
           Window(
               state = windowState,
               title = "Campfire",
               …
           ) {
               window.minimumSize = Dimension(400, 400)
               // Another process was asked to open Campfire and handed over to this one, so this is the window the
               // user is looking for.
               LaunchedEffect(Unit) {
                   for (activation in activations) {
                       windowState.isMinimized = false
                       window.bringForward()
                   }
               }
               …
   ```

   and, at the bottom of the file:

   ```kotlin
   /**
    * Raises the window as far as the platform lets an application raise itself. Windows only lets the foreground
    * process take the focus, and this one is not - the process the user just started is - so `toFront` alone ends in
    * a flashing task bar button there; being always on top for a moment is what moves the window above the others
    * regardless. A Wayland compositor may refuse both and show its own "Campfire is ready" notice, which is its call.
    */
   private fun ComposeWindow.bringForward() {
       isVisible = true
       val wasAlwaysOnTop = isAlwaysOnTop
       isAlwaysOnTop = true
       toFront()
       isAlwaysOnTop = wasAlwaysOnTop
       requestFocus()
       if (Desktop.isDesktopSupported()) {
           Desktop.getDesktop().takeIf { it.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND) }?.requestForeground(true)
       }
   }
   ```

   New imports: `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.ui.awt.ComposeWindow`,
   `androidx.compose.ui.window.rememberWindowState`,
   `com.pandulapeter.campfire.presentation.ui.platform.desktopDataDirectory`, `java.io.File`,
   `kotlin.system.exitProcess`, `kotlinx.coroutines.channels.Channel`.
   `APP_REQUEST_FOREGROUND` is macOS only, where the call is what `open -n` needs; elsewhere the `takeIf` is null.

   The claim goes **before** `OpenedFiles.listenForSystemRequests()` and `OpenedFiles.open(args…)` as well as before
   Koin: the JDK queues open-file events until a handler is set, so nothing is lost by the few milliseconds, a
   secondary has no business starting the AWT toolkit (which asking `Desktop` anything does), and a secondary must
   not queue its own arguments. The primary's own `args` are still opened by the line plan 44 wrote.

   Under the Mac App Store sandbox a handed-over path may not be readable by the primary (the sandbox extension
   belongs to the process that was given the file); `readAsImportedFiles()` already logs and skips such a file. Not
   worth more: a second process on macOS takes `open -n`.

4. **Packaging** — everything used is in `java.base` (`java.nio.channels`, `java.net`, `java.security`), so
   `modules(...)` in `app/desktop/build.gradle.kts` does not change; run `./gradlew :app:desktop:suggestRuntimeModules`
   once to confirm. No reflection, so no ProGuard rule. The release build still has to be started once
   (`:app:desktop:runRelease`), as `app/desktop/CLAUDE.md` asks of every change there.

### Web

`index.html` localizes nothing today: the failure message is English only, with a comment saying so ("it is shown
before the app's own language is known"). The app's language preference is in OPFS and not worth reading from the
loading page; but a preference left at "system default" — the default — follows the browser's language, and so can
the page. This plan introduces a two-entry text table picked by `navigator.language`, moves the existing message
into it, and plan 53 adds its own message to the same table.

5. **`app/web/src/wasmJsMain/resources/index.html`**, markup: replace the comment above `#splash-message` and the
   button (lines 152–155) — the label and the click handler are now set by the script:

   ```html
       <!-- Only shown when the app could not be started. In the browser's language where that is one the app speaks:
            the app's own choice is in storage this page does not read, and follows the browser unless it was changed. -->
       <p id="splash-message" role="alert" hidden></p>
       <button id="splash-retry" type="button" hidden></button>
   ```

   and **delete** the static tag on line 338 (`<script … src="campfire.js" onerror="window.campfireLoadFailed()">`).
   With it goes `window.campfireLoadFailed = showFailure;` and the comment above it (lines 264–266): the injected
   tag below is wired directly.

6. Same file, script. After the `var retry = …` line add:

   ```js
        var TEXTS = {
            en: {
                loadFailed: 'Campfire could not be loaded. Check the connection and try again.',
                alreadyOpen: 'Campfire is already open in another tab. Close that tab first, or carry on there.',
                retry: 'Try again'
            },
            hu: {
                loadFailed: 'A Campfire betöltése nem sikerült. Ellenőrizd a kapcsolatot, és próbáld újra.',
                alreadyOpen: 'A Campfire már meg van nyitva egy másik lapon. Előbb zárd be azt a lapot, vagy folytasd ott.',
                retry: 'Újra'
            }
        };
        var text = /^hu\b/i.test(navigator.language || '') ? TEXTS.hu : TEXTS.en;
        if (text === TEXTS.hu) {
            document.documentElement.lang = 'hu';
        }
        retry.textContent = text.retry;
   ```

   In `showFailure()` replace the literal with `text.loadFailed`, and add `retry.onclick = function () {
   location.reload(); };` before `retry.hidden = false;`.

   Replace the final `schedule();` (line 335) with:

   ```js
        // One library, one page: the app reads its files once and writes them whole from what it remembers, so a
        // second tab would take back what the first one saved. The Web Lock is asked for before anything of the
        // app is downloaded, which is why campfire.js is added from here rather than by a tag of its own - a tab
        // that is turned away has fetched this page and its icon, and nothing else.
        var LOCK_NAME = 'campfire-library';
        var hasStarted = false;

        function startApp() {
            if (hasStarted) {
                return;
            }
            hasStarted = true;
            message.hidden = true;
            retry.hidden = true;
            track.hidden = false;
            var script = document.createElement('script');
            script.src = 'campfire.js';
            // A script that fails to download reports it to its own tag only, never to the window.
            script.onerror = showFailure;
            document.body.appendChild(script);
            schedule();
        }

        function showAlreadyOpen() {
            track.hidden = true;
            message.textContent = text.alreadyOpen;
            message.hidden = false;
            // Asks again in place rather than reloading: a tab that came back from a consent page still has the
            // answer in its address bar, and the app has to be the one that takes it out.
            retry.onclick = function () {
                claimLibrary(startApp, showAlreadyOpen);
            };
            retry.hidden = false;
        }

        /**
         * A lock is held for as long as the promise its callback returns is pending, and this one never settles:
         * the library is this page's until the page is closed or navigates away, which is when the browser lets
         * go of it. A browser without Web Locks, or one that refuses them here, starts the app as it always has.
         */
        function claimLibrary(onGranted, onRefused) {
            if (!navigator.locks || !navigator.locks.request) {
                onGranted();
                return;
            }
            navigator.locks.request(LOCK_NAME, { ifAvailable: true }, function (lock) {
                if (!lock) {
                    onRefused();
                    return undefined;
                }
                onGranted();
                return new Promise(function () {});
            }).catch(onGranted);
        }

        // A page that is put into the back/forward cache loses its locks, and gets none back when it is restored -
        // pressing Back on the consent page is how that happens. It asks again, and if another tab has taken the
        // library in the meantime it reloads, which lands on the page above.
        window.addEventListener('pageshow', function (event) {
            if (event.persisted && hasStarted) {
                claimLibrary(function () {}, function () {
                    location.reload();
                });
            }
        });

        claimLibrary(startApp, showAlreadyOpen);
   ```

   Notes for the implementer:
   - `track.hidden` needs the attribute to win over the stylesheet: `#splash-track` sets no `display`, so the UA
     rule for `[hidden]` applies. `#splash-message` and `#splash-retry` already rely on the same thing.
   - `schedule()` moves into `startApp()` so that a tab that was turned away does not run an animation frame loop
     over a hidden bar for as long as it stays open.
   - The `error` / `unhandledrejection` listeners stay registered from the top, as today.
   - webpack derives its public path from `document.currentScript.src`, which is set while an injected classic
     script runs just as it is for a parsed one, so the `.wasm` URLs resolve as before — check it in the network
     tab once (Verify).
   - The font `<link rel="preload">`s stay in `<head>`. A tab that is turned away exists only because another tab of
     the same browser has the app open, so they are answered from the HTTP cache.
   - Deliberately **not** done: queueing a second, waiting lock request so that the turned-away tab starts by
     itself when the other one closes. A background tab that downloads and starts an app because a different tab
     was closed is a surprise; the decision is a Retry button.

7. **The OAuth round trip** (confirmed in `SyncAuthenticator.wasmJs.kt:68-70`: `window.location.assign(url)`, same
   tab, no `window.open`). Leaving for the consent page unloads the document, which releases the lock; the redirect
   loads the page again in the same tab, which asks for the lock like any fresh load and gets it. The one way to
   lose it is a second tab whose Retry was pressed while the first was away: the returning tab then shows the
   "already open" page with `?code=…` still in its address bar, and because Retry asks again in place (step 6), the
   app that eventually starts there still finds the code and finishes connecting. No Kotlin changes.

   Web Locks are per origin and per storage partition, exactly like OPFS, so a private window (its own OPFS) gets
   its own lock, and the development server and the deployment do not block each other. Every browser that has
   Wasm GC has Web Locks (Chrome 69, Firefox 96, Safari 15.4), so the fallback is for the unexpected.

## Tests
None: `:app:desktop` and `index.html` are outside the tested modules (only pure logic is tested), and
`claimSingleInstance` is two processes and a socket. It is exercised by hand below.

## Verify
Desktop (`./gradlew :app:desktop:run` cannot start twice from one Gradle daemon comfortably; build
`./gradlew :app:desktop:createDistributable` and start
`app/desktop/build/compose/binaries/main/app/Campfire…` twice, or use two terminals with `run`):
1. Start Campfire. `<data directory>/instance.lock` and `instance.endpoint` exist; on macOS/Linux
   `ls -l instance.endpoint` shows `-rw-------`.
2. Start it again with a song: `… Campfire /path/to/new_song.cho`. No second window; the first one comes forward
   (de-minimize it first to see that too) and imports the song; the second process exits with code 0 within a
   second.
3. Start it again with no arguments: the first window comes forward, nothing is imported.
4. Windows: select several `.cho` files in Explorer and press Enter with Campfire closed. One window, all files
   imported (as one or several import batches — both are fine).
5. `kill -9` the app, start it again: it starts normally (the stale lock file does not block), and
   `instance.endpoint` holds a new port and token.
6. `printf 'CAMPFIRE 1 wrong\n/etc/hosts\n' | nc 127.0.0.1 <port>`: no `OK`, nothing imported, window does not move.
7. `chmod a-w` the data directory (or point `XDG_DATA_HOME` at a read-only folder): the app still starts and logs
   "Could not ask for the single instance lock".
8. Quit normally: `instance.endpoint` is gone, `instance.lock` stays.
9. `./gradlew :app:desktop:suggestRuntimeModules` prints no new module; `./gradlew :app:desktop:runRelease` starts.

Web (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`, then the distribution once):
1. Open the app in tab A, then the same URL in tab B. B shows the icon, the name, the message and "Try again", no
   progress bar; its network tab lists no `campfire.js` and no `.wasm`.
2. Press Try again in B with A open: the message stays. Close A, press it again: B downloads and starts.
3. Set the browser's language to Hungarian: the Hungarian texts, `<html lang="hu">`.
4. In A, the `.wasm` requests still resolve next to `campfire.js` and the progress bar still moves.
5. Block `campfire.js` in DevTools and reload: the "could not be loaded" message and a Try again that reloads.
6. With sync configured: Settings → Connect in tab A, approve, come back: the app starts and connects. Repeat, but
   press Back on the consent page: the app is usable (Chrome reloads it; Firefox/Safari restore it and the
   `pageshow` handler takes the lock again — `await navigator.locks.query()` in the console shows it held).
7. In the console, `delete Navigator.prototype.locks` cannot be run early enough; instead temporarily edit the
   condition to `if (true)` to see the fallback start the app.

Compile: `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64
:app:web:wasmJsBrowserDistribution` (the `:presentation` visibility change is desktop only, but cheap to confirm).

## Docs
- `app/desktop/CLAUDE.md`: a new paragraph after the one about `main` taking `args` — one process per data
  directory: `SingleInstance.kt` takes `instance.lock` with `tryLock()` before Koin starts, the holder listens on
  `127.0.0.1` on a port of the system's choosing and writes it with a random token into `instance.endpoint`
  (owner-only), a later process sends the token and its arguments, gets `OK` and exits, the paths join the ones
  from `args` and the macOS open-file handler, the window is brought forward (and why `isAlwaysOnTop` is toggled);
  fail-open when the lock cannot be asked for or nobody answers; `java.base` only. The sync paragraph's "the desktop
  briefly becomes a web server" stays true but is no longer the only socket — say "for sync, a second socket …".
- `app/web/CLAUDE.md`, the `index.html` bullet: `campfire.js` is no longer "loaded by a `<script>` tag" in the
  markup but by one the page adds once it holds the `campfire-library` Web Lock, held by a promise that never
  settles; a second tab gets the "already open" page with a Retry that asks again in place (and why: the OAuth
  answer in the address bar); the `pageshow` re-claim; the page's texts follow `navigator.language` (English and
  Hungarian) instead of "English, like the rest of this page".
- Root `CLAUDE.md`: in the Web section add one bullet — one tab per origin owns the library, by Web Lock, which is
  what keeps "OPFS cannot change behind the app's back" true; in the library layout block add
  `instance.lock / instance.endpoint   desktop only: what keeps a second process off the library (see app/desktop)`.
- `documentation/publishing/mac-app-store.md`, the `com.apple.security.network.server` line: it now covers two
  loopback sockets — the OAuth redirect on `53682` and the single-instance listener — and the review notes should
  name both.

## Touches
- `app/desktop/src/main/java/com/pandulapeter/campfire/SingleInstance.kt` (new)
- `app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt`
- `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.desktop.kt`
- `app/web/src/wasmJsMain/resources/index.html`
- `app/desktop/CLAUDE.md`
- `app/web/CLAUDE.md`
- `CLAUDE.md`
- `documentation/publishing/mac-app-store.md`

## Depends on
44 (`OpenedFiles.open`, the one way a path to open gets into the running app, which this plan adds a caller to). Plan 53 builds on the `TEXTS` table this plan puts into `index.html`.
