<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :chordpro

The [ChordPro](https://www.chordpro.org) format, and nothing else. A multiplatform library with **no dependencies at
all** — not even Koin: everything in it is a stateless `object`, reached from the rest of the app through the use cases
in `:domain:*`. `:data:source:local:implementation` uses it directly for the metadata of the song list.

- `model/` — `ChordProSong` (metadata + blocks), `ChordProBlock` (`Section`, `ChorusRecall`, `Comment`, `Break`),
  `ChordProLine` (`Lyrics` with positioned chords and annotations, `Tab`, `Grid`, `Blank`) and `ChordProMetadata`,
  whose `displayTitle(fallback)` is the one rule for naming a song: `{title}` with `{subtitle}` after it in
  parentheses, which is what the library scan writes into `Song.title` and what the editor's app bar shows.
  Nothing here knows about Compose, colours or measurements: the model says what a thing *is*, the viewer decides what
  it looks like.
- `ChordProSyntax` — the shared low-level rules (the directive and chord regexes, `chordNameRegex` for "is this whole
  word a chord and not a word that starts with a letter", long/short directive names, the `start_of_` / `end_of_`
  prefixes, `label="…"` attributes, and what counts as a tag directive). Every other object here goes through it, so
  the dialect is defined once.
- `ChordProParser` — `parse` (the whole song), `summarize` (the directives plus "does it have chords", from one walk,
  which is what the library scan calls for every file at startup) and `parseMetadata` (directive lines only, for a
  caller with no interest in the body). Total: it never throws and never rejects a document, because the
  file on disk is the user's and half of it may be under the caret. Unknown directives are ignored; `{define}`, fonts,
  colours, images and page directives are parsed and dropped. It also understands the Campfire 3 dialect, where
  `{comment: Verse 1}` outside an environment was a section heading.
- `ChordProSerializer` — writes the model back as canonical ChordPro. The editor works on raw text, so the user's own
  formatting does not have to survive this; `parse(serialize(parse(x))) == parse(x)` does.
- `ChordProTags` — the tags of a song. ChordPro's own `{tag: Needs study}` directive, one tag per directive and as
  many of them as the song has; `{meta: tag Needs study}`, which the spec documents as the same thing, is read as
  well but never written. The value is taken whole, commas included, because the spec calls a tag arbitrary text.
  Two spellings of the same word are one tag everywhere: matching ignores case, and the library shows the spelling
  of the first song that carries it. `addTag` and `removeTag` edit the text rather than the model, for the same
  reason `ChordProTransposer.transposeText` does — the result is written straight back to the user's file, so their
  own formatting has to survive a chip being tapped in the viewer. A new tag lands after the last one the file
  already has, or at the end of the directives it opens with.
- `ChordProSplitter` — splits a file that holds several songs at `{new_song}` / `{ns}`.
- `ChordProTransposer` — moves chords by semitones, on the model (the viewer) or directly on the text keeping every
  byte of formatting (the editor's transpose action). Chooses sharps or flats from the song's key, follows the bass
  note after `/`, understands German `H`, and leaves annotations alone. A caller that knows better passes
  `preferFlats` and gets that spelling instead, which is what the accidentals preference does; forced that way it is
  worth running for no semitones at all, so only `semitones == 0` *and* no forced spelling short-circuits. Its walk
  over the model is `rewriteChords`, which takes the rename as a function so that `ChordProNotation` can reuse it;
  the two differ only in what a tab is, a fingerboard to one and a page of chord names to the other.
- `ChordProTabTransposer` — the same move inside a `{start_of_tab}` environment, where it means the fret numbers and
  not the notes: the tuning stays what it was. A tab environment is transposed as a whole, so that a transposition
  that would take a fret off the fingerboard moves all of it by octaves instead of producing an unplayable number,
  and a tab that fits in no octave (one spanning more than 24 frets) is left alone rather than half moved. Only lines
  that look like tablature are touched that way; a line above them holding nothing but chord names gets those
  transposed, and anything else in the environment (`Tuning: D A D G A D`, a note to the player) is left byte for
  byte. Fret numbers are not all the same width, so the dashes around them are absorbed or padded to keep the columns
  lining up; where there is no dash to take (inside a `0h1p0` group) the line grows by a character instead. The same
  column bookkeeping serves `rewriteChordNames`, which only respells those chord names — `Bb` is a character wider
  than the `B` it becomes in German notation, and the staff underneath still has to line up.
- `ChordProNotation` — German notation, and the one thing in here that is about how a song is *read* rather than what
  it *is*: the note written `B` becomes `H`, the one written `Bb` becomes `B`, and nothing else moves — not the other
  letters, not the `#` and `b` signs, not the quality. (The classical German names spell every accidental out as
  `Cis` or `Es`; chord charts in those countries stop at the two letters, and so does this — a German chord chart
  writes `B` for what an English one calls `Bb`, but never `Ais`.) It runs after the
  transposition, never before, because the transposition works in the notation the file is written in. Nothing that
  goes back to disk passes through it: `ChordProTransposer.transposeText`, the editor's action, has no counterpart
  here on purpose.
- `ChordProHighlighter` — the typed spans an editor wants to colour (directive name, directive value, chord,
  annotation, comment). It lives here rather than in the UI so that what counts as a chord is decided in exactly one
  place; only what those look like on screen is the caller's business.

Everything here is pure, so everything here is tested: `commonTest`, run with `./gradlew :chordpro:desktopTest`. A
change to the dialect belongs in a test first.
