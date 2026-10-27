<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :abc:api

The contract of the staff notation engraver, and nothing else: depends on nothing, so no app type leaks in. Explicit
API mode. The plan and the measured facts behind it are in `documentation/plans/abc-notation.md`.

- `AbcRenderer.render(tune, width, transposition)` **never throws** (cancellation aside): anything that goes wrong is
  `AbcRendering.Failed`, and the caller keeps the ABC text on screen.
- A rendering is a list of `AbcSystem`s, one `<svg>` each (the tune's header first, then one per staff system), so a
  layout can keep a system whole. **The systems of one rendering are read together, in order**: abc2svg writes its
  `<style>` and `<defs>` once, into the first system needing them. Its class numbers (`fN`) keep counting across
  renders in one engine, so they mean nothing outside their rendering and never go into a cache key.
- Chord symbols carry `AbcSystem.CHORD_SYMBOL_CLASS` (`gch`) so they can take the chords' colour. Lyrics carry
  `AbcSystem.LYRICS_CLASS` (`lyr`), set `AbcSystem.LYRICS_ROOM` times larger than they are meant to be drawn, so the
  notes are spaced for a typeface wider than the renderer's width tables (Inter's).
