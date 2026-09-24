<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Stop updating the web loading bar on idle frames

Priority: low. Web startup only; verify the gain with a browser trace.

## Evidence

In `app/web/src/wasmJsMain/resources/index.html`, `startApp()` starts the progress animation, and `draw()` unconditionally schedules another animation frame (around lines 264-289). Every frame writes the bar's transform and `aria-valuenow`, even when no bytes have arrived and the displayed percent has not changed. A cold start can spend several seconds downloading or compiling Wasm, so this loop competes with startup work on the browser's main thread.

## Implementation plan

1. In the inline loading script, remember the last rendered transform value and rounded percentage. Write the DOM only when either value changes. Keep the `isReady` completion path and removal timer intact.
2. During the download phase, stop scheduling frames once `shown` has approached a stationary `target()` closely enough. The wrapped Wasm stream already calls `schedule()` for every chunk, which restarts easing when progress changes. Ensure the first script load and a response without a body cannot strand the bar.
3. During the post-download phase, `target()` changes with time even without chunks. Keep that decay moving, but cap updates to a sensible rate such as 30 per second. On `campfireReady`, resume the final ease to 100% immediately.
4. With browser cache disabled, compare a throttled cold load before and after in a Performance trace: animation callbacks, style/layout work, long tasks, and time to first app frame. Also test a failed download, retry, unsupported browser, and a second tab refused by the library lock.

## Done when

- No animation callback loop runs while download progress is stationary.
- Unchanged `aria-valuenow` is not written every frame.
- The bar still advances smoothly, reaches 100%, fades out, and reports failures in all existing startup paths.
