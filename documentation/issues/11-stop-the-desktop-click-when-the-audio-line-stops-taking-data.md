# Stop the desktop click as a lost output when the audio line stops taking data

**Kind:** bug (crash)  ·  **Severity:** medium  ·  **Platforms:** desktop (Linux most of all; Windows possibly)
**Files:** `metronome/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/metronome/implementation/AudioOutput.desktop.kt`,
`metronome/implementation/CLAUDE.md` (only if it describes the desktop output's failure handling)

## Problem

The desktop output's feed thread is paced only by `SourceDataLine.write` blocking, and only an exception ends it
(`AudioOutput.desktop.kt:58-69` at 8ee010b36):

```kotlin
try {
    while (this.line === line) {
        stream.renderPcm(samples, frames)
        ...
        line.write(bytes, 0, bytes.size)
    }
} catch (_: Exception) {
    if (this.line === line) listener.onLost(MetronomeStopReason.OUTPUT_FAILED)
}
```

`SourceDataLine.write` does not throw when the device fails. Its documented contract is that it returns the number of
bytes actually written, fewer when the line is stopped, flushed or closed; and the JDK's implementation
(`com.sun.media.sound.DirectAudioDevice.DirectDL.write`, checked in the OpenJDK source) breaks out of its loop and
returns what it has written so far when the native layer reports an error:

```java
thisWritten = nWrite(id, b, off, len, softwareConversionSize, leftGain, rightGain);
if (thisWritten < 0) {
    // error in native layer
    break;
}
...
return written;
```

On Linux the ALSA backend reports an error for a device that is gone (a USB headset or audio interface unplugged
while the click plays), so every later `write` returns 0 at once. The max-priority thread then spins: each pass
advances the mixer by one chunk of stream time, and `ClickStream.schedule` sends every tick into `renderedTicks`, a
`Channel.UNLIMITED` (`ClickStream.kt:34`). `MetronomeImpl.releaseHeardBeats` moves them into `pending`, which never
empties because `longFramePosition` no longer advances. One core runs at 100 % and memory grows by thousands of ticks
a second until the JVM runs out of heap, while the UI still shows the click as playing. The Android output already
treats a failed write as a lost output (`AudioOutput.android.kt:121`: `if (track.write(...) < 0 && this.track === track)`).

(On macOS the write more likely blocks forever on a vanished device, which is a silent click still shown as playing;
this plan does not cover that, and nothing in the JDK reports it.)

## Fix

Check what `write` returns. A short write can only otherwise happen after `stop()` has stopped, flushed and closed the
line, and `stop()` clears `this.line` first, which the identity check already tells apart:

```kotlin
while (this.line === line) {
    stream.renderPcm(samples, frames)
    for (index in 0 until frames) { ... }
    // A write that returns short without stop() having taken the line is the device failing: the JDK does not throw
    // for that, and a loop that went on would render the stream as fast as the processor allows.
    if (line.write(bytes, 0, bytes.size) < bytes.size) {
        if (this.line === line) listener.onLost(MetronomeStopReason.OUTPUT_FAILED)
        break
    }
}
```

`onLost` is what an exception already leads to, so the engine stops the click and says why, as it does on Android.
Keep the `catch` for the exceptions `write` does throw. If `metronome/implementation/CLAUDE.md` describes the desktop
output's failure path, add that a short write counts as a lost output.

## Tests

None: the output is platform code around `javax.sound.sampled`, which the project does not unit test.

## Manual check

On a Linux desktop (the `.deb`), start a click on the Metronome tab through a USB headset or USB audio interface, then
unplug it. The click stops and says the output failed, the CPU use of the process drops back, and its memory stays
flat. Repeat on Windows with a USB headset. Also check that stopping the click normally, paging to another song while
it plays and closing the song details screen still stop it without an "output failed" message.
