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

- `src/jvmSharedMain/resources/abc2svg/`: `abc2svg-1.js` and the modules a tune may ask for (`chordnames-1.js`,
  `roman-1.js`), **bundled unmodified**, built from the v1.23.6 tag of the author's repository
  (https://chiselapp.com/user/moinejf/repository/abc2svg; npm and the GitHub mirror are stale). Its tarball endpoint
  fails, so the sources are fetched through the "raw" links at the tag and concatenated and minified as its
  build.ninja says (its jsmin.js runs under node with the three QuickJS globals it uses provided). The license
  header check leaves the folder alone.
- **Never bundle or adapt its GPL command line helpers** (cmdline.js, tosvg.js, tohtml.js):
  `campfire-abc-host.js` is our own shim over the core API (`new abc2svg.Abc(user)`, `tosvg`, `img_out`,
  `errmsg`, `read_file`), evaluated before abc2svg; modules are loaded through the host's `hostLoadModule`, only by
  abc2svg's own names.
- `AbcRendererImpl` puts format lines in front of the tune: the page (`%%pagewidth`, no margins), chord symbols
  tagged `gch`, lyrics tagged `lyr` and measured as sans-serif regular at `LYRICS_ROOM` times their size (abc2svg's
  bold serif ran Inter's words together; regular sans-serif at their own size still let long syllables touch), and
  `%%transpose`, which moves notes, key signatures, and chord symbols (the app renames the chords afterwards).

### Engines (`AbcScriptEngine`, `createAbcScriptEngine`)

- Android and desktop (`jvmSharedMain`): `io.github.dokar3:quickjs-kt`, one engine for the process, started by the
  first render (~150 ms on the desktop, then ~5 ms a render), on a daemon thread of its own: a QuickJS context is used
  by one thread at a time. Rhino is the tested fallback should quickjs-kt stall (see the plan).
- iOS (JavaScriptCore) and the web (the browser's engine) are not wired up yet: their engine fails every render.
- Tests run the real engine on the desktop (`desktopTest`).
