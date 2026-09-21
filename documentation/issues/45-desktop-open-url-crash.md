# 45 · Desktop: tapping a link in Settings closes the app where AWT's `Desktop` is not supported, and the `xdg-open` fallback can never run

**Severity:** crash (desktop — Linux sessions without the GNOME/GTK libraries AWT looks for, and any machine with no default browser registered; certain there, rare overall) · **Area:** `:presentation` `desktopMain` (`CampfireDesktopApp.kt`: `openUrl`), plus a message in `CampfireViewModel` / `CampfireApp`

## Symptom
1. Run the desktop build on a Linux machine with a minimal window manager (no `libgnomevfs` / GTK for AWT to load), or
   in a container.
2. Settings → any link: another build's store page, the author's site, GitHub, the issue tracker, the privacy policy,
   the donation page.
3. `UnsupportedOperationException` leaves the `onClick`. Compose Desktop's window exception handler shows an error
   dialog and closes the window — with whatever the editor held unsaved.

The same happens on any desktop where `Desktop.browse` is supported but fails (`IOException`: no default browser
registered, the launcher could not be started). And on Windows a machine in that state gets nothing at all: the
fallback only knows `open` and `xdg-open`.

## Cause
`presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireDesktopApp.kt:113-126`
(verified by the lead and again for this plan):

```kotlin
private fun openUrl(url: String) {
    try {
        val desktop = Desktop.getDesktop()                       // throws UnsupportedOperationException / HeadlessException
        val osName by lazy(LazyThreadSafetyMode.NONE) { System.getProperty("os.name").lowercase() }
        when {
            Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE) -> desktop.browse(URI(url))
            "mac" in osName -> Runtime.getRuntime().exec(arrayOf("open", url))
            "nix" in osName || "nux" in osName -> Runtime.getRuntime().exec(arrayOf("xdg-open", url))
            else -> println("Cannot open url: $url")
        }
    } catch (_: NoClassDefFoundError) {                          // the only thing caught
        println("Cannot open url: $url")
    }
}
```

- `Desktop.getDesktop()` is called *before* `Desktop.isDesktopSupported()` is asked, and it is documented to throw
  exactly where the answer is no. The two branches under it exist for that machine and are unreachable on it.
- `browse()` throws `IOException`, `SecurityException` and `IllegalArgumentException`; `URI(url)` throws
  `URISyntaxException`; `exec` throws `IOException` where `xdg-open` is not installed. None is caught, and none falls
  through to the next way of opening the link.
- Nothing tells the user when no way worked: there is **no existing message for it** (`CampfireViewModel.Message`
  has `ImportFinished`, `ImportFailed`, `ExportFailed`, `SaveFailed`, `OperationFailed`), only a `println`.

The two other places that ask `Desktop` do it in the right order and are the model: `CampfireDesktopApplication.kt:52`
(the quit handler) and `openInSystemBrowser` in
`data/source/remote/implementation/src/desktopMain/.../auth/SyncAuthenticator.desktop.kt:172`, which also knows the
Windows command. That one lives in another module and cannot be shared with `:presentation`; leave it alone.

## Fix
1. **`CampfireDesktopApp.kt`: replace `openUrl`** with the three functions below. It now answers whether anything took
   the link.

   ```kotlin
   /**
    * Opens [url] in the system's browser and answers whether anything took it. `java.awt.Desktop` goes first, and the
    * operating system's own command is what is left - both where AWT has no desktop to speak of, which is a Linux
    * session without the GNOME libraries it looks for, and where it has one that fails, as `browse` does on a machine
    * with no default browser registered.
    */
   private fun openUrl(url: String) = openWithAwt(url) || openWithSystemCommand(url)

   /** `getDesktop` throws where `isDesktopSupported` says no, so it is only asked for after that has said yes. */
   private fun openWithAwt(url: String) = try {
       val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop().takeIf { it.isSupported(Desktop.Action.BROWSE) } else null
       desktop?.browse(URI(url))
       desktop != null
   } catch (exception: Exception) {
       println("Could not open $url through java.awt.Desktop: ${exception.message}")
       false
   } catch (error: LinkageError) {
       // The desktop peer loads native libraries the first time it is asked for, and one that does not link is an
       // Error rather than an Exception. This runs inside a click handler, where either would close the window.
       println("Could not open $url through java.awt.Desktop: ${error.message}")
       false
   }

   private fun openWithSystemCommand(url: String) = try {
       val osName = System.getProperty("os.name").orEmpty().lowercase()
       val command = when {
           "mac" in osName -> arrayOf("open", url)
           "win" in osName -> arrayOf("rundll32", "url.dll,FileProtocolHandler", url)
           else -> arrayOf("xdg-open", url)
       }
       // Discarded rather than piped: xdg-open may become the browser itself, which then writes its log into a pipe
       // nobody reads and stops once that is full.
       ProcessBuilder(*command)
           .redirectOutput(ProcessBuilder.Redirect.DISCARD)
           .redirectError(ProcessBuilder.Redirect.DISCARD)
           .start()
       true
   } catch (exception: Exception) {
       println("Could not open $url: ${exception.message}")
       false
   }
   ```

   Notes for whoever applies it:
   - The `"mac"` branch stays first, so `"win"` never sees `"darwin"` (the JVM reports macOS as `Mac OS X` anyway).
   - The array form of the command is kept on purpose: the URL is one argument and never passes through a shell.
   - Do not wait for the process or read its exit code: this runs on the UI thread, inside `onClick`.
   - Do not move the call off the UI thread either; `browse` returns as soon as the request is handed over.
   - Imports: `java.awt.Desktop` and `java.net.URI` are already there; nothing new is needed
     (`ProcessBuilder` is `java.lang`).

2. **Tell the user when nothing could open the link** (separable from step 1 — step 1 alone already ends the crash,
   and until this step lands the call site is `urlOpener = { url -> openUrl(url) }`, since `::openUrl` no longer
   returns `Unit`).

   a. `presentation/src/commonMain/.../ui/CampfireViewModel.kt`, in `sealed interface Message`:

      ```kotlin
      /** A link nothing on this machine would open. The address is shown, since reading it is all that is left. */
      data class LinkNotOpened(val url: String) : Message
      ```

      and next to the other intent handlers that only report something:

      ```kotlin
      /** For the shells, which are what opens a link and so what finds out that nothing did. */
      fun onLinkNotOpened(url: String) {
          _messages.trySend(Message.LinkNotOpened(url))
      }
      ```

      `_messages` is a `Channel(BUFFERED)`, so `trySend` from a click handler is enough; no coroutine is needed.

   b. `presentation/src/commonMain/.../ui/CampfireApp.kt`, the `when (current)` that builds the snackbar text
      (around `:439`), add before `null -> null`:

      ```kotlin
      is CampfireViewModel.Message.LinkNotOpened -> stringResource(Res.string.error_link_not_opened, current.url)
      ```

   c. Strings, in the group that holds `error_operation_failed`, in both files:
      - `values/strings.xml`: `<string name="error_link_not_opened">Could not open %1$s</string>`
      - `values-hu/strings.xml`: `<string name="error_link_not_opened">Nem sikerült megnyitni: %1$s</string>`

   d. `CampfireDesktopApp.kt`, the `CampfireApp(...)` call:

      ```kotlin
      urlOpener = { url -> if (!openUrl(url)) viewModel.onLinkNotOpened(url) },
      ```

   The other three shells are not changed: the web's `window.open` and iOS' `openURL` have no failure this would
   describe, and Android already answers an `ActivityNotFoundException` with a toast (`CampfireActivity.openUrl`).
   None of the app's URLs contains a `%`, so plan 63 (percent signs in formatted strings) has nothing to say about
   this one.

## Tests
None (UI is untested).

## Verify
1. `./gradlew :app:desktop:run` on macOS or Windows: every link in Settings → About and in the builds list still
   opens the browser.
2. The fallback: on a Linux VM without the GNOME libraries (`Desktop.isDesktopSupported()` is false there) a link
   opens through `xdg-open` and the window stays open. Without such a machine, temporarily make `openWithAwt` return
   `false` and check that `open` / `rundll32` / `xdg-open` takes the link.
3. The failure: temporarily make both helpers return `false`. The snackbar reads "Could not open https://…" (in
   Hungarian with the app set to Hungarian) and the window stays open. Tap the link twice: two snackbars, one after
   the other. Undo the temporary change.
4. Compile everything, since `Message` gained a case every `when` over it must know:
   `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:compileKotlin`.

## Docs
`presentation/CLAUDE.md`, the `desktopMain/ui/CampfireDesktopApp.kt` bullet: "links open through `java.awt.Desktop`"
becomes "links open through `java.awt.Desktop`, or through the system's own command (`open`, `rundll32`, `xdg-open`)
where AWT has no desktop or its `browse` fails, and a link nothing would open is answered with a snackbar naming the
address (`Message.LinkNotOpened`)". Drop the last clause if step 2 is not done.

## Touches
- `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireDesktopApp.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt` (step 2)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt` (step 2)
- `presentation/src/commonMain/composeResources/values/strings.xml` (step 2)
- `presentation/src/commonMain/composeResources/values-hu/strings.xml` (step 2)
- `presentation/CLAUDE.md`

## Depends on
Nothing.
