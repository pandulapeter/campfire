# 24 · Desktop: Campfire opened again while the previous window is still closing either loses the file it was opened with or runs without the single-instance lock, so two processes can end up on one library

**Severity:** wrong behaviour with a data-loss path (desktop, Windows and Linux mostly — macOS' LaunchServices rarely
starts a second copy of a bundle; unlikely: the relaunch has to land in the fraction of a second between the window
closing and the process ending, e.g. closing Campfire and at once double-clicking a `.cho`) · **Area:** `:app:desktop`
(`SingleInstance.kt`)

**Verifier:** Second part changed: the listener is closed at the moment the app decides to exit (wrapping `exitApplication`), not after `application(exitProcessOnExit = false)` returns — that API exists in CMP 1.12.0 (`Application.desktop.kt:105-121`) and returns once the last window is gone and its effects are complete, but the window between the decision and that return is exactly the one that loses a file, and releasing the lock by hand before `exitProcess` would let a new process read the library while this one may still be writing; the lock is left to the OS, which frees it when the process is gone.

## Symptom
1. Close Campfire and immediately open it again (or open a ChordPro file with it).
2. The new window appears after about five seconds instead of at once.
3. That process never took the library lock and never listens for other instances. The next "open with" therefore
   finds the lock free (the old process is gone by now), becomes the owner, and opens a **second window on the same
   library** next to the first — the situation review 1's plan 49 put the lock in for: every repository reads its
   files once and writes them whole from memory, so the two quietly undo each other's saves.

Or, a little earlier in the same window: nothing opens at all, and the file the new process was started with is
lost — the closing process accepted it (see Cause).

## Cause
`claimSingleInstance` (`app/desktop/src/main/java/com/pandulapeter/campfire/SingleInstance.kt:50-70`) asks for the lock
once:

```kotlin
channel.tryLock() ?: run {
    channel.close()
    null
}
…
if (lock == null) return !handOver(dataDirectory, paths)
```

The exiting process still holds the lock, but its shutdown hook has already deleted `instance.endpoint` (`:87`) and
its listener thread is going with it, so `handOver` (`:137-157`) finds no file or a closed port on all 20 attempts
(20 × 250 ms, plus connect timeouts) and returns `false`, which `claimSingleInstance` turns into "carry on starting"
(`true`) — without the lock, which by then is free, and without `startListening`. The fail-open rule is a documented
decision for a holder that does not answer; the flaw is only that the lock is never asked for again while waiting.

The same window has a second outcome, the other way round: until the shutdown hook runs, the closing process's daemon
listener (`:89`, `serve` at `:110-130`) still accepts a hand-over and answers `OK`. Its `onActivated` queues the paths
for a window that no longer exists (`CampfireDesktopApplication.kt:50-53`), the new process exits as told, and the file
the user just opened is not opened by anybody. Nothing stops the listener
before the process is on its way out.

## Fix
The holder going away is the other way the wait can end, so ask for the lock on every attempt and become the
instance when it is granted. `claimSingleInstance` takes the retry loop over:

```kotlin
internal fun claimSingleInstance(dataDirectory: File, paths: List<String>, onActivated: (paths: List<String>) -> Unit): Boolean {
    val lockFile = File(dataDirectory, LOCK_FILE_NAME)
    repeat(HAND_OVER_ATTEMPTS) {
        // Asked again every time: the holder may be a process that is closing, and once it is gone this one is the
        // process that opens the library.
        val lock = try {
            dataDirectory.mkdirs()
            FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).let { channel ->
                channel.tryLock() ?: null.also { channel.close() }
            }
        } catch (exception: Exception) {
            println("Could not ask for the single instance lock: ${exception.message}")
            return true
        }
        if (lock != null) {
            heldLock = lock
            startListening(dataDirectory, onActivated)
            return true
        }
        if (sendPaths(dataDirectory, paths)) return false
        Thread.sleep(HAND_OVER_RETRY_MILLIS)
    }
    println("The running instance did not answer, starting next to it.")
    return true
}
```

`sendPaths` is today's `handOver` loop body (read the endpoint, connect, send, wait for `OK`), returning whether the
holder acknowledged and swallowing the same exceptions; `handOver` itself goes away. What matters: the lock is asked
for before each hand-over attempt, a granted lock goes through exactly the same `heldLock` + `startListening` path as
the first process, a refused lock's channel is closed each time, and both fail-open exits are unchanged. Update the
KDoc of `claimSingleInstance` (the `@return` paragraph) and move the retry explanation from `handOver`'s KDoc to it.

Second part, so that a closing instance stops accepting work it will never do: a new
`stopListeningForOtherInstances()` in `SingleInstance.kt` closes the `ServerSocket` (keep a reference to it next to
`heldLock`, set in `startListening`) and deletes `instance.endpoint`; it keeps the lock. Closing the socket ends
`serve`'s `accept()` with a `SocketException` (an `IOException`, already caught) and `isClosed` ends its loop. In
`CampfireDesktopApplication.kt`, inside `application { }`, define once

```kotlin
// From the moment the app decides to go, another process's files are not accepted any more: this one would only
// acknowledge them and exit. The lock stays until the process is gone, so a newcomer waits for it (claimSingleInstance).
val exit = {
    stopListeningForOtherInstances()
    exitApplication()
}
```

and use it in the three places that call `::exitApplication` today: `requestExit` (both branches, `:67`) and the
window's `onKeyEvent` (`:84`). `requestExit` only calls it once the unsaved-text question has been answered, so a
quit the user cancels keeps listening. `application { }` keeps its default `exitProcessOnExit = true`: there is
nothing to run after it. A process started in that moment finds no endpoint or a closed port, asks for the lock
again on its next attempt (first part), and gets it as soon as the OS has released it with the old process (well
within the 20 × 250 ms). Keep the existing shutdown hook as the fallback for a process that ends any other way.

Do **not** block on `channel.lock()` instead: a holder that is alive but hung would then keep the new process waiting
forever with no window.

## Tests
None (`:app:desktop` is untested; this is JVM-only code and could get a `desktopTest` later, but none exists for the
module).

## Verify
1. `./gradlew :app:desktop:createDistributable`, run the binary from `app/desktop/build/compose/binaries/main/app`.
2. Simulate a closing holder: in a scratch Kotlin/JShell snippet, open `instance.lock` in the data directory with
   `FileChannel.open(…).tryLock()`, keep it for 2 s without writing `instance.endpoint`, then release it; start
   Campfire during those 2 s. Before: it starts after ~5 s without the lock (a third launch opens a second window).
   After: it starts about 2 s later, and a following "open with" is handed to it (`--args` path imported in the same
   window).
3. Close the window and, within a fraction of a second, run the binary again with a `.cho` path (a shell loop that
   sends the close via `osascript`/`xdotool` and relaunches is enough): the song opens in the new window every time;
   before, some attempts ended with no window at all.
4. Normal cases unchanged: a second launch while Campfire runs hands its file over and exits; a read-only data
   directory still starts.

## Docs
`app/desktop/CLAUDE.md`, the "One process owns a data directory" paragraph: add "The lock is asked for again before
every hand-over attempt, so a process started while the previous one is still closing takes it over once it is free
rather than starting without it; and an instance that has decided to exit stops listening
(`stopListeningForOtherInstances`, called before `exitApplication`) while keeping the lock until it is gone, so it
never acknowledges files it will not open."

## Touches
- `app/desktop/src/main/java/com/pandulapeter/campfire/SingleInstance.kt`
- `app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt`
- `app/desktop/CLAUDE.md`

## Depends on
19, which adds `onPreviewKeyEvent` and a focus listener to the same `Window` call in `CampfireDesktopApplication.kt`:
apply after it (this plan changes the `onKeyEvent` line next to it and `requestExit`).
