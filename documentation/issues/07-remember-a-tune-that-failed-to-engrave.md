<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Remember a tune that failed to engrave, so a resize or a transposition does not try it again

**Kind:** performance · **Severity:** low · **Platforms:** all (desktop and Android worst)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/notation/SongNotations.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/notation/NotationCache.kt`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/notation/NotationCacheTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/notation/CLAUDE.md`

**Challenged:** amended — the failed set must also count as "kept" in `renderedFor` and in the effect's `isCached`
check, or a song holding one failed tune loses its first-frame notation and its instant re-pinch for every *other* tune;
"non-cancelled" is made concrete (cancellation already throws through `AbcRendererImpl` and the QuickJS engine, so check
`isActive` after a null as a guard); a tune that later renders removes its failure; the transient time-out case is
named and accepted.

## Problem

```kotlin
(cache?.get(key) ?: render(renderer, painter, tune, request)?.also { cache?.set(key, it) })
```

`render` returns null on `AbcRendering.Failed` (`renderNotationDrawing` prints and returns null), and nothing records
the failure. A tune that hits the desktop/Android QuickJS time limit (6ebdda707) is rendered again after every settled
resize, pinch step, or transposition: each attempt holds the single abc2svg thread for the whole limit, every other tune
waits behind it, and the engine is then closed and restarted. On iOS and the web, which have no engine time limit, a
slow tune within the caps pays its full cost each time (on the web on the main thread).

## Fix

In `NotationCache`, beside the renderings, a bounded set of failed `NotationTuneKey`s (the same key: tune text,
spelling, width, unit, title, painting), last 64 keys, least recently used out (a `LinkedHashMap<NotationTuneKey, Unit>`
re-inserted on read, as `renderings` is):

- `isFailed(key)` / `markFailed(key)`; `set(key, rendering)` removes the key from the failed set.
- `renderedFor(request)`: a tune whose key is failed counts as kept and is simply absent from `renderings` (it is drawn
  as its text, as today); return null only where a tune is neither rendered nor failed. Otherwise a song holding one
  failed tune would never be notation on its first frame again. (With every tune failed, `renderings` is empty and
  `SongNotations` is `NONE`, as today.)
- In `rememberSongNotations`' effect: `isCached` counts a failed key as kept (no 300 ms wait for nothing to engrave);
  in the loop, skip a failed key before `cache?.get(key)` / `render(...)`; after `render` returns null, call
  `markFailed(key)` only if the coroutine is still active (`currentCoroutineContext().isActive` / `isActive` of the
  effect's scope). Cancellation normally never returns null — `AbcRendererImpl.rendered` and
  `QuickJsAbcScriptEngine.render` rethrow `CancellationException`, and `withContext` rethrows on resume — but the guard
  keeps a racing interruption from being recorded.
- Threading: unchanged. The effect runs on the composition's (main) dispatcher and `render`'s `withContext` returns to
  it before the cache is touched, so every read and write stays on the main thread, as the class KDoc requires; no lock.
- What is remembered: every null from `render` — an `AbcRendering.Failed` (caps, abc2svg error, time limit, engine that
  cannot load) or a drawing outside the SVG subset (`parseNotationDrawing` null). All are deterministic per key except
  a time-out on a device busy at that moment; accept that such a tune stays text until its key changes (edit, width,
  size, transposition), it is evicted, or the app restarts. A failure is per key, so an edit or a new width tries again.
- The PDF's own renders (`printNotationsOf`) do not use this cache and are unchanged.
- Update the class KDoc and add one line to the package `CLAUDE.md`'s "Renderings are kept app-wide" bullet: a tune that
  failed is remembered per key (64), drawn as its text without being rendered again.

## Tests

`NotationCacheTest`: a key marked failed reports failed, another width of the same tune does not; the failed set is
bounded and evicts its least recently used key; `set` clears a failure; `renderedFor` returns the other tunes' renderings
(not null) when one tune of the request is failed and the rest are kept, and null when one is neither.
