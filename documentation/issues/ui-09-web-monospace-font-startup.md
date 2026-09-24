<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Defer web monospaced fonts until a song or editor needs them

Priority: low; validate with a cold-load network trace before implementation. Web only.

## Evidence

`app/web/src/wasmJsMain/resources/index.html` preloads both JetBrains Mono files along with the three Inter interface fonts (around lines 56-60). `CampfireTheme` calls `monospaceFontFamily()` at the root (around line 82), so those two files are requested on every launch, even when the user stays on the Songs, Setlists, or Settings screens. The source files total about 228 KiB before transport compression. Only the song lyrics' tab/grid content and the editor field read `LocalMonospaceFontFamily`.

## Implementation plan

1. Record a cold-load browser Network trace with cache disabled and a constrained connection. Note when the two monospaced files start/finish relative to the Wasm binaries, Inter fonts, and first interactive Songs frame. If they do not compete for startup bandwidth or delay that frame, close this issue with the trace; do not change loading merely for a smaller request count.
2. If they do compete, remove the two JetBrains Mono preload links from `index.html` and stop calling `monospaceFontFamily()` in the root `CampfireTheme`. Keep the three Inter fonts and the launch-screen typography gate unchanged.
3. Provide `LocalMonospaceFontFamily` at the song-details and song-editor screen boundaries, calling the existing platform `monospaceFontFamily()` there. Ensure editor preview receives the same family. Keep the platform defaults on Android, iOS, and desktop.
4. Check the first opening of a song with tablature and of the editor on a cold cache. The fallback font must not make tab columns misalign; if it does, preload on navigation to those screens or retain the current eager behavior.

## Done when

- A cold launch that stays on a list or settings page makes no JetBrains Mono requests.
- Tab/grid alignment and editor highlighting are correct on their first visible frame.
- The constrained-network trace shows an improvement in time to the interactive Songs frame without a worse first-song experience.
