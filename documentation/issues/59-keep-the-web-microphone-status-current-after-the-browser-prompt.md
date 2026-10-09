# Keep the web's microphone status current once the browser's prompt is answered

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** web
**Files:** `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/MicrophonePermission.wasmJs.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CLAUDE.md`

## Problem

On the web the status comes from `navigator.permissions.query`, kept in a file-level `lastKnownStatus` that only a
query changes — once as the app starts (`CampfireWebApp.kt:54`) and on every resume
(`MicrophonePermission.wasmJs.kt:29-38` and `:52-60` at b5c8ed3b5):

```kotlin
var status by remember { mutableStateOf(lastKnownStatus) }
var checks by remember { mutableStateOf(0) }
LifecycleResumeEffect(Unit) {
    checks++
    onPauseOrDispose { }
}
LaunchedEffect(checks) { status = refreshMicrophoneStatus() }
…
private var lastKnownStatus = MicrophoneStatus.UNKNOWN
```

The flow, verified against `tunerNoticeOf` (`tuner/TunerNotice.kt:32-47`):

1. Fresh origin: the start query answers `"prompt"`, so `lastKnownStatus = NOT_ASKED`.
2. Tuner tab, "Use the microphone": `hasRequested = true`, the browser's prompt is granted, the tuner hears. The page's
   `status` is still `NOT_ASKED` (answering a prompt is no resume), but `NOT_ASKED.takeUnless { hasRequested && listening
   !is Stopped }` keeps the notice away while it listens, so nothing shows.
3. Songs tab, then back to Tuner. Leaving stopped the tuner (`listening = Stopped(reason = null)`). The new composition
   starts from `lastKnownStatus` — still `NOT_ASKED` — with `listening` stopped, so `tunerNoticeOf` answers
   `NOT_ASKED` and `canListenWithoutTap(NOT_ASKED, …)` is false: **the first frame shows the "Campfire listens through
   the microphone…" notice**. Then the resume query resolves `"granted"`, the notice animates away, the display
   expands in and the tuner starts.

That is a state starting on a wrong value and animated over, which the root `CLAUDE.md` forbids ("fixed at its source
so the first frame is already right"); the `lastKnownStatus` KDoc states the same intent.

## Fix

Options:
- **A (recommended): follow `PermissionStatus.onchange`.** The browser fires `change` on the `PermissionStatus` object
  a query returned the moment its prompt is answered, and also when the user changes the site setting by hand. Keep the
  status as snapshot state so a composition reads the current value, and subscribe once:

  ```kotlin
  /** … (the existing KDoc), followed by: Kept current between queries by the browser's change events. */
  private var lastKnownStatus by mutableStateOf(MicrophoneStatus.UNKNOWN)
  private var isWatchingStatus = false
  ```

  In `rememberMicrophonePermission`, drop the local `status` state and read `lastKnownStatus` directly
  (`val status = lastKnownStatus`), keeping the resume query (`LaunchedEffect(checks) { refreshMicrophoneStatus() }`)
  for browsers that never fire the event. In `refreshMicrophoneStatus`, after a successful answer, start watching once:

  ```kotlin
  if (!isWatchingStatus) {
      isWatchingStatus = true
      watchMicrophonePermission { state -> lastKnownStatus = microphoneStatusOf(state.toString()) }
  }
  ```

  with the `when` of `refreshMicrophoneStatus` moved into `private fun microphoneStatusOf(state: String)` so both read
  it, and

  ```kotlin
  private fun watchMicrophonePermission(onChange: (JsString) -> Unit): Unit = js(
      """(function () {
          try {
              if (!navigator.permissions || !navigator.permissions.query) return;
              navigator.permissions.query({ name: 'microphone' }).then(function (result) {
                  result.onchange = function () { onChange(result.state); };
              }, function () {});
          } catch (error) {}
      })()"""
  )
  ```

  The callback runs on the page's one thread; writing snapshot state from it is applied on the next frame. A Kotlin
  function type passed to a `js()` function is supported by Kotlin/Wasm (the window listeners in
  `MetronomeShortcutEffect.kt` pass Kotlin lambdas the same way).
- **B: mark the status granted once the tuner hears.** It is the same fact, but `rememberMicrophonePermission` cannot see
  the tuner's state; it needs a new `expect`/`actual` hook in all four source sets or a common-code rule in
  `tunerNoticeOf`. More code for the same result; only worth it if A turns out not to fire on a browser the app
  supports.

Docs: `ui/tuner/CLAUDE.md`, the microphone paragraph: "the web's `navigator.permissions.query`, asked as the app starts
(`CampfireWebApp`) so the tab's first frame knows, **and followed through its change events, so an answer given in the
browser's prompt is known by the next frame too**".

## Tests

None: a browser API binding. (The web's JavaScript tests in `app/web` cover its own scripts, not Kotlin `js()` blocks.)

## Manual check

Chrome and Firefox, a fresh profile (or the site's microphone permission reset): open the web build, Tuner tab, "Use
the microphone", allow in the prompt; it listens. Switch to Songs and back to Tuner: the display is there on the first
frame, with no notice fading in and out. Then block the microphone in the site settings next to the address with the
Tuner tab open: the refusal notice appears without reloading. Safari: the same steps; if the change event does not
fire there the behavior is today's (the resume query catches up) — note it rather than failing the check.
