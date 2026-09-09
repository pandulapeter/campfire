# :chordpro

The [ChordPro](https://www.chordpro.org) format, and nothing else. A multiplatform library with **no dependencies at
all** — not even Koin: everything in it is a stateless `object`, reached from the rest of the app through the use cases
in `:domain:*`. `:data:source:local:implementation` uses it directly for the metadata of the song list.

- `model/` — `ChordProSong` (metadata + blocks), `ChordProBlock` (`Section`, `ChorusRecall`, `Comment`, `Break`),
  `ChordProLine` (`Lyrics` with positioned chords and annotations, `Tab`, `Grid`, `Blank`) and `ChordProMetadata`.
  Nothing here knows about Compose, colours or measurements: the model says what a thing *is*, the viewer decides what
  it looks like.
- `ChordProSyntax` — the shared low-level rules (the directive and chord regexes, long/short directive names, the
  `start_of_` / `end_of_` prefixes, `label="…"` attributes). Every other object here goes through it, so the dialect is
  defined once.
- `ChordProParser` — `parse` (the whole song), `parseMetadata` (directive lines only, cheap enough to run over every
  file in the library at startup) and `hasChords`. Total: it never throws and never rejects a document, because the
  file on disk is the user's and half of it may be under the caret. Unknown directives are ignored; `{define}`, fonts,
  colours, images and page directives are parsed and dropped. It also understands the Campfire 3 dialect, where
  `{comment: Verse 1}` outside an environment was a section heading.
- `ChordProSerializer` — writes the model back as canonical ChordPro. The editor works on raw text, so the user's own
  formatting does not have to survive this; `parse(serialize(parse(x))) == parse(x)` does.
- `ChordProSplitter` — splits a file that holds several songs at `{new_song}` / `{ns}`.
- `ChordProTransposer` — moves chords by semitones, on the model (the viewer) or directly on the text keeping every
  byte of formatting (the editor's transpose action). Chooses sharps or flats from the song's key, follows the bass
  note after `/`, understands German `H`, and leaves tabs and annotations alone.
- `ChordProHighlighter` — the typed spans an editor wants to colour (directive name, directive value, chord,
  annotation, comment). It lives here rather than in the UI so that what counts as a chord is decided in exactly one
  place; only what those look like on screen is the caller's business.

Everything here is pure, so everything here is tested: `commonTest`, run with `./gradlew :chordpro:desktopTest`. A
change to the dialect belongs in a test first.
