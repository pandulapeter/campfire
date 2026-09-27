<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 46 — Count the web download on a clone so the browser can cache compiled WebAssembly

| | |
|---|---|
| Lane | F |
| Impact | medium (on returning visits in Chromium browsers) |
| Confidence | medium. Step 1 confirms it before anything is changed |
| Platforms | web |
| Files | `app/web/src/wasmJsMain/resources/index.html`, `app/web/CLAUDE.md` |
| Depends on / conflicts with | Conflicts with 47 (same `index.html` and `app/web/CLAUDE.md`, different blocks). Land either first and rebase the other. |
| Commit message | `Count the web download on a clone of each binary's response so that the browser can cache the compiled code.` |

## Problem
To drive the loading bar, `app/web/src/wasmJsMain/resources/index.html` wraps `window.fetch` (lines 438–499). Every `.wasm` response is replaced with a new response built around a counting stream:

```js
var reader = result.body.getReader();
var stream = new ReadableStream({ pull: function (controller) { … received += chunk.value.byteLength; … } });
return new Response(stream, {
    status: result.status,
    statusText: result.statusText,
    headers: result.headers
});
```

Both binaries (about 16 MB, the app's and skiko's) are instantiated with `WebAssembly.instantiateStreaming(fetch(…))`, so both get these synthetic responses. The copied headers keep streaming compilation working, as the comment at lines 380–384 and `app/web/CLAUDE.md:20–23` intend.

What they cannot keep is **Chrome/V8's WebAssembly code cache**. On repeat visits, Chrome reuses compiled (TurboFan) code for modules over 128 KB. That cache is tied to the response's URL and its HTTP-cache entry, and a `new Response(stream, …)` has `url === ""` and no cache entry behind it. So every visit likely compiles both modules from scratch: Liftoff first, then TurboFan tier-up of the hot functions. That costs startup time before `campfireReady` and brings back the early jank of tier-up on each visit. Firefox and Safari have no persistent Wasm code cache, so they are unaffected either way.

## Fix
1. **Confirm the miss first** on a production build served the way the memory note `web-target-verification` describes (a local imitation of GitHub Pages; not the dev server, which has no manifest). In Chrome:
   1. Load the page, use it for ten or more seconds, then reload twice.
   2. Record the third load in DevTools → Performance with "Enable advanced paint / JS" off, or in Perfetto with the `v8.wasm` category.
   3. Look for module compilation (`wasm.AsyncCompile*` / `v8.wasm.compile*`) against a cache hit (module deserialization, `v8.wasm.cacheHit` / `wasm.Deserialize*`).

   If the current build already deserializes from the cache, drop this plan and say so.
2. **Replace the wrapper's body** so the original response goes back to the caller untouched and the bytes are counted on a clone:

   ```js
   return response.then(function (result) {
       if (!result.ok || !result.body) {
           pending--;
           completed++;
           schedule();
           return result;
       }
       if (!BUILD) {
           expected += parseInt(result.headers.get('content-length'), 10) || 0;
       }
       // Counted on a copy: the response itself goes to instantiateStreaming as the network handed it over, with its
       // URL and its place in the HTTP cache, which is what lets the browser keep the compiled code for next time.
       var reader = result.clone().body.getReader();
       (function pump() {
           reader.read().then(function (chunk) {
               if (chunk.done) {
                   pending--;
                   completed++;
               } else {
                   received += chunk.value.byteLength;
                   pump();
               }
               schedule();
           }, function () {
               pending--;
               schedule();
           });
       })();
       return result;
   }, function (error) { … unchanged … });
   ```

   Keep the `pending` / `completed` / `received` bookkeeping and `showFailure` exactly as they are today; only the body of the success path changes. Note the cost this accepts: `clone()` tees the body, so chunks the compiler has not consumed yet are held for it. The counting branch reads as fast as the network, so the extra memory is at most what compilation lags behind the download, and it is freed as it catches up.
3. **Rewrite the KDoc above the wrapper** (lines 380–384): the body is read through a clone, and the original response is returned so that it keeps its URL, which is what streaming compilation *and* the code cache need. **`app/web/CLAUDE.md:20–23`** says the same instead of "read through a counting stream … The headers are carried over to the replacement response".

Must not change: the progress bar's behavior and numbers, the versioned `fetch` it wraps (restored in `campfireReady`), failure reporting, or the Web Lock flow.

## Verification
- `./gradlew :app:web:wasmJsBrowserDistribution`, then serve `app/web/build/dist/wasmJs/productionExecutable` as in `web-target-verification`.
- The loading bar still moves smoothly from 0 to 92%, then decays and finishes. Check once on a throttled "Fast 4G" profile in DevTools.
- Repeat step 1's trace. The third load should show the module(s) coming from the cache, and the time from navigation to `campfireReady` should drop compared with before (average three runs each). Record both numbers in the commit's notes.
- Firefox and Safari still load the app (no functional change there).
- Update `app/web/CLAUDE.md` in the same commit (step 3).
