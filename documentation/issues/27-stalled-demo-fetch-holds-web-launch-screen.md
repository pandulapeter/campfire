# 27 · Web: a first visit whose demo song request never answers stays on the loading page for good

**Severity:** wrong behaviour, unlikely (web only; first visit, or the first visit after the site's data was cleared. It takes one of three small requests to the site stalling rather than failing, a few seconds after the same site served ~10 MB of binaries. But then the app never opens, and reloading goes down the same path) · **Area:** `:presentation` (`CampfireViewModel.kt`: `readDemoLibrary`)

## Symptom
1. Open the web build in a browser that has never opened it (or after clearing the site's data), on a connection
   that stalls: a captive network, a proxy that holds requests, a mobile connection dropping out between the binaries
   and the first frame.
2. The loading page fills its progress bar and stays. The app underneath never shows, however long one waits.
3. The same happens on "Add the demo songs" in Settings on a stalling connection, in milder form: the row stays
   disabled until the page is reloaded.

## Cause
A first run plants the demo library before the launch screen may go
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:1209-1227`):
`isDemoLibraryPending` starts true, `hasLibraryToShow` (`:272-276`) is false while it is, and only the `finally` of
`plantDemoLibraryOnFirstRun` clears it. In between, `readDemoLibrary()` (`:1230-1237`) calls `DemoLibrary.read()`,
which is `Res.readBytes("files/demo/…")` for three files (`DemoLibrary.kt:50-55`). On the web that is a `fetch` of each
file with no deadline. A request that fails throws, `readDemoLibrary` answers null and the app opens on an empty
library, which is fine. A request that neither answers nor fails leaves the coroutine suspended: the `finally` never
runs, `hasLibraryToShow` stays false, `LaunchScreen` never fades, `onAppReady` is never called and the page's own
loading screen (`app/web`'s `index.html`, waiting for `window.campfireReady`) stays up. The preferences are written
only after the planting, so the next load is a first run again and asks for the same files.

`importDemoLibrary` (`:1179-1195`) reads through the same function and holds `isAddingDemoLibrary` meanwhile, which is
what keeps the Settings offer disabled.

The three other platforms read these files from the APK, the jar or the app bundle, which cannot stall. The web's
drawables had the same problem and got a deadline for it (`DrawablePreload.wasmJs.kt`, `PRELOAD_DEADLINE_MILLIS`,
"Compose resources never reports a drawable that failed to load").

## Fix
Give the read a deadline in the one function both callers go through, and treat running out of it as the failure it
already handles.

1. `CampfireViewModel.kt`, `readDemoLibrary` (`:1230-1237`):

   ```kotlin
   /**
    * Null where the bundled files could not be read, which each caller then says as much about as it should. On the web
    * they are requests to the site, and one that neither answers nor fails would otherwise hold whatever waits for it
    * for good - on a first run, the launch screen, which only goes once the demo has been planted or given up on.
    */
   private suspend fun readDemoLibrary() = try {
       withTimeoutOrNull(DEMO_LIBRARY_READ_TIMEOUT_MILLIS) { DemoLibrary.read() }
           .also { if (it == null) println("Could not read the demo library in time.") }
   } catch (exception: CancellationException) {
       throw exception
   } catch (exception: Exception) {
       println("Could not read the demo library: ${exception.message}")
       null
   }
   ```

   (`withTimeoutOrNull` answers null rather than throwing, so the `CancellationException` branch still only sees a real
   cancellation.) Import `kotlinx.coroutines.withTimeoutOrNull`.

2. Companion: `private const val DEMO_LIBRARY_READ_TIMEOUT_MILLIS = 10_000L` — generous next to the drawables' five
   seconds, since what it cuts short is a first impression rather than a frame, and on the three platforms that read
   from their own package it can never be reached.

3. Nothing else changes. A first run that gave up opens on the empty library and still saves the preferences, so the
   next start is not a first run; the Settings offer and the empty state's "Add the demo songs" are there to get the
   songs later. `importDemoLibrary` reports `Message.ImportFailed`, as for any unreadable demo.

4. Do **not**:
   - put the deadline around the whole of `plantDemoLibraryOnFirstRun`: the import it enqueues can legitimately wait,
     behind a file opened with the app as it first starts and that file's conflicts question, for as long as the user
     takes to answer. Only the read has no reason to take long.
   - move the deadline into `DemoLibrary.read()` or into the web platform code: the view model is where "given up" has
     a meaning, and the other callers of `Res.readBytes` are not waited on by anything.

## Tests
None (UI is untested).

## Verify
1. Simulate the stall: temporarily put `awaitCancellation()` at the start of `DemoLibrary.read()` (remove it before
   committing). Web (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`), in a fresh browser profile or after clearing
   the site's data. Before the fix: the loading page stays. After: the app opens on the empty library after ten seconds;
   reload: it opens at once, and not as a first run. The desktop behaves the same way with the same change, which is
   quicker to try (`./gradlew :app:desktop:run` with an empty library folder and no `preferences.json`).
2. Normal first visit: the demo songs are there, as before, with no added delay.
3. With the same temporary change, Settings → Library → "Add the demo songs": after ten seconds the "import failed"
   message, and the row is enabled again.
4. Compile: `./gradlew :app:web:wasmJsBrowserDistribution :app:desktop:compileKotlin`.

## Docs
`presentation/CLAUDE.md`, the `ui/DemoLibrary.kt` bullet, after "…an empty library would then be announced a moment
before the songs arrived.": add "Reading the bundled files is given ten seconds (`readDemoLibrary`): on the web they
are requests to the site, and one that never answered held the launch screen, and the web's loading page, for good."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 10, 17, 33 and 34 edit other functions of the same file.
