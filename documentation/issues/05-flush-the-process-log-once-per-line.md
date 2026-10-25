<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Flush the process log to its file once per finished line instead of on every write

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** Android mainly (desktop and iOS too)
**Challenged:** amended — `FileProcessLogStore.read()` flushes inside a try that ignores the failure (a bare flush first
would throw on a broken stream and turn "what came before can still be read", `ProcessLogWriter.read`'s KDoc, into no
report at all); spelled out what is lost now that was not (an unfinished line at a kill or `exitProcess`) and that iOS
flushes once per pipe chunk, not per line. Crash paths, the `campfire.crash` marker, and the test checked: unchanged.
**Files:**
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/ProcessLogWriter.kt`
- `presentation/src/jvmSharedMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/ProcessLog.jvmShared.kt`
- `presentation/src/iosMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/ProcessLog.ios.kt`
- `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/ProcessLog.wasmJs.kt`
- `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/platform/ProcessLogWriterTest.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/CLAUDE.md`

## Problem

`FileProcessLogStore` (`ProcessLog.jvmShared.kt`) flushes its `BufferedOutputStream` on every append:

```kotlin
override fun append(bytes: ByteArray, offset: Int, length: Int) {
    stream.write(bytes, offset, length)
    stream.flush()
}
```

and `ProcessLogWriter.append` calls the store once for the time stamp and once per line piece, while the mirrored
`PrintStream` may hand one `println` over in several writes (text, then line separator, on Android's libcore). That is
two to three `write` syscalls per printed line, on the printing thread. Since 6daebd00d this runs on Android too, where
`logBreadcrumbs` (`ui/state/Breadcrumbs.kt`) and `MessageSink.launchLibraryChange` print on the main thread: disk writes
on the main thread (StrictMode disk-write noise, a little jank). The old desktop-only `DesktopLog` flushed per write as
well, so the desktop has always paid it. iOS's `PosixProcessLogStore.append` `fflush`es per append too (on its own
reader thread, so only the syscall count matters there).

## Fix

Options:

- **Flush once per finished line (recommended).** Each complete line still reaches the OS (page cache, which outlives
  the process) before `println` returns, so the guarantee the crash path relies on is unchanged: the uncaught exception
  handler in `installProcessLog` prints "Uncaught exception on …" and the stack trace with `println`s, every line ending
  in a newline, before calling the previous handler that ends the process on Android. One syscall per line instead of
  two to three.
- A single writer thread with a queue: no disk work on the main thread at all, but the crash handler must drain it
  before the previous handler kills the process, and a process killed without an exception (Android's low-memory or
  ANR kill, a force stop) loses whatever was queued. It weakens the guarantee and adds a thread; not worth it for one
  syscall per line.

Changes:

1. `ProcessLogStore` (in `ProcessLogWriter.kt`): add `fun flush()` with KDoc "Hands what was appended to the system, which
   the writer asks for at the end of every line." and update the interface KDoc if needed.
2. `ProcessLogWriter.append`: note whether the call wrote a newline (`isAtLineStart` after a piece) and call
   `store.flush()` once at the end of the call if it did, inside the same `guard`. A partial line (a `print` with no
   newline) stays buffered until its line ends; say in the class KDoc that every finished line is flushed, which is what
   keeps the lines before a crash in the file.
3. `FileProcessLogStore`: `append` only writes; `flush()` flushes the stream; `read()` first tries to flush, ignoring
   an `IOException` (as the iOS store's `read` ignores `fflush`'s result), so a report also holds an unfinished last line
   — but a stream that has failed, after which the writer is broken and has said so, must not cost the report the files
   already on disk, which `ProcessLogWriter.read` promises. `rotate()` already closes (and so flushes); rotation only
   happens at a line start, so it never strands a partial line.
4. `PosixProcessLogStore` (iOS): move the `fflush(file) != 0` check from `append` into `flush()`, keeping the
   `fwrite` length check in `append`. The reader thread appends whole pipe chunks (up to 4096 bytes, many lines), so iOS
   flushes once per chunk that ends a line, fewer than before. The uncaught exception hook's text ends in `\n`, so its
   `append` flushes before the marker is written and the previous hook runs.
5. `LocalStorageProcessLogStore` (`ProcessLog.wasmJs.kt`) already stores on its own schedule: implement `flush()` as a
   no-op there, or give the interface method an empty default body and leave the web
   untouched; prefer the explicit no-op, so a future store cannot forget it.
6. `ui/platform/CLAUDE.md`, the Android and desktop bullet under `ProcessLog.kt`: add "flushed once per finished line,
   so the lines before an uncaught exception are in the file".

What this gives up: an unfinished line (a `print` with no newline) still in the buffer when the process ends without
printing another line — Android's low-memory or ANR kill, a force stop, desktop's `exitProcess` (a JVM exit does not
flush a `BufferedOutputStream`), iOS jetsam — is lost, where every write reached the file before. The app logs with
`println`, so this is at most a fragment. The crash paths are unchanged: Android and desktop's handler writes
`campfire.crash` first and then prints with `println`s through the autoflushing `PrintStream` (libcore's newline write
is its own `append`, which flushes), and a pending fragment is flushed with the handler's first line.

## Tests

`./gradlew :presentation:desktopTest`, in `ProcessLogWriterTest`: give `MemoryStore` a `flushedText` (what `current` held
at each `flush()`) and add `every finished line is flushed once, and an unfinished one only once it ends`:
`append("Hel")` → no flush; `append("lo\nWorld\nAnd")` → one flush, holding `"[T] Hello\n[T] World\n[T] And"`;
`append(" more\n")` → a second flush. The existing tests keep passing unchanged (a failing
`MemoryStore` already throws on its first `append`, before any flush). The jvmShared store has no
test harness; its read-after-failure behaviour is the try in step 3, kept by review.
