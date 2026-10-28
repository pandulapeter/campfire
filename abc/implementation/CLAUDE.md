<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :abc:implementation

abc2svg (Jean-François Moine, LGPL-3.0-or-later) run in an embedded JavaScript engine. Only `:app:di` sees this module.

### The script

- `src/jvmSharedMain/resources/abc2svg/`: `abc2svg-1.js` and every module that changes how a tune is drawn, loaded
  when a tune asks for it (chord names and numerals, diagrams, grids, tablature, jazz chords, capo, Clairnote, jianpu,
  and the rest; not the playback and page modules), **bundled unmodified**, built from the v1.23.6 tag of the author's repository
  (https://chiselapp.com/user/moinejf/repository/abc2svg; npm and the GitHub mirror are stale). Its tarball endpoint
  fails, so the sources are fetched through the "raw" links at the tag and concatenated and minified as its
  build.ninja says (its jsmin.js runs under node with the three QuickJS globals it uses provided). The license
  header check leaves the folder alone.
- **Never bundle or adapt its GPL command line helpers** (cmdline.js, tosvg.js, tohtml.js):
  `campfire-abc-host.js` is our own shim over the core API (`new abc2svg.Abc(user)`, `tosvg`, `img_out`,
  `errmsg`, `read_file`), evaluated before abc2svg; modules are loaded through the host's `hostLoadModule`, only by
  abc2svg's own names.
- **A tune is untrusted** (shared links, imports, pastes): the host replaces each `Abc` instance's `do_begin_end` so a
  `%%beginjs` block, which abc2svg would `eval`, is ignored. On the web that script would run in the page, beside OPFS
  and the sync token. A tune over `MAX_TUNE_LENGTH` characters or `MAX_TUNE_VOICES` voices is never rendered, and a
  rendering of more than `MAX_SYSTEMS` systems is dropped: memory grows with the text, time with the voices times the
  systems, and iOS and the web have no engine limits.
- `AbcRendererImpl` puts format lines in front of the tune: the page (`%%pagewidth`, no margins), chord symbols,
  annotations, and lyrics at the page's text size (16 units, as a unit is drawn 1 dp times the reader's text size;
  abc2svg's 12 and 13 read as small print beside the lyrics), chord symbols in bold like the page's and tagged `gch`,
  lyrics tagged `lyr` and measured as sans-serif regular at `LYRICS_ROOM` times their size (abc2svg's bold serif ran
  Inter's words together; regular sans-serif at their own size still let long syllables touch), and `%%transpose`,
  which moves notes, key signatures, and chord symbols (the app renames the chords afterwards).

### Engines (`AbcScriptEngine`, `createAbcScriptEngine`)

- Android and desktop (`jvmSharedMain`): `io.github.dokar3:quickjs-kt`, one engine for the process, started by the
  first render or by the app's warm-up, whichever comes first (~150 ms on the desktop, then ~5 ms a render), on a
  daemon thread of its own: a QuickJS context is used by one thread at a time. It runs under a memory limit and a
  per-evaluation timeout, and is closed and started again after any failed render (a context that ran out of memory
  fails every later evaluation, or answers `null`), never after a cancellation, which quickjs-kt turns into an
  interruption of the render. Chosen over Rhino for being ~5× faster warm and ~20× faster cold at about the same
  download.
- **Rhino is the tested fallback** should quickjs-kt stall: `org.mozilla:rhino` 1.9.x (MPL-2.0, its NOTICE adds BSD-3;
  pin 1.9, as 2.0 will likely need Java 17 and break the embedding API), language version ES6 or later (abc2svg uses optional
  chaining), interpreted on Android (minSdk 26), compiled on the desktop only with jdk.dynalink in the jlink set. R8
  needs `-dontwarn java.beans.**`, `jdk.dynalink.**`, `javax.lang.model.**` and the names and constructors of
  `org.mozilla.javascript.**` kept, or it crashes at start. A `Context` is bound to its thread; compile the script once.
  Measured: desktop 11–21 ms warm, ~200 ms cold; emulator 23–29 ms warm, ~0.7 s cold.
- iOS: the system's JavaScriptCore (the engine adds nothing to the app; the scripts are ~330 KB), one context on a
  thread of its own (a context's virtual machine locks itself to one thread). Web: the page's own engine, scripts
  evaluated in the global scope on the main thread (a render takes a few milliseconds; reading abc2svg is the one longer
  pause, on the first tune). Both are handed the tune and the modules as values (`AbcScriptGlobals`), never Kotlin
  functions; the web catches every script error on the script's side, since one thrown into Kotlin/Wasm is no
  `Exception`.
- Neither has resources, so the build generates the scripts' text as Kotlin (the generateAbcScripts tasks in
  `build.gradle.kts`, from the very files in `jvmSharedMain/resources`), one copy per target.
- The tests (`commonTest`) run the real engine of each platform: desktopTest (QuickJS), iosSimulatorArm64Test
  (JavaScriptCore), and wasmJsBrowserTest (headless Chrome); CI runs the desktop's.
