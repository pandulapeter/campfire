# Load the desktop web view helper off the main thread, before anything asks whether pages can be read

**Challenged:** amended — a plain daemon thread (no unowned scope) whose body cannot throw; the KDocs that say when the check runs (`DesktopPageSource`, `PastedSongController`'s `@param`) are updated with it.

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** desktop
**Files:** `data/source/remote/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/page/DesktopPageSource.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/PastedSongController.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/PastedSongControllerTest.kt`,
`data/source/remote/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/page/DesktopPageSourceTest.kt`,
`data/source/remote/implementation/CLAUDE.md` (Desktop section) if it describes when the helper loads

## Problem

The view model asks at construction whether this platform reads pages (`CampfireViewModel.kt:527`):

```kotlin
        isPageLoadingSupported = importWork.useCases.canLoadPages(),
```

On the desktop that resolves `DesktopPageSource.delegate`:

```kotlin
    private val delegate by lazy {
        if (NativeWebView.isAvailable(logger)) OffscreenPageSource(...) else NoPageSource
    }
```

and `NativeWebView.isAvailable` runs `System.load` of the helper and its `isEngineAvailable()`: on macOS the dylib links
`WebKit.framework`; on Linux `isEngineAvailable` `dlopen`s `libwebkit2gtk` with `RTLD_NOW` (which brings GTK,
libsoup, and JavaScriptCore) and resolves every symbol; on Windows it asks for the installed WebView2 version. All of it
happens on the thread building the view model, before the first frame, on every launch, whether or not the Pages from
links switch is on and whether or not a page is ever read.

Measured once on an Apple Silicon Mac (a plain `System.load` of `app/desktop/build/webViewHelper/libcampfirewebview.dylib`
in a fresh JVM): **172 ms** on the first run after a while, **~3 ms** warm. Linux's `dlopen` of WebKitGTK is expected
to cost more than macOS's.

## Fix

The answer is still needed synchronously in composition (Settings → Features shows the Pages from links row only where
it is supported, and must be right on its first frame; the songs list's hint names links where they are read), so a
`StateFlow` that starts `false` is not the fix: the row would arrive late, which the UI rules forbid animating over.

**Recommended: start the check early on a background thread and ask for it late.**

1. `DesktopPageSource`: in `init`, start resolving `delegate` on a daemon thread:
   `thread(isDaemon = true, name = "Campfire web view check") { runCatching { delegate } }` (not a
   `CoroutineScope(Dispatchers.IO)`, which nothing would own). `by lazy` is synchronized, so a later `canLoadPages` on
   the main thread waits only for whatever is left, and in practice finds it done. `runCatching` keeps anything
   `isAvailable` does not catch (it catches `LinkageError` only) from printing as an uncaught exception on that thread;
   a `lazy` whose initializer threw stays uninitialized, so the main thread's read runs it again and sees the error
   there, as it does today. Rewrite the class KDoc's "which is asked once, on the first use": the check starts on a
   background thread as the source is created and is waited for by the first read that comes before it ends.
2. `PastedSongController`: take `isPageLoadingSupported: () -> Boolean` instead of a `Boolean`; read it where it is used
   (`newSongFromText`, and the `isPageLoadingSupported` the screens read, which becomes a getter or a function). The
   view model passes `{ importWork.useCases.canLoadPages() }`. Nothing reads it while the view model is built. Keep
   the screens' `pastedSongController.isPageLoadingSupported` reads as they are by making it a getter
   (`val isPageLoadingSupported: Boolean get() = canLoadPages()`, the lambda parameter renamed, e.g. `canLoadPages`), and say in the `@param` KDoc that it is asked only when read,
   since the desktop's answer may still be on its way while the view model is built.

Its first reader is then the Settings screen or the songs list's hint (which waits for the library to load), each well
after `DesktopPageSource` was created with the view model's graph.

Rejected: making `canLoadPages` a `suspend` or a flow through `PageSource` → repository → use case (four modules for a
desktop-only cost, and the first-frame problem above); checking only that the helper file exists (would offer the
switch on a Linux without WebKitGTK or a Windows without WebView2).

**Drop this plan if** loading costs under ~5 ms on macOS on a cold launch. Measure in the app, not in a bare JVM: wrap
`NativeWebView.isAvailable` in `measureTimeMillis` with a temporary `logger.log`, run `./gradlew :app:desktop:run`
(the helper is copied into the resources folder by `compileWebViewHelper`), and read the line in `campfire.log`; take a
launch after a reboot (or after `sudo purge`) and a warm one. Remove the timing before committing. The cold number
above suggests the premise holds.

## Tests

- `PastedSongControllerTest`: switch its `isPageLoadingSupported` var to the lambda form; add a test that building the
  controller does not call it and a paste of an address does.
- `DesktopPageSourceTest`: its existing test (a build without the helper answers `canLoadPages == false`) must still
  pass with the background start; add none for the thread.
