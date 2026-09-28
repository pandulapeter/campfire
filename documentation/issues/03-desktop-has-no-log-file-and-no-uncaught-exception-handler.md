# Write the desktop app's diagnostics to a bounded log file and catch what nobody catches

**Kind:** robustness  ·  **Severity:** medium  ·  **Platforms:** desktop (macOS, Windows, Linux)
**Files:** app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt, a new `app/desktop/src/main/java/com/pandulapeter/campfire/DesktopLog.kt`, app/desktop/CLAUDE.md, root CLAUDE.md (library layout)

## Problem
The desktop shell installs no `Thread.setDefaultUncaughtExceptionHandler`, and every diagnostic it and the desktop
source sets write is a bare `println` (`SingleInstance.kt`, `CampfireDesktopApp.kt`, `SystemBrowser.desktop.kt`,
`SyncAuthenticator.desktop.kt`). An installed build is started from Finder, the Start menu or a `.desktop` entry, where
stdout goes nowhere, so nothing the app says survives anywhere on disk. `viewModelScope` is a `SupervisorJob` with no
`CoroutineExceptionHandler`, so an unexpected exception in one of `CampfireViewModel`'s launches (the expected
failures are wrapped and shown as snackbars; this is about the bug nobody anticipated) prints a stack trace to a
stderr nobody sees and leaves either a feature that silently stopped working or a window that no longer recomposes.

A user who hits that has nothing to attach to the GitHub issue that About links to, and the developer, with no crash
reporting by design, never learns the bug exists. Android and iOS at least have logcat and the system crash log; the
desktop has nothing.

## Fix
1. `DesktopLog`: a tiny logger writing timestamped lines to `<data directory>/campfire.log` (next to `instance.lock`,
   outside `library/` so it is never exported). Bounded: at start, if the file is larger than 1 MB, rename it to
   `campfire.log.1` (replacing the previous one) and start fresh. Append with a `PrintStream` opened once; every
   write in a `try` that falls back to `println`, since the log must never itself throw. Also redirect `System.err`
   and `System.out` into it (`System.setErr`/`setOut` with a stream that writes to both the original and the file),
   which captures the existing `println`s and Compose's own error output without touching them.
2. In `main()`, before the single-instance check, `Thread.setDefaultUncaughtExceptionHandler` that logs the thread
   name and stack trace through `DesktopLog`. Set `sun.awt.exception.handler` is not needed: since Java 7 AWT routes
   event-thread exceptions to the default handler.
3. `viewModelScope` cannot take a handler from the outside, so leave `CampfireViewModel` alone; the thread handler
   receives what its supervisor lets through.
4. Document the file in the library layout of the root `CLAUDE.md` and in `app/desktop/CLAUDE.md`, and mention it
   in the GitHub issue template if there is one.

No dialog: a Compose window that failed to recompose cannot show one, and a Swing dialog over a live window is
alarming for what is usually a harmless background failure. The log is what matters.

## Verification
Manual: run the debug desktop app with a temporary `throw IllegalStateException()` inside a `viewModelScope.launch`
(a menu action), confirm the stack trace lands in `campfire.log` with a timestamp, and that starting the app with a
2 MB log rotates it. Remove the throw. `./gradlew :app:desktop:run` and `createReleaseDistributable` still start.

## Conflicts
`main()` in `CampfireDesktopApplication.kt`, whose ordering comments (Swing globals before AWT, the instance lock
before Koin) must be kept; nothing else in this batch touches the desktop shell.
