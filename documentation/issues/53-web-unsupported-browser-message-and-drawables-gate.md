# 53 · Web: a browser that cannot run the app is told to check its connection, and one icon that fails to download keeps the loading screen up forever

**Severity:** minor — wrong behaviour (web; every visit from Safari before 18.2 / iOS before 18.2, Firefox before 120, Chrome before 119) and a hang (web; one failed request among fifty-one on a flaky connection) · **Area:** `app/web` (`index.html`), `:presentation` (`ui/platform/DrawablePreload.wasmJs.kt`, `DrawablePreload.kt`)

## Symptom
1. Open the web build on an iPhone that is still on iOS 17, or in an old Firefox ESR. The 16 MB download runs, the
   bar reaches the end, and the page says "Campfire could not be loaded. Check the connection and try again." The
   connection is fine, and **Try again** downloads everything again and says the same.
2. Open the web build on a train. The binaries arrive, but one of the fifty-one icon requests that follow is
   dropped. The loading page sits at about 99 % for good: no message, no retry button, and the app — fully started
   behind it — is never uncovered. Only a manual reload helps.

## Cause
**1.** Kotlin/Wasm output needs the garbage collection proposal and exception handling; a browser without them
throws a `WebAssembly.CompileError`, first of all from the small helper modules `campfire.js` instantiates while it
is being evaluated. `app/web/src/wasmJsMain/resources/index.html:248-263` has one answer to every failure:

```js
function showFailure() {
    …
    message.textContent = 'Campfire could not be loaded. Check the connection and try again.';
```

**2.** `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/DrawablePreload.wasmJs.kt:32-41`
counts the drawables whose preload state is still `null`, and `CampfireApp.kt:216-226` keeps the launch screen — and
with it `onAppReady`, `window.campfireReady()` and the loading page — until that count is zero:

```kotlin
Res.allDrawableResources.forEach { (name, drawable) ->
    if (key(name) { preloadImageVector(drawable).value } == null) pending++
}
return pending == 0
```

The reviewer could not say what a failed request does to that state; the library's source can (Compose resources
1.12.0, `webMain/.../ResourceState.web.kt`, which `preloadImageVector` → `vectorResource` goes through):

```kotlin
scope.launch(start = CoroutineStart.UNDISPATCHED) {
    try {
        mutableState.value = block(environment)
    } catch (_: Exception) {
        //resource loading failed
    }
}
```

A failed fetch (`DefaultWasmResourceReader.readAsBlob` turns a rejected `fetch` and a non-2xx answer alike into
`MissingResourceException`) is swallowed, the state is remembered at its default and nothing ever asks again. So
`null` means "not yet" and "never" alike, `pending` stays at one, and the gate never opens. Nothing reaches
`window`'s `error` or `unhandledrejection`, so the loading page's own failure message cannot help either. **The hang
is confirmed**, not merely possible.

## Fix

### The unsupported browser — `app/web/src/wasmJsMain/resources/index.html`
Written on top of plan 49, which replaces the static `<script src="campfire.js">` tag with a `startApp()` that adds
the tag from script, and introduces the `TEXTS` table (English and Hungarian, picked by `navigator.language`). Both
are needed here: a page with a static tag cannot decide *not* to download the app.

1. Add one entry to each language of `TEXTS`:

   ```js
                unsupported: 'This browser cannot run Campfire. It needs Chrome or Edge 119, Firefox 120, Safari 18.2 (iOS 18.2), or a later version.',
   ```
   ```js
                unsupported: 'Ez a böngésző nem tudja futtatni a Campfire-t. Chrome vagy Edge 119, Firefox 120, Safari 18.2 (iOS 18.2) vagy ezeknél újabb verzió kell hozzá.',
   ```

   The versions are the ones that shipped Wasm GC, which is the youngest thing the binary needs. iOS is named
   because every browser there is Safari underneath, so "use Chrome instead" is not a way out on an old iPhone.

2. Add, next to `showFailure()`:

   ```js
        /**
         * Whether the browser can compile what Kotlin/Wasm produces. Each probe is the smallest module that uses the
         * feature, and validating one compiles and downloads nothing, so this costs well under a millisecond.
         *
         * Garbage collected types: "\0asm", version 1, then a type section (id 1, 5 bytes) declaring 1 type - a
         * struct (0x5f = 95) with 1 field, an i8 (0x78 = 120) that is immutable (0).
         *
         * Exception handling, in the original try / catch form the binary uses: a type section with the function
         * type () -> () (0x60 = 96), a function section with one function of that type, and a code section (id 10)
         * whose one body is: no locals, try (0x06) with no result (0x40 = 64), catch_all (0x19 = 25), end, end (11).
         */
        function isBrowserSupported() {
            try {
                return typeof WebAssembly === 'object'
                    && WebAssembly.validate(new Uint8Array([0, 97, 115, 109, 1, 0, 0, 0, 1, 5, 1, 95, 1, 120, 0]))
                    && WebAssembly.validate(new Uint8Array([0, 97, 115, 109, 1, 0, 0, 0, 1, 4, 1, 96, 0, 0, 3, 2, 1, 0,
                        10, 8, 1, 6, 0, 6, 64, 25, 11, 11]));
            } catch (error) {
                return false;
            }
        }

        /** No retry button: trying again is what cannot help here, and it costs the whole download each time. */
        function showUnsupported() {
            isFailed = true;
            track.hidden = true;
            message.textContent = text.unsupported;
            message.hidden = false;
        }
   ```

   Both byte arrays were checked with `WebAssembly.validate` in Node 24 (`true`, `true`; a copy of the first with
   the struct opcode changed to `94` is `false`). That the binary uses the original exception handling was read off
   a development build of `campfire.wasm`: about 26 000 `catch` (`07 00`) and `delegate` instructions, no
   `try_table`, and the tag is imported from JavaScript (`WebAssembly.JSTag`). Should a later Kotlin switch the
   target to the final proposal (`-Xwasm-use-new-exception-proposal`, or a new default), the second probe becomes
   `[0, 97, 115, 109, 1, 0, 0, 0, 1, 4, 1, 96, 0, 0, 3, 2, 1, 0, 10, 8, 1, 6, 0, 31, 64, 0, 11, 11]` — the same
   module with `try_table` (0x1f = 31), no result, 0 catch clauses, in place of `try … catch_all` (validated in Node
   26) — and the versions in the message move to Chrome 137, Firefox 131 and Safari 18.4. Rebuild and grep once per
   Kotlin upgrade; `app/web/CLAUDE.md` gets a line saying so (Docs).

3. Replace the last statement of the script, which plan 49 leaves as `claimLibrary(startApp, showAlreadyOpen);`:

   ```js
        // Asked before the library is claimed and before anything of the app is fetched: a browser that cannot run
        // it would otherwise download sixteen megabytes to find that out, and be told to check its connection.
        if (isBrowserSupported()) {
            claimLibrary(startApp, showAlreadyOpen);
        } else {
            showUnsupported();
        }
   ```

Deliberately not done: treating a `WebAssembly.CompileError` caught by the `error` / `unhandledrejection` listeners
as "unsupported". With the probe in front, a compile error that still happens is far more likely a download that a
proxy cut short or rewrote, for which "could not be loaded, try again" is the right advice. `typeof WebAssembly`
being `undefined` (iOS Lockdown Mode turns it off) lands on the unsupported message, which is close enough to true.

### The drawables gate — `:presentation`
4. **`presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/DrawablePreload.wasmJs.kt`**
   — the wait gets a deadline and fails open:

   ```kotlin
   @OptIn(ExperimentalResourceApi::class)
   @Composable
   internal actual fun areDrawablesLoaded(): Boolean {
       var pending = 0
       Res.allDrawableResources.forEach { (name, drawable) ->
           // Keyed by name rather than left to the position in the map, so that a drawable added or removed later
           // cannot shift the preload of every drawable after it onto somebody else's remembered state.
           if (key(name) { preloadImageVector(drawable).value } == null) pending++
       }
       // A request that failed looks exactly like one that has not been answered: Compose resources swallows the
       // failure and leaves the state where it was, for good. So the wait ends by itself, and an app with an icon
       // missing - which the screen that draws it asks for again, in a request of its own - is a far better place to
       // be than a loading page that never goes away over an app that has started.
       val hasWaitedLongEnough by produceState(initialValue = false) {
           delay(PRELOAD_DEADLINE_MILLIS)
           value = true
       }
       return pending == 0 || hasWaitedLongEnough
   }

   /**
    * Counted from the first composition, by which time the binaries are in and the requests are all on their way
    * together: fifty files of a few hundred bytes each are a second's work on a slow connection, so this is only
    * ever reached by a request that is not coming back.
    */
   private const val PRELOAD_DEADLINE_MILLIS = 5_000L
   ```

   New imports: `androidx.compose.runtime.getValue`, `androidx.compose.runtime.produceState`,
   `kotlinx.coroutines.delay`. Add one sentence to the function's KDoc: "The wait is bounded, see
   [PRELOAD_DEADLINE_MILLIS]: a drawable that cannot be fetched must not be what keeps the app covered."

   Why failing open is safe: `painterResource` on the screen that needs the missing icon goes through its own
   `rememberResourceState`, which starts a new fetch (`ResourceWebCache` only stores successful answers), so the
   icon is retried when it is wanted and the only cost is the layout jump the preload exists to avoid — for that one
   icon. `areDrawablesLoaded()` is only composed while the launch screen is up (`if (!isAppReady)` in
   `CampfireApp`), so the `produceState` leaves the composition with it.

5. **`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/DrawablePreload.kt`** —
   the contract changes from "every icon is in memory" to "…or has been waited for long enough". First sentence of
   the KDoc becomes: "Whether the icons of the app are in memory — or have been waited for as long as they are
   worth — so that the first `painterResource` asking for one is answered with the icon itself rather than with a
   placeholder." and, at the end of the second paragraph: "A fetch that fails is never reported by Compose
   resources, so the web actual stops waiting after a few seconds rather than hold the launch screen for a file that
   is not coming." The other three actuals (`= true`) and `CampfireApp.kt` do not change.

Do **not** replace the preload with a hand-written `fetch` of the drawables to be able to see failures: the point of
`preloadImageVector` is that it fills the cache `painterResource` reads, which nothing else can do. And do not move
the deadline into `CampfireApp`'s `LaunchedEffect`: the other three conditions there are about the app's own data
and must never time out into an empty library.

## Tests
None (`index.html` and `:presentation` are untested).

## Verify
`./gradlew :app:web:wasmJsBrowserDevelopmentRun`.
1. In the DevTools console of a current browser, paste the two `WebAssembly.validate(new Uint8Array([...]))` calls:
   both `true`.
2. Simulate an old browser: temporarily change the first probe's `95` to `94` (not a type), reload: the unsupported
   message appears at once, no progress bar, no button, and the network tab shows neither `campfire.js` nor a
   `.wasm`. With the browser language set to Hungarian the Hungarian text. Undo the edit. If an iOS 17 simulator or
   an old Firefox (`< 120`) is at hand, open the page there for the real thing.
3. Drawables: in DevTools → Network → request blocking, block `*ic_update.xml` (any one drawable), reload. The
   loading page goes away about five seconds after the bar reaches the end, and the app works; the blocked icon is
   missing where it is used. Unblock it and navigate to a screen that uses it: it appears.
4. Without blocking: the app is uncovered as quickly as before (the deadline is not what opens the gate — check by
   raising it to 60 s once and seeing no difference).
5. `./gradlew :app:web:wasmJsBrowserDistribution :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64`
   (the KDoc change in `commonMain` is the only thing the other targets see).

## Docs
- `app/web/CLAUDE.md`, the `index.html` bullet: before anything is downloaded the page validates two tiny modules
  (`isBrowserSupported`) — Wasm GC and the original exception handling, which is what the Kotlin version in use
  emits — and a browser that fails gets its own message with no retry button instead of "check the connection"; the
  probe has to follow the compiler if it moves to `try_table` (check `campfire.wasm` after a Kotlin upgrade).
- `presentation/CLAUDE.md`, where `onAppReady` and the launch screen are described: add that the launch screen also
  waits for the web build's drawables (`areDrawablesLoaded`), for at most five seconds, because Compose resources
  never reports a drawable that failed to load.
- Root `CLAUDE.md`, Web section, the loading screen bullet: add "A browser without Wasm GC is told so before the
  download starts."

## Touches
- `app/web/src/wasmJsMain/resources/index.html`
- `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/DrawablePreload.wasmJs.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/DrawablePreload.kt`
- `app/web/CLAUDE.md`
- `presentation/CLAUDE.md`
- `CLAUDE.md`

## Depends on
49 (the `TEXTS` table and the scripted `startApp()` in `index.html`; without it the static `<script>` tag starts the
download whatever the probe says — if 53 has to land alone, take steps 5–6 of plan 49 minus the lock with it). The
drawables half (steps 4–5) depends on nothing.
