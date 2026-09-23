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
  it looks like. `SectionType` is the clearest case of that line: it has no `Tab` or `Grid`, because tablature and a
  chord grid are ways of **writing lines down** rather than parts of a song. `{start_of_tab}` and `{start_of_grid}`
  switch the kind of `ChordProLine` that is read until they close, inside whatever section is running, so a solo
  written as a line of chords with tablature under it is one section and not three. Outside every environment they
  open the same implicit paragraph a bare line of lyrics would, carrying their own label, which is the only place
  `{start_of_tab: Riff}` can still say "Riff". Everything downstream follows: the serializer wraps each *run* of tab
  or grid lines back in its environment, the transposer moves each run of frets as its own fingerboard, and the viewer
  cuts each run into rows that fit its width (`ChordProTabWrapper`) with the lyrics around it. A blank line inside an
  environment does not end its run for either of the first two: the serializer writes it inside the environment,
  where the parser keeps it, and the transposer makes one octave decision for it on the model as it does in the text.
  A second environment does end it, even one only a blank line away or written straight after the first:
  `ChordProLine.Tab.continuesEnvironment` is what tells the two apart, and the serializer writes the boundary back.
  The environments ChordPro hands to another program (`abc`, `ly`, `svg`, `textblock`) are sections whose lines are kept
  verbatim as lyrics with no chords, so the transposition, the chord detection of the library scan and the highlighter
  all leave them alone. A `{comment}`, a break or a `{chorus}` inside an environment cuts the section in two the way it
  does anywhere else, and the environment carries on in the second half; a comment there is never read as a Campfire 3
  heading. The second half is marked `isContinuation`: it is the rest of a section the file wrote once, so the viewer
  does not head it again, and the serializer writes the pieces back into one environment with what cut them inside. A
  `ChorusRecall` carries the chorus it repeats (`blocks`) — every piece of the last chorus that was over where it
  stands, and what stood between them — resolved by the parser, so that the transposition and the notation reach it
  like any other block. The two halves of a tab are runs of their own, so the text transposition moves them as two fingerboards as
  well. A grid line keeps what comes before its first bar and after its last one as text, the margins ChordPro puts
  labels and comments in, and a cell may hold several chords joined with `~`, each transposed on its own.
- `ChordProSyntax` — the shared low-level rules (the directive and chord regexes, long/short directive names, a value
  separated from a known directive name by a colon or by whitespace alone (the spec allows both, and a line in braces whose name the app
  does not know stays the lyrics it has always been shown as), the `start_of_` / `end_of_` prefixes, `label`
  attributes in either quotes, and no label at all for a value made of other attributes, what counts as a tag or a language directive, `metadataKind` for the one name a
  directive is known by whichever of its spellings a file uses, `isStaffLine` for "is this line of a tab environment
  the staff or something written above it", `words` for the words of a line and their ranges, and where a new one
  goes in a file the user wrote).
  `metadataInsertionIndex` is that last rule: after the last directive of the same kind, and otherwise into the
  header in `metadataOrder`, the order the app lists metadata in — which leaves a header arranged some other way
  exactly as it is, since it only ever decides where a line is *added*. Every other object here goes through it, so
  the dialect is defined once. What counts as a space is decided in `words`, by `Char.isWhitespace` and not by a
  regex: the three platforms' regex engines disagree about `\s`, and a chart pasted from a web page is full of
  non-breaking spaces.
- `ChordProParser` — `parse` (the whole song), `summarize` (the directives plus "does it have chords", from one walk,
  which is what the library scan calls for every file at startup) and `parseMetadata` (directive lines only, for a
  caller with no interest in the body). Total: it never throws and never rejects a document, because the
  file on disk is the user's and half of it may be under the caret. Unknown directives are ignored; a directive with a
  selector suffix (`{title-guitar}`) is dropped, since there is nothing to match it against, and one with a negated
  selector (`{title-guitar!}`) is read as the directive it is on for the same reason; an environment with a selector is
  the environment it selects, since its lines are the song itself; a song that changes key is in the key its first
  `{key}` names; a `{transpose}` before the song's first line transposes the whole of it (`ChordProMetadata.transpose`,
  the last one there winning), and one further down is a modulation — a `ChordProBlock.Transpose` holding the offset
  from the whole-song value for everything after it, cutting the section it stands in the way a comment does — each
  value being the transposition of the rest of the song and a valueless one going back to the one before, as the spec
  has it; `{meta: title …}` and the other standard names the spec defines as their standalone directive
  (`subtitle`, `artist`, `composer`, `lyricist`, `album`, `year`, `key`, `capo`, `tempo`, `time`, `duration`) are read
  as that directive; `{define}`, fonts, colours, images and page directives are parsed and dropped. It also understands
  the Campfire 3 dialect, where `{comment: Verse 1}` outside an environment was a section heading; one that no line
  follows before a blank line, another section or the end of the file stays the comment it was, since a section with
  nothing in it is not drawn.
- `ChordProSerializer` — writes the model back as canonical ChordPro. The editor works on raw text, so the user's own
  formatting does not have to survive this; `parse(serialize(parse(x))) == parse(x)` does.
- `ChordProTags` — the tags of a song. ChordPro's own `{tag: Needs study}` directive, one tag per directive and as
  many of them as the song has; `{meta: tag Needs study}`, which the spec documents as the same thing, is read as
  well but never written. The value is taken whole, commas included, because the spec calls a tag arbitrary text — arbitrary text on one line: a line break in a value handed to `addTag` or `removeTag` is read as a space, since it would otherwise end the directive and leave the rest of it in the song as lyrics.
  Two spellings of the same word are one tag everywhere: matching ignores case, and the library shows the spelling
  of the song that comes first by file name. `addTag` and `removeTag` edit the text rather than the model, for the same
  reason `ChordProTransposer.transposeText` does — the result is written straight back to the user's file, so their
  own formatting has to survive a chip being tapped in the viewer. A new tag lands after the last one the file
  already has, or at the end of the directives it opens with. What makes "every other byte" true for all of these
  text edits (tags, languages, the transposition) is `ChordProSyntax.joinLines`: the line separator is detected per
  file, CRLF where any line ends that way, a bare CR where the file uses those and no CRLF (an old Mac export), LF
  otherwise, and a trailing line break is put back where the file had one; `ChordProHeader.insert` writes its line
  break with the same separator. A file mixing its endings comes out with the one separator that picked.
- `ChordProLanguages` — the languages a song is sung in, which ChordPro has no directive for at all: what Campfire
  writes is `{meta: language en}`, a custom metadata item, and what it reads is that plus `{meta: lang en}` and the
  bare `{language: en}` / `{lang: en}` a hand written file may carry. A value is normalized on the way in
  (`ChordProSyntax.languageCode`): trimmed, cut down to its primary subtag, folded to lower case and, where the
  standard has a two letter code for the same language, to that — so `EN-us`, `eng` and `ger` are `en`, `en` and
  `de`. A region would make a filter group of its own for what is the same language to a song book, and the
  platforms disagree about what to call one anyway; a three letter code would make a *second* group for a language
  that already has one, and is besides the spelling a JVM refuses to translate, answering "English" for `eng` in
  every language it is asked in. The table that does the folding is `ChordProLanguageCodes`, the only data in this
  module that is not about the format; the three letter codes it does not list (`rom`, Romani — most of what ISO
  639-2 adds) are kept exactly as the file wrote them, since nothing else can name those languages at all. `und` and `zxx` are read as *no* language, since that is
  what they mean and it leaves `und` free to stand for "declares none" above this module. `setLanguages` rewrites the
  set in one pass, editing the text rather than the model for the same reason `ChordProTags` does; a language that is
  kept stays on the line and in the spelling it was written in. `code` is that same normalization offered on its own,
  for a caller holding a piece of text rather than a file — the picker's search field, where somebody may well type
  `HUN` or `en-US` and has to find the row the library files that language under.
- `ChordProHeader` — the block of directives a song opens with, for the editor, which writes into it while the caret
  is somewhere else entirely. ChordPro reads a `{title}` as the title from anywhere in the file, so a directive
  inserted at the caret is valid in the middle of a verse, invisible in the rendered song and nowhere near the rest
  of what the file says about itself; `insert` answers with the one offset it belongs at instead, the line to write
  there and where the caret then goes, so that nothing already written is moved or reformatted. `declaredMetadata`
  is the other half of that: what the song already declares, counted by directive and not by value, so that a
  `{title: }` waiting to be typed into is a title. `repeatableMetadata` is the pair a song may say twice — its tags
  and its languages — and everything else is a thing a song can only be one of, which is what lets an editor stop
  offering it.
- `ChordProSplitter` — splits a file that holds several songs at `{new_song}` / `{ns}`, trimming the blank lines around
  each. `comparable` folds a text the same way, line endings included, which is what an import compares a part
  against the file already on disk with: the same song, tagged in the app or written by hand, is not a conflict. Both
  drop a byte order mark wherever it sits, since joining two files that each carry one leaves one in the middle, and
  U+FEFF is not whitespace to `trim`, so a `{title}` behind it would be read as lyrics.
- `ChordProTransposer` — moves chords by semitones, on the model (the viewer) or directly on the text keeping every
  byte of formatting (the editor's transpose action). Chooses sharps or flats from the song's key, follows the bass
  note after `/`, understands German `H`, moves a key spelled out in words (`G major`, `A minor`, `Bb-Dur`) by its note
  and keeps the words (`renameKey`), which is also what the notation and the library scan use for the key, and leaves
  annotations alone. A bracket is only moved when
  `ChordProChordNames.isChordName` accepts the whole of it, so a `[Break]` or a `[Chorus 2x]` somebody wrote without
  the `*` is left where it is, and does not vote on the spelling either — the same question `ChordProNotation` has
  always asked. A caller that knows better passes `preferFlats` and gets that spelling instead, which is what the accidentals preference does; forced that way it is
  worth running for no semitones at all, so only `semitones == 0` *and* no forced spelling short-circuits. Its walk
  over the model is `rewriteChords`, which takes the rename as a function so that `ChordProNotation` can reuse it;
  the two differ only in what a tab is, a fingerboard to one and a page of chord names to the other. A modulation
  moves the stretch after it by its offset on top of the transposition asked for, spelled for the key it lands in, and
  a recall is moved by the offset where it stands; the text transposition leaves the `{transpose}` directives alone,
  which keeps them right, since each is relative to the song as written.
  `transposedOffset` maps a caret through a text transposition (same line, same place between the brackets), which
  is what keeps the editor's caret next to the text it was at.
- `ChordProTabTransposer` — the same move inside a `{start_of_tab}` environment, where it means the fret numbers and
  not the notes: the tuning stays what it was. A tab environment is transposed as a whole, so that a transposition
  that would take a fret off the fingerboard moves all of it by octaves instead of producing an unplayable number,
  and a tab that fits in no octave (one spanning more than 24 frets) is left alone rather than half moved. Only lines
  that look like tablature are touched that way; a line above them holding nothing but chord names (bar lines, repeats
  and an `N.C.` allowed) gets those transposed, and anything else in the environment (`Tuning: D A D G A D`, a note
  to the player) is left byte for byte — while a bare `E A D G B E` is six chord names and is transposed as such. Fret numbers are not all the same width, so the dashes around them are absorbed or padded to keep the columns
  lining up; where there is no dash to take (inside a `0h1p0` group) the line grows by a character instead. The same
  column bookkeeping serves `rewriteChordNames`, which only respells those chord names — `Bb` is a character wider
  than the `B` it becomes in German notation, and the staff underneath still has to line up.
- `ChordProTabWrapper` — cuts one run of tablature into rows that fit a width, for the viewer, which is the one
  place a tab can be too wide for: a tab is a grid of columns, so it can never be wrapped line by line, and every line
  of the run is cut at the same columns instead — after the last bar line that fits, so that a row ends where the
  music does; where one bar is wider than the row, on the last column in its second half that every string has a
  dash on and no chord name spans; and only where there is no such column at the edge. A run is whatever lines of a
  tab environment no blank line separates, and it may stack several systems, so it is cut system by system: a new one
  starts at the lines above a staff that follows another, or where the string names start over, all of them in order
  (a name that recurs inside one system, `E|` for both E strings or DADGAD's three `D|`, does not); every system is
  cut at columns of its own and its rows come before the next one's. Every row after the first
  repeats the string names in front of the staff (`e|`, `B|`, padded to one width where an `Eb|` sits above a ` G|`),
  and a line above the staff is left out of the rows it has nothing to say in, so the chord names travel with the
  notes they are written over. Such a line is cut at the nearest place in front of the column that is not inside a
  character, so a surrogate pair or a letter and its combining mark travel into one row whole. A run with no staff
  line in it at all is not tablature but preformatted text, which `isTablature` says, and it is returned whole for the viewer to scroll instead. Nothing in it is a measurement: it
  is asked for a number of characters, and the viewer works that out from its font. A run that would wrap into more
  row lines than half its characters, which only a crafted file does, is returned whole.
- `ChordProNotation` — German notation, and the one thing in here that is about how a song is *read* rather than what
  it *is*: the note written `B` becomes `H`, the one written `Bb` becomes `B`, and nothing else moves — not the other
  letters, not the `#` and `b` signs, not the quality. (The classical German names spell every accidental out as
  `Cis` or `Es`; chord charts in those countries stop at the two letters, and so does this — a German chord chart
  writes `B` for what an English one calls `Bb`, but never `Ais`.) It runs after the
  transposition, never before, because the transposition works in the notation the file is written in. A file is taken
  for German-notated when one of its chords uses `H` (a key counts too), and for nothing else, since the file carries no
  marker: a German chart in a flat key, which never needs an `H`, reads as English, so the `B` in it is B natural —
  drawn as `H` with the German notation preference on and transposed as B. Writing that chord `Bb`, or any `H` chord in
  the song, is what tells the two apart. Nothing that goes back to disk passes through it:
  `ChordProTransposer.transposeText`, the editor's action, has no counterpart here on purpose. The same charts often
  write a minor chord as its root in lowercase (`a` for `Am`, `h` for `Hm`), and that is read as the minor chord it
  stands for, in either notation; a lowercase `h` marks a song as German like an uppercase one. The model spells them
  out; the editor's transposition keeps the file's lowercase. The note after a `/` may be lowercase for the same
  reason (`D/f#`, `C/h`): it is transposed and respelled like any other note and folded back to the case the file
  used, and a lowercase `h` there marks a song as German as well. Only there: a lowercase root is still read as a
  minor chord and never as a note.
- `ChordProHighlighter` — the typed spans an editor wants to colour (directive name, directive value, chord,
  annotation, comment). It lives here rather than in the UI so that what counts as a chord is decided in exactly one
  place; only what those look like on screen is the caller's business. It reads the file's lines through
  `ChordProSyntax` rather than walking them itself, so it agrees with the parser about where a line ends whichever of
  the three endings the file uses, and reads a bracket trimmed the way the parser does, so a `[ *softly]` is an
  annotation and an empty `[]` is not a chord.

Everything here is pure, so everything here is tested: `commonTest`, run with `./gradlew :chordpro:desktopTest`. A
change to the dialect belongs in a test first.
