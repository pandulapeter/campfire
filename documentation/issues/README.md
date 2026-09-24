<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# UI performance review

These are source-based opportunities, not measured claims about a particular device. The plans name the repeated work, preserve the current behavior, and specify a way to verify any change. The shared Compose UI runs on Android, iOS, desktop, and web; the browser's Kotlin/Wasm code and UI share one thread.

## Suggested order

1. [Setlist row allocations](ui-01-setlist-row-allocation.md) and [editor caret highlighting](ui-02-editor-highlighting-on-caret-move.md): frequent work on direct interactions.
2. [Editor summary scans](ui-03-editor-summary-per-keystroke.md): keep its immediate feedback while optimizing ordinary lyric edits.
3. [Song-details lookups](ui-04-song-details-lookup.md) and [stepper labels](ui-05-song-details-stepper-labels.md): independent changes on the same screen; review or land them one at a time.
4. [Search query work](ui-06-search-per-keystroke-work.md) and [search index rebuilds](ui-07-search-index-rebuilds.md): both touch `CampfireViewModel`; implement the shared index first if doing both.
5. [Lyric run grouping](ui-08-lyric-run-regrouping.md) and [song section indexing](ui-10-song-list-section-index.md): allocation reductions for long content and large lists.
6. [Web monospaced font loading](ui-09-web-monospace-font-startup.md) and [loading-bar idle frames](ui-11-web-loading-progress-idle-frames.md): low-priority startup experiments; require a cold-load trace before keeping a change.

## Repeatable web comparison

Build the production web output with `./gradlew :app:web:wasmJsBrowserDistribution`, then serve `app/web/build/dist/wasmJs/productionExecutable` from `localhost` (for example, `python3 -m http.server 8765` in that directory). Use the same browser, library, viewport, route, and sequence of actions before and after a change. Capture at least three runs with browser Performance and, for startup work, Network traces with cache disabled. Record median interaction time or long tasks as well as whether the screen still behaves correctly. A development-server result alone is not a production performance result.

`presentation` currently has no `commonTest` source set. A plan that adds pure helpers there should add `commonTest.dependencies { implementation(kotlin("test")) }` to `presentation/build.gradle.kts`. Existing `:chordpro` common tests already have this dependency.
