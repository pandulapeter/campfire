<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :libs:abc:implementation

abc2svg (Jean-François Moine, LGPL-3.0-or-later) run in an embedded JavaScript engine.

### The script

- `src/jvmSharedMain/resources/abc2svg/`: `abc2svg-1.js` and every module that changes how a tune is drawn (not the
  playback and page modules), **bundled unmodified**, built from the v1.23.7 tag of the author's repository
  (https://chiselapp.com/user/moinejf/repository/abc2svg; npm and the GitHub mirror are stale) by
  `python3 -I tools/abc2svg/update.py <tag>`, which also regenerates the music glyphs from the tag's font. An update
  then changes the version in `documentation/THIRD_PARTY_SOFTWARE.md` and `abc2svg_notice.txt` by hand.
- **Never bundle or adapt its GPL command line helpers** (cmdline.js, tosvg.js, tohtml.js): `campfire-abc-host.js` is
  our own shim over the core API, evaluated before abc2svg; modules are loaded only by abc2svg's own names.
- **A tune is untrusted** (shared links, imports, pastes): the host replaces each `Abc` instance's `do_begin_end` so a
  `%%beginjs` block, which abc2svg would `eval`, is ignored. On the web that script would run in the page, beside OPFS
  and the sync token. A tune over `MAX_TUNE_LENGTH` characters or `MAX_TUNE_VOICES` voices is never rendered, and a
  rendering of more than `MAX_SYSTEMS` systems is dropped: memory grows with the text, time with the voices times the
  systems, and iOS and the web have no engine limits. **A length divided by zero** (`C/0`) is refused before abc2svg
  sees it (`AbcZeroDurations.kt`): it never finishes laying one out, which only QuickJS's timeout would end.
- The host returns abc2svg's messages after the systems, with the line and column it passes to `errmsg`
  (`AbcMessages.kt` takes the format lines off); an unknown decoration, which it names without a position, is placed on
  every one written. The tune's U+0000 to U+0002, the output's separators, are blanked first, so positions hold.
- `AbcRendererImpl`'s format lines set chord symbols, annotations, and lyrics at the page's text size (16 units;
  abc2svg's 12 and 13 read as small print beside the lyrics), and measure lyrics as sans-serif regular at
  `LYRICS_ROOM` times their size (abc2svg's bold serif ran Inter's words together; regular sans-serif at their own size
  still let long syllables touch). `%%transpose` moves notes, key signatures, and chord symbols; the app renames the
  chords afterwards. **The tune's own page geometry, overall scale, and those three fonts are made comments**
  (`PageFormat.kt`; `%%` or `I:` becomes `% `, so positions hold): an export's `%%pagewidth 21.59cm` and `%%scale 0.83`
  engraved it for a sheet of paper, then shrunk to the column at half a hand-written tune's size, and its own lyrics font
  dropped the lyrics' class. A voice's own scale is relative to the score and kept.
- A tune with a voice in the tab clef (`V:… tab` or `clef=tab`, how notation programs export a guitar's or a bass's
  tablature) also gets `%%strtab`: only that line loads abc2svg's string tablature module, without which the voice is
  engraved as a bass-clef staff of notes with string numbers over them. Its chord symbols get `%%cstabfont` with the
  chord class, at the size that the module's 1.6 times scale draws as the page's chords (it gives them neither).
- A percussion map as notation programs export it (`I:percmap ^g g 42 x`, xml2abc's note, shown step, MIDI drum, and
  head) is rewritten into abc2svg's (`%%percmap ^g 42 x`) before rendering (`PercussionMaps.kt`): abc2svg reads the third
  word as the head, names no glyph by it, and draws the notes headless. Its `normal` head is dropped, being abc2svg's
  unnamed default.

### Engines (`AbcScriptEngine`)

- Android and desktop: `io.github.dokar3:quickjs-kt`, one engine for the process on a daemon thread of its own (a
  QuickJS context is used by one thread at a time), started by the first render or by the app's warm-up. It runs under
  a memory limit and a per-evaluation timeout, and is restarted after any failed render (a context that ran out of
  memory fails every later evaluation, or answers `null`), never after a cancellation, which quickjs-kt turns into an
  interruption. Chosen over Rhino for being ~5× faster warm and ~20× faster cold at about the same download.
- **Rhino is the tested fallback** should quickjs-kt stall: `org.mozilla:rhino` 1.9.x (MPL-2.0, its NOTICE adds
  BSD-3; pin 1.9, as 2.0 will likely need Java 17 and break the embedding API), language version ES6 or later (abc2svg
  uses optional chaining), interpreted on Android, compiled on the desktop only with jdk.dynalink in the jlink set. R8
  needs `-dontwarn java.beans.**`, `jdk.dynalink.**`, `javax.lang.model.**` and the names and constructors of
  `org.mozilla.javascript.**` kept, or it crashes at start. A `Context` is bound to its thread; compile the script once.
- iOS: the system's JavaScriptCore, one context on a thread of its own (a context's virtual machine locks itself to
  one thread). Web: the page's own engine, on the main thread. Both are handed the tune and the modules as values,
  never Kotlin functions; the web catches every script error on the script's side, since one thrown into Kotlin/Wasm is
  no `Exception`. Neither has resources, so the build generates the scripts' text as Kotlin from the very files in
  `jvmSharedMain/resources`.
- The tests run the real engine of each platform (desktopTest, iosSimulatorArm64Test, wasmJsBrowserTest); CI runs the
  desktop's.
