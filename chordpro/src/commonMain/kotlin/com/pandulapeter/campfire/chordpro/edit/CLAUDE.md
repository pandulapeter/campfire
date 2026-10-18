<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# chordpro/edit

- Tags, languages, cover, and links are directives: `{tag}` one per value; `{meta: language en}`, `{meta: cover URL}`,
  `{meta: link URL Optional name}`. **Language codes are normalized** to one primary subtag, two letters where one
  exists (a region or three-letter code would split a filter group); `und` and `zxx` mean no language. Values
  cannot inject braces or line breaks.
- `ChordProMetadataFields.set` rewrites the line the parser reads, in its own spelling, **but never a body
  `{key}`/`{tempo}`/`{time}`**; a cleared header value the body still changes is kept as an empty line, so the song
  does not start in its first change.
- `ChordProHeader.insert` puts a directive in the header, never at the caret (valid but invisible mid-verse);
  `ChordProHeader.write` selects (or fills) the existing line of a kind the song may hold only once instead.
- `ChordProPrettifier` formats raw text, never re-serializes a model, so unsupported directives survive. **No blank line
  is added around a tab or grid, or inside a legacy heading section or implicit paragraph** — it would split or close
  it. `define` is deliberately not in `metadataOrder`.

### `ChordProHighlighter`

Lives here so "what is a chord" is decided once. A directive the parser reads and drops is one `INVALID` token, decided
by the parser's own functions; an *empty* value is not invalid. `highlight(text, previous)` reads from the first changed
line until a line past the edit is the old one in the same state (open environment, once-only kinds said, header key)
and on the same side of the body's start, and must always equal `tokenize`: the tests type and delete through a song.
