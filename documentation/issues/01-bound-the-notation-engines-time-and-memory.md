<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Bound what one tune may cost the notation engine: a length, voice, and system cap on every platform, and QuickJS's memory limit and timeout with a fresh engine after any failed render

**Kind:** robustness (hostile input) · **Severity:** medium · **Platforms:** all (engine limits: Android and desktop)
**Challenged:** amended — the length cap does not bound time: the number of voices multiplies it (a 6,000-character
tune naming 400 voices in `%%score` takes 5 s in QuickJS, 1,000 voices over 50,000 characters 15 s in node), so a
voice cap (32) joins it, and a system cap (2,000) bounds what the app itself parses and paints (one note a line gives
25,000 systems, 10 MB of SVG); a null result is checked in the engine (quickjs-kt returns it, it does not throw, so
the plan's `try` never saw it); the timeout is 10 s, not 5 s (legitimate-shaped 50,000-character text takes up to
2.1 s on the desktop); the timeout test uses 1,000 ms and a slow tune (50 ms can fail abc2svg's own start on CI); the
comment on cancellation is made consistent with quickjs-kt interrupting a cancelled render.
**Files:** `abc/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/abc/implementation/AbcRendererImpl.kt`,
`abc/implementation/src/jvmSharedMain/kotlin/com/pandulapeter/campfire/abc/implementation/QuickJsAbcScriptEngine.kt`,
`abc/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/abc/implementation/AbcRendererImplTest.kt`,
new `abc/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/abc/implementation/QuickJsAbcScriptEngineTest.kt`,
`abc/implementation/CLAUDE.md`

## Problem

A tune is untrusted (a shared link's import, a pasted or imported file), and the import preview renders it before
anything is written. `%%beginjs` is ignored since 8a507d1f8, so a tune can no longer run a script of its own, but nothing
bounds what abc2svg itself spends on one:

- **Memory grows with the tune, without limit.** Measured with quickjs-kt 1.0.15 on the desktop (Apple silicon), a
  bar of dense eighth notes (`"D"d2fd AdfA|`, 13 characters) costs abc2svg about 12–20 KB of QuickJS heap while
  rendering: 2,000 bars fail under a 16 MB limit and pass under 32 MB, 3,000 bars need more than 32 MB, 5,000 bars
  more than 64 MB (128 MB passes, ~550 ms), and 20,000 bars (~260 KB of text) fail even under 128 MB and took 2.2 s
  under 256 MB. In node the same 20,000 bars grew the heap by 337 MB. On Android that is native memory a few hundred
  kilobytes of shared text can make the process allocate, inviting a low-memory kill; on iOS a jetsam kill.
- **Time grows with the voices times the systems, not with the text.** abc2svg lays every staff out in every system,
  so a short tune that names many voices and writes into one of them is slow: measured in QuickJS on the desktop,
  `%%score 1 2 … 400` and ~6,000 characters of `[V:1]C|` lines take 5.0 s, `%%score 1 … 1000` and ~10,000 characters
  17 s; in node (a JIT engine, as JavaScriptCore and the browsers are) 1,000 voices over 50,000 characters take 15 s.
  On the web that is the page's main thread frozen, on iOS the `abc2svg` thread held; voices declared by `V:` lines
  cost the same.
- **What comes back can be huge too.** One note a line (`C\n` repeated) is one system a line: 50,000 characters give
  ~25,000 systems and 10 MB of SVG, which the app then parses, paints (plan 05: a path and text layouts per system),
  and lays out on the JVM heap.
- **Nothing stops a render that does not end.** `QuickJsAbcScriptEngine` runs every render on its one `abc2svg`
  thread, so a render that loops (an abc2svg bug a crafted tune finds) holds that thread for good, and every later tune
  of the session queues behind it and stays text.

```kotlin
// QuickJsAbcScriptEngine.kt
private val dispatcher = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "abc2svg").apply { isDaemon = true } }
    .asCoroutineDispatcher()
...
private suspend fun start() = QuickJs.create(dispatcher).apply {
    try {
        function("hostRenderInput") { source }
```

quickjs-kt has both bounds, off by default (`QuickJs.memoryLimit` and `QuickJs.evaluationTimeoutMillis`, both `-1`).
Their real behaviour, probed against the bundled `campfire-abc-host.js` and `abc2svg-1.js` (a Java harness over the
1.0.15 jar, in the review's scratchpad):

- `evaluationTimeoutMillis = 2000` stops `while (true) {}` after 2,001 ms with `QuickJsInterruptedException`
  ("InternalError: interrupted"); it is per evaluation. The context stays usable: `1+1` and a reel render right after.
- `memoryLimit` stops an allocation loop with `QuickJsException` ("InternalError: out of memory"), and a string
  doubling loop ("string too long") and unbounded recursion ("stack overflow") fail on their own.
- **But a render that runs out of memory inside abc2svg poisons the context**: every later evaluation, the small reel
  included, fails with "out of memory" until `gc()` is called, and even after `gc()` the context kept up to 111 MB the
  failed render built (abc2svg's state lives in globals until the next render replaces it). So after a tripped limit
  the engine must be closed and started again, not reused.
- **Under memory pressure a render can come back as `null` instead of throwing**: `evaluate<String>` is inline and
  reified, and its `castValueOr` returns a `null` result as it is (read from the jar's bytecode), so the `null` leaves
  the engine without an exception and only fails later, in `AbcRendererImpl`'s `output.startsWith` (an NPE), outside
  any `try` in the engine.
- **A cancelled render is interrupted too**: `evaluate` hangs an `invokeOnCompletion` on a child of the caller's job
  that calls `requestInterruptIfOpen()` on cancellation, then throws a `CancellationException` (`ensureActive`) rather
  than the interruption. `rememberSongNotations` cancels the render under way whenever the request changes (every
  keystroke in the editor's preview), so interrupted renders are routine, and the next one renders.

A render that trips nothing is unaffected: a normal tune renders in ~1–5 ms warm, the engine and abc2svg take ~2.4 MB,
and starting abc2svg in a fresh context takes 13–41 ms (the first start of a process also loads the native library).

Measured at 50,000 characters in QuickJS on the desktop (the smallest of 64/96/128/192 MB that rendered, and the time):
dense bars 64 MB, 0.4 s; plain notes, grace notes, tuplets 96 MB, 0.4–0.5 s; 80 one-line tunes 64 MB, 1.5 s; one note a
line more than 128 MB, 2.1 s; 32 voices over 50,000 characters 5.0 s (node: 0.63 s).

## Fix

Two layers, the first platform-independent.

1. **Caps in `AbcRendererImpl`**, all in its companion object with a KDoc each saying what it bounds and that the
   limits in `QuickJsAbcScriptEngine` sit above them. A tune over a cap comes back as `AbcRendering.Failed(<what it
   exceeded>)` (the caller keeps its text, as for any failure); measure `tune`, not the header put in front of it.
   - **`MAX_TUNE_LENGTH` = 50,000 characters**, before the engine is asked (about 3,800 dense bars, 64–96 MB in
     QuickJS; a long real tune is a few hundred bars, a medley a few thousand characters; ABC files are imported one
     song a tune). It bounds memory, not time.
   - **`MAX_TUNE_VOICES` = 32 distinct voice names**, before the engine is asked: the names of `V:` fields (at a line's
     start), of inline `[V:` fields, and the names listed on a `%%score` or `%%staves` line (or `I:score` /
     `I:staves`), since abc2svg creates every voice those lines name. A name is the run of characters after `V:` up to
     whitespace or `]`, and on a score line every run of characters other than whitespace and `()[]{}|*`. Regexes with
     `(?m)`; no `{`/`}` outside a character class (Android's ICU, see `check_regexes.py`). Real scores rarely pass 20
     voices; 32 voices over 50,000 characters take 0.63 s in node, so this bounds the main-thread freeze on the web and
     the hold on iOS's thread to under a second on a desktop, a few on a phone.
   - **`MAX_SYSTEMS` = 2,000 systems**, after the engine answers and before any is read: counted as the separators
     plus one. It bounds what the app parses, paints, caches (plan 06), and lays out; a few-hundred-bar tune is a
     couple of hundred systems even at a phone's width, 80 real tunes about 600.
2. **QuickJS limits, and a fresh engine after any failed render** (`QuickJsAbcScriptEngine.kt`):
   - Set them in `start()`, right after `QuickJs.create(dispatcher)` and before the host and abc2svg are evaluated:
     `memoryLimit = MEMORY_LIMIT_BYTES` (recommended **128 MB**: what the caps let through measured at most 96 MB in
     a realistic shape) and `evaluationTimeoutMillis = EVALUATION_TIMEOUT_MILLIS` (recommended **10,000 ms**: the
     slowest realistic shape the length cap lets through takes ~2 s on the desktop, and Android is a few times slower;
     a looping render then holds the queue for 10 s once, after which the engine is new).
   - In `render`, evaluate as `evaluate<String?>` and treat a `null` result as a failure inside the engine
     (`?: error("abc2svg returned no text")`), and wrap it in a `try`: on any `Throwable` other than
     `CancellationException`, close the engine and set `quickJs = null` before rethrowing, so the next render starts a
     new one. Catch everything rather than only `QuickJsInterruptedException` and an "out of memory" message: a
     render that throws at all is rare (no prefix of a typed tune threw in a probe of all 143 prefixes of a reel with
     voices, decorations, and lyrics; `K:H` does), so a restart on it costs nothing a reader notices.
   - Keep the engine on a `CancellationException`: quickjs-kt interrupts a cancelled render the same way a timeout
     does, the context renders the next tune after it (probed), and the editor's preview cancels a render on every
     keystroke, so restarting there would start abc2svg again for each. (Should a cancellation race a render that ran
     out of memory, the next render fails with "out of memory" and that failure restarts the engine.)
   - Comment the constraint in the present tense: a context that ran out of memory fails every later evaluation and
     can answer `null` instead of throwing, so it is replaced after any failure, but not after a cancellation, which
     is routine and leaves it usable.
   - To let a test use small limits, make the class `internal` with a constructor taking the two values
     (`memoryLimitBytes: Long`, `evaluationTimeoutMillis: Long`); `createAbcScriptEngine()` passes the constants. It is
     no Koin definition, so the constructor rule about defaults does not apply, but give it no defaults anyway.

**iOS (JavaScriptCore)**: the public `JSContext` API has neither a time nor a memory limit;
`JSContextGroupSetExecutionTimeLimit` exists only in `JSContextRefPrivate.h`, private API the App Store rejects. The
caps are the defence; a looping abc2svg there would hold the `abc2svg` thread as on the JVM today, which an
abandoned-thread watchdog could only hide while burning a core, so do nothing more.
**Web**: the render is synchronous on the page's main thread and cannot be interrupted from Kotlin; only a Worker could
be terminated, a change of architecture out of this plan's scope. The caps are the defence.

**`abc/implementation/CLAUDE.md`**: under "A tune is untrusted", add that a tune over `MAX_TUNE_LENGTH` characters or
`MAX_TUNE_VOICES` voices is never rendered, and a rendering of more than `MAX_SYSTEMS` systems is dropped (memory grows
with the text, time with the voices times the systems, and iOS and the web have no engine limits); under Engines, that
QuickJS runs under a memory limit and a per-evaluation timeout and is closed and started again after any failed render
(a context that ran out of memory fails every later evaluation, or answers `null`), never after a cancellation, which
quickjs-kt turns into an interruption of the render.

## Tests

- `AbcRendererImplTest` (commonTest, every platform's engine; no test renders anything slow, since the caps answer
  before the engine is asked or count its output):
  - `` `a tune longer than the renderer takes fails without being rendered` ``: a `REEL` header followed by enough bars
    to pass `MAX_TUNE_LENGTH` gives `AbcRendering.Failed`, and `REEL` renders right after. Build the text from a
    repeated bar, not a copied constant value.
  - `` `a tune naming more voices than the renderer takes fails without being rendered` ``: `X:1`, a `%%score` line
    naming `MAX_TUNE_VOICES + 1` voices, `K:C`, `C|` gives `Failed`; the same with `MAX_TUNE_VOICES` names renders
    (measured: 33 voices draw).
  - `` `a rendering of more systems than the renderer takes fails` ``: `X:1`, `L:1/8`, `K:C`, then `C` on
    `MAX_SYSTEMS + 1` lines of its own (one system a line: 2,001 lines gave 2,002 systems) gives `Failed`.
- New `QuickJsAbcScriptEngineTest` (desktopTest, since the limits exist only in QuickJS), constructing the engine with
  small limits:
  - `` `a tune that runs out of memory fails and the next one renders` ``: limit 16 MB, timeout 10,000 ms, a 2,000-bar
    tune (fails, measured) throws from `render` (a thrown error or the `null` check), then the reel renders (output not
    starting with NUL).
  - `` `a render past its time is stopped and the next one renders` ``: limit 128 MB, timeout **1,000 ms** (well above
    abc2svg's 13–41 ms start, which the limit also covers, so a slow CI machine cannot fail the start), the 400-voice
    tune above (`%%score 1 … 400`, then `[V:1]C|` lines to ~6,000 characters: 5 s measured; the engine test bypasses
    `AbcRendererImpl`'s caps) throws within a few seconds, then the reel renders.
- The existing `` `a failed tune leaves the engine working` `` keeps passing (now through a restart).

Run `./gradlew :abc:implementation:desktopTest`.
