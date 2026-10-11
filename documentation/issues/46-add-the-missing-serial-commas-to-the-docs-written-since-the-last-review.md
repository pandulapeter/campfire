# Add the serial commas missing from the README and from the CLAUDE.md prose written since the last review

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `README.md`, `CLAUDE.md`, `data/model/CLAUDE.md`, `data/source/local/implementation/CLAUDE.md`,
`data/sync/implementation/CLAUDE.md`, `presentation/src/wasmJsMain/CLAUDE.md`, and under
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`: `CLAUDE.md`, `chords/CLAUDE.md`,
`components/CLAUDE.md`, `dialogs/CLAUDE.md`, `screens/export/CLAUDE.md`, `screens/songDetails/CLAUDE.md`,
`screens/songEditor/CLAUDE.md`, `screens/songs/CLAUDE.md`

## Problem

The user's writing rule (their global `CLAUDE.md`) is the Oxford comma in every list of three or more, in all prose. The
README and many lines added to the `CLAUDE.md` files between fc1a089c2 and d650f3cb0 drop it ("red, green and blue").
Found by scanning every line `git diff fc1a089c2..d650f3cb0 -- '*.md'` adds, and the whole README; only clear lists are
listed. Not included, on purpose: the metronome and tuner `CLAUDE.md` files (`ui/metronome/`, `ui/tuner/`,
`screens/metronome/`, `metronome/`, `tuner/`), which other plans edit; the root `CLAUDE.md`'s long list of tested
helpers (~L229–233), whose items nest their own "and" and need a rewrite rather than a comma; the sentence of
`ui/CLAUDE.md` that plan 43 rewrites ("…always names the capo, the tempo and the time signature there…"); and the root
Tuner sentence plan 45 rewrites.

## Fix

Each change below is an exact phrase (unique in its file at d650f3cb0) and its replacement. Match by the phrase, not by
the line number (other lanes merge first and move lines); a phrase whose line wraps in the file wraps at the same place
in the replacement, so search for the words either side of the break. Skip a phrase another plan has already rewritten.
Keep each file's line width (150 columns at most) when a comma pushes a line over.

**`README.md`**
- ~L14 "Android, iOS, macOS, Windows, Linux and the web" → "Android, iOS, macOS, Windows, Linux, and the web"
- ~L20 "chord diagrams for guitar, ukulele and keyboard" → "chord diagrams for guitar, ukulele, and keyboard"
- ~L21 "accents, subdivisions and five sounds" → "accents, subdivisions, and five sounds"
- ~L48 "The ChordPro parser, transposer and serializer" → "The ChordPro parser, transposer, and serializer"

**`CLAUDE.md`** (root)
- ~L228 "(the caches, the edit of many songs at once and the cover cache)" → "(the caches, the edit of many songs at once,
  and the cover cache)"
- ~L262 "tagged, given languages or exported in one go" → "tagged, given languages, or exported in one go"

**`data/model/CLAUDE.md`**
- ~L48 "the library-wide transpositions, tempos and capos" → "the library-wide transpositions, tempos, and capos"

**`data/source/local/implementation/CLAUDE.md`**
- ~L157 "one transposition, tempo or capo that is not a number" → "one transposition, tempo, or capo that is not a number"

**`data/sync/implementation/CLAUDE.md`**
- ~L211 "when the four maps, the chord shapes or the tag colors change" → "when the four maps, the chord shapes, or the tag
  colors change"
- ~L314–315 "A change to those four maps, the chord shapes or the tag colors schedules a run" → "A change to those four
  maps, the chord shapes, or the tag colors schedules a run"

**`ui/CLAUDE.md`**
- ~L359 "key, capo, tempo and time stay on" → "key, capo, tempo, and time stay on"
- ~L394 "then the key, capo, tempo and time signature fields" → "then the key, capo, tempo, and time signature fields"
- ~L407–408 "the Choose setlists and the menu, the title opening the About the song sheet" → "the Choose setlists button,
  the menu, and the title opening the About the song sheet"
- ~L420–421 "the chords over the lyrics, the diagrams, the chord sheet and the key next to the stepper" → "the chords over
  the lyrics, the diagrams, the chord sheet, and the key next to the stepper"
- ~L425–426 "the guitar, the ukulele and Other are unchanged" → "the guitar, the ukulele, and Other are unchanged"

**`ui/chords/CLAUDE.md`**
- ~L67 "for a mouse, a keyboard and a screen reader" → "for a mouse, a keyboard, and a screen reader"

**`ui/components/CLAUDE.md`**
- ~L155–156 "and a setlist's Edit details and Reorder songs, Archive, Duplicate, Export, Delete, a song's Update file name
  and Duplicate song and the editor's Revert are always menu entries" → "and a setlist's Edit details, Reorder songs,
  Archive, Duplicate, Export, and Delete, a song's Update file name and Duplicate song, and the editor's Revert are always
  menu entries"
- ~L225 "the setlist's entry, the library's override or the file's `{tempo}`" → "the setlist's entry, the library's
  override, or the file's `{tempo}`"
- ~L357–358 "in the pills, the song filters' tag chips and the song picker's alike" → "in the pills, the song filters'
  tag chips, and the song picker's alike"
- ~L360 "eleven hues around the circle and then brown, gray and black" → "eleven hues around the circle and then brown,
  gray, and black"

**`ui/dialogs/CLAUDE.md`**
- ~L21 "**Search**: artist, album and title fields" → "**Search**: artist, album, and title fields"
- ~L166–167 "new song, new and duplicate setlist, tag management, language management and library deletion focus" →
  "new song, new and duplicate setlist, tag management, language management, and library deletion focus"
- ~L167–168 "Duplicating a song, editing an existing setlist, song metadata, song links and cover art leave" →
  "Duplicating a song, editing an existing setlist, song metadata, song links, and cover art leave"
- ~L194–195 "the original's lyrics, chords and every other directive" → "the original's lyrics, chords, and every other
  directive"
- ~L195 "by its own title, subtitle and artist" → "by its own title, subtitle, and artist"
- ~L196 "The bodies of the new song, setlist details and library deletion sheets" → "The bodies of the new song, setlist
  details, and library deletion sheets"
- ~L286 "a half ticked row goes on, off and back to half ticked" → "a half ticked row goes on, off, and back to half
  ticked"

**`ui/screens/export/CLAUDE.md`**
- ~L22–23 "the setlist content choice, the overview and the zip's manifest" → "the setlist content choice, the overview,
  and the zip's manifest"
- ~L23–24 "\"Start each song on a new page\", the Zip format and its preview" → "\"Start each song on a new page\", and
  the Zip format and its preview"
- ~L32 "so that Escape, the browser's Back and the back gesture close it" → "so that Escape, the browser's Back, and the
  back gesture close it"
- ~L36 "a zoomed, panned or turning page" → "a zoomed, panned, or turning page"
- ~L56 "between the list, the song's text and its messages" → "between the list, the song's text, and its messages"

**`ui/screens/songDetails/CLAUDE.md`**
- ~L168 "its value, its label and its icons" → "its value, its label, and its icons"
- ~L211 "then album, year, composer, lyricist and duration" → "then album, year, composer, lyricist, and duration"
- ~L212 "the file's key, capo, tempo and time as the same tiles" → "the file's key, capo, tempo, and time as the same
  tiles"
- ~L239–240 "while the tile, its chevron and its empty state stay" → "while the tile, its chevron, and its empty state
  stay"
- ~L243–244 "every row of chips, buttons or call to action" → "every row of chips, buttons, or call to action"
- ~L284–285 "The song is parsed, transposed and split into sections" → "The song is parsed, transposed, and split into
  sections"
- ~L336–337 "A row of chord diagrams, a tab system, a comment and the song's first section" → "A row of chord diagrams,
  a tab system, a comment, and the song's first section"
- ~L339–340 "in its columns, one, two and three lines more and the screen" → "in its columns, one, two, and three lines
  more, and the screen"
- ~L360 "the stepper, the menu and the app bar keep the reader's own size" → "the stepper, the menu, and the app bar
  keep the reader's own size"
- ~L581–582 "the back button, the cover, the title, Choose setlists and the menu" → "the back button, the cover, the
  title, Choose setlists, and the menu"
- ~L623 "so the back button, the title and the actions stay" → "so the back button, the title, and the actions stay"

**`ui/screens/songEditor/CLAUDE.md`**
- ~L117–118 "(for what the Shortcuts write, a `{define}` above all) and the revert" → "(for what the Shortcuts write, a
  `{define}` above all), and the revert"

**`ui/screens/songs/CLAUDE.md`**
- ~L23–24 "so the search, the sorting and the filters keep working" → "so the search, the sorting, and the filters keep
  working"
- ~L38–39 "A refresh is a change of the search, the sorting or a filter" → "A refresh is a change of the search, the
  sorting, or a filter"
- ~L46 "and the copy, the header and the divider fade in" → "and the copy, the header, and the divider fade in"
- ~L47 "keeps the search, the sort and the filter;" → "keeps the search, the sort, and the filter;"
- ~L49–50 "then Choose setlists, Manage tags, Manage languages and Export songs" → "then Choose setlists, Manage tags,
  Manage languages, and Export songs"

**`presentation/src/wasmJsMain/CLAUDE.md`**
- ~L68 "or a dialog, a sheet or a menu open over them" → "or a dialog, a sheet, or a menu open over them"
- ~L110 "a dialog, a sheet or a menu open over a screen is one too" → "a dialog, a sheet, or a menu open over a screen is
  one too"
- ~L112 "pushed, replaced or gone back through to match" → "pushed, replaced, or gone back through to match"

Every touched `CLAUDE.md` keeps its MPL-2.0 header; run `.github/scripts/check_license_headers.py` if in doubt.

## Tests

None: prose only.

## Manual check

None. Optionally re-run the scan: list the lines the range added
(`git diff fc1a089c2..d650f3cb0 -- '*.md'`) and grep them for `, [^,]+ (and|or) ` without a comma before the
conjunction, to confirm nothing on the list was missed.
