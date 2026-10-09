# Close the desktop input's reopen race that can leave an orphaned line holding the microphone

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** desktop (macOS above all, where the silent-line reopen is
what answers the permission prompt; Windows, Linux)
**Files:** `tuner/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/tuner/implementation/DesktopAudioInput.kt`,
`tuner/implementation/CLAUDE.md`

## Problem

A line that has heard only zeros for three seconds is closed and opened again by the capture thread itself, which then
publishes the new line with a plain write:

```kotlin
if (silentFrames >= REOPEN_AFTER_SILENT_FRAMES) {
    silentFrames = 0
    line.stop()
    line.close()
    line = open()
    if (this.line == null) {
        line.close()
        break
    }
    this.line = line
}
```

`open()` takes real time (tens to hundreds of milliseconds, longer while macOS shows its prompt). If, meanwhile,
`stop()` and `start()` both run — the user leaves the tuner tab and comes back, or the screen is recomposed through a
stop and a listen — then:

1. `stop()` sets `line = null` and closes line A (already closed by the thread).
2. `start()` opens line C, sets `line = C`, and starts thread 2 on C.
3. Thread 1's `open()` returns line B; `this.line == null` is false (it is C), so it sets `line = B`.
4. Thread 2's loop sees `this.line !== C` and breaks — without closing C, since nothing in `capture` closes the line it
   leaves. C stays open with nobody reading it, and the system's microphone indicator stays on for it until the process
   ends (the next `stop()` closes only B).

Thread 1 also keeps writing B's frames into the ring that `start()` just cleared for thread 2's session.

## Fix

1. Hold the current line in an `java.util.concurrent.atomic.AtomicReference<TargetDataLine?>` (this is `desktopMain`,
   so `java.*` is fine) instead of the `@Volatile var line`.
2. The reopen swaps only if the line is still the thread's own, and closes the new one otherwise:

   ```kotlin
   val reopened = open()
   // A stop, or a stop and a new start, may have come while the line was being opened: the new line then belongs to
   // nobody and is closed here, or it would hold the microphone with nothing reading it.
   if (!current.compareAndSet(line, reopened)) {
       reopened.close()
       break
   }
   line = reopened
   ```
3. Every capture thread closes the line it ends with, in a `finally`, whatever made it end (the loop condition, a
   failed read, an exception); `stop()`, `flush()` and `close()` on a line that `stop()` already closed are harmless:

   ```kotlin
   } finally {
       line.stop()
       line.close()
   }
   ```
4. The loop condition and the post-read check become `current.get() === line`; the exception branch reports
   `FAILED` only `if (current.get() === line)`, as today. `stop()` becomes `val line = current.getAndSet(null) ?: return`
   followed by the existing stop/flush/close. `start()` sets the new line with `current.set(line)`.

The ring is shared by every thread; a stale thread writing into it after a new `start()` is bounded by the
post-read check (`if (current.get() !== line) break` before `ring.write`), which this keeps.

## Tests

None: `TargetDataLine` is platform audio, not pure logic (root `CLAUDE.md`). `./gradlew :app:desktop:run` must still
build and listen.

## Manual check

macOS, a build without microphone permission granted yet (`tccutil reset Microphone` for the app's bundle id or the
JVM), `./gradlew :app:desktop:run`:

1. Open the tuner; while the system prompt is up, switch to the Songs tab and back to the Tuner tab twice, then allow
   the microphone. Leave the tuner: the orange microphone indicator in the menu bar goes out within a second.
2. Normal use: open the tuner, play a note (read), leave it (indicator out), come back (read again).

## Docs

`tuner/implementation/CLAUDE.md`, the **Desktop** bullet: after "is opened again", add "(the new line swapped in only
if listening has not stopped or restarted meanwhile, and every capture thread closing the line it ends with)".
