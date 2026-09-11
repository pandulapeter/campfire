<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# File format

Campfire's library is a folder of plain text files. Nothing in it is Campfire's own invention except the small JSON
file a setlist is written into, and that is defaulted field by field so it can be written by hand.

### Songs

Songs are [ChordPro](https://www.chordpro.org/chordpro/chordpro-directives/) text files:

```
{title: Wonderwall}
{artist: Oasis}
{key: F#m}

{start_of_verse: Verse 1}
[F#m]Today is [A]gonna be the day
{end_of_verse}
```

Campfire understands the core of the format:

- **Metadata**: `title` / `t`, `subtitle` / `st`, `artist`, `composer`, `lyricist`, `album`, `year`, `key`, `capo`,
  `tempo`, `time`, `duration`, `transpose`, `tag` for each of a song's tags, `language` / `lang` for each of the
  languages it is in (`{meta: language en}` is how they are written back), and `meta` for anything else.
- **Environments**: `start_of_verse` / `sov`, `start_of_chorus` / `soc`, `start_of_bridge` / `sob`,
  `start_of_tab` / `sot`, `start_of_grid` / `sog` and any other `start_of_<name>`, each with the matching `end_of_…`,
  an optional label (`{sov: Verse 1}` or `{sov: label="Verse 1"}`), and `{chorus}` to repeat the last chorus.
- **Content**: `[Chord]` markers anchored to the syllable that follows them, `[*annotations]`, tab lines kept exactly
  as written, grid rows, `#` source comments, and `comment` / `comment_italic` / `comment_box` for shown ones.
- **Page directives** (`new_page`, `column_break`, …) are treated as layout hints; chord diagrams (`define`), fonts,
  colours and images are parsed and ignored.
- `{new_song}` / `{ns}` splits one imported file into several songs.

### Setlists

Setlists are small JSON files (`<name>.setlist.json`) stored next to the songs, so an exported library is a zip of
`songs/` and `setlists/`:

```json
{ "title": "Friday gig", "priority": 3, "songs": [ { "file": "oasis_wonderwall.cho", "transposition": 2 } ] }
```

An archived setlist carries `"isArchived": true` as well; every field is defaulted, so a hand-written file can leave
out anything it has nothing to say about.

### File names

A file name is a song's identity — it is what a setlist points at — and Campfire derives it from the song's own
header rather than from whatever the file was called when it arrived: `{artist}`, `{title}` and `{subtitle}`, folded
to lowercase unaccented words joined with underscores, as `green_day-good_riddance_time_of_your_life.cho`. The name a
file arrives under counts for nothing, except where the song inside declares no `{title}`, in which case it stands in
as the title.

The folding rules exist so that the same song written down by two people arrives at the same name: an apostrophe is
dropped rather than turned into a separator (`dont_cry`), `&` and `+` are spelled out (`rock_and_roll`), and a credit
is filed under `ft` however it was abbreviated. The rule is idempotent, since a name that left the app is normalized
again on its way back in. A name that is already taken by something else gets a `_2`, `_3`… suffix.

Campfire only ever renames a file when you ask it to, or when nothing is lost by it. A setlist's file follows its
title, because the title is written inside the document. A song's does not: it is what a setlist points at, and on
the platforms where the library is a folder you may have chosen it yourself. Where a song's name and its metadata
have drifted apart, its menu offers **Update file name**, which moves the file and everything that named it.
