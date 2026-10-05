# Resume the web audio context on every start, whatever its state reads

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** web (Firefox, Safari; not Chromium)
**Files:** metronome/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/metronome/implementation/AudioOutput.wasmJs.kt, metronome/implementation/CLAUDE.md

## Problem

`MetronomeImpl` closes the output and opens it again in one engine task whenever a click starts while the output is
held — most often right after a sound preview, which keeps the output open for `PREVIEW_HOLD_MILLIS` (1.5 s) after the
last tap: `start` calls `closeOutput()` → `WebAudioOutput.stop()` → `closeContext()`, then `output.start(...)` →
`openContext()`. In `AudioOutput.wasmJs.kt`:

```js
// closeContext()
if (metronome.context && metronome.context.state === 'running') metronome.context.suspend().catch(function () {});
```

```js
// openContext()
if (metronome.context.state !== 'running') metronome.context.resume().catch(function () {});
```

Whether `state` has changed by the time `openContext` reads it, synchronously after `suspend()`, is up to the browser.
Chromium sets it to `suspended` inside `suspend()`, so there the resume happens. Gecko and WebKit change it only when
the rendering thread reports back, in a later task (the Web Audio spec likewise updates the `state` attribute and fires
`statechange` from a queued task once the control message has run). There `openContext` still reads `'running'`,
skips `resume()`, and the queued suspend then completes: the click starts with its context suspended, and the loop in
`start` reports `MetronomeAudioIssue.WAITING_FOR_GESTURE` — the user just tapped Play and is told to tap again, and the
click is silent until they do.

## Fix

Call `resume()` unconditionally in `openContext()` (keeping the creation and the `try`):

```js
if (!metronome.context) metronome.context = new AudioContextType({ latencyHint: 'playback' });
metronome.context.resume().catch(function () {});
```

Control messages run in the order they are queued, so a `suspend()` followed by `resume()` always ends running, and
`resume()` on a running context just resolves. Nothing else changes: the engine task has the page's sticky user
activation by then (the tap that previewed or played), which is all Chromium and Gecko ask of a `resume()`, and where a
browser refuses, `WAITING_FOR_GESTURE` is still reported as today. Add a short comment saying why the state is not
checked (it may still read running while a suspend is on its way).

In `metronome/implementation/CLAUDE.md`'s Web bullet, add that a start always resumes the context, since a suspend
from the previous session's stop may still be in flight.

## Tests

None: browser audio glue in a `js(...)` block, outside the pure logic that is unit tested.

## Manual check

In Firefox (and Safari if available), open the deployed or dev-server web build, go to the Metronome tab, tap a sound
chip to preview it and, within a second, tap Play. Before the fix the click shows the "tap to allow sound" issue and
is silent until another tap; after it, the click is heard at once. Repeat Stop then Play quickly, and check Chrome
behaves as before.
