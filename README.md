# Campfire
*A lightweight ChordPro viewer and editor.*

Campfire is an app for musicians and people who like to sing. It keeps a library of plain
[ChordPro](https://www.chordpro.org) files — the same text files everyone else's chord tools read and write — and shows
them the way you want to read them while playing: chords above the syllables they belong to, sections flowed into as
few, as wide columns as the screen allows, and the text as large as you need it.

It works **completely offline**. There are no accounts, no servers and no network requests at all: the songs are files
on your device, and they are yours.

- **Write and edit** songs in a built-in editor with ChordPro syntax highlighting, a live preview and autosave.
- **Import** `.cho` files (and `.chopro`, `.chordpro`, `.crd`, `.chord`, `.pro`, `.txt`) or whole `.zip` archives of
  them; **export** a single song, a setlist, or the entire library as a zip. Nothing is ever overwritten: a name that
  collides gets a ` (2)` suffix.
- **Open with**: a ChordPro file opened from a file manager, an email or a browser download lands straight in Campfire.
- **Setlists** with their own per-song transposition, stored next to the songs so they travel with an export.
- **Transpose** by ear or by key (the app picks sharps or flats to match), **lyrics-only mode**, adjustable text size
  (pinch, or Ctrl/Cmd + scroll), light / dark / system theme, English and Hungarian.
- Android, iOS, macOS / Windows / Linux desktop and the web, from one Compose Multiplatform codebase.

Campfire is completely free, without any ads.

[<img src="https://play.google.com/intl/en_us/badges/images/badge_new.png" />](https://play.google.com/store/apps/details?id=com.pandulapeter.campfire)

### File format

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
  `tempo`, `time`, `duration`, `transpose`, and `meta` for anything else.
- **Environments**: `start_of_verse` / `sov`, `start_of_chorus` / `soc`, `start_of_bridge` / `sob`,
  `start_of_tab` / `sot`, `start_of_grid` / `sog` and any other `start_of_<name>`, each with the matching `end_of_…`,
  an optional label (`{sov: Verse 1}` or `{sov: label="Verse 1"}`), and `{chorus}` to repeat the last chorus.
- **Content**: `[Chord]` markers anchored to the syllable that follows them, `[*annotations]`, tab lines kept exactly
  as written, grid rows, `#` source comments, and `comment` / `comment_italic` / `comment_box` for shown ones.
- **Page directives** (`new_page`, `column_break`, …) are treated as layout hints; chord diagrams (`define`), fonts,
  colours and images are parsed and ignored.
- `{new_song}` / `{ns}` splits one imported file into several songs.

Setlists are small JSON files (`<name>.setlist.json`) stored next to the songs, so an exported library is a zip of
`songs/` and `setlists/` that any other tool can read:

```json
{ "title": "Friday gig", "priority": 3, "songs": [ { "file": "Oasis - Wonderwall.cho", "transposition": 2 } ] }
```

### Notes

- Version 4.0 is a rewrite: the online song library is gone and there is **no migration**. The first launch starts with
  an empty library.
- The [privacy policy](https://pandulapeter.com/legal/privacy_policy-campfire.html) linked from the app's settings
  still describes the 3.x app and needs to be updated: 4.0 makes no network requests and collects nothing.

### Screenshots

**Outdated** — these show Campfire 3.x, which had a built-in online song library. New ones are needed for 4.0.

<img src="screenshots/01.png" width="20%" /> <img src="screenshots/02.png" width="20%" />
<img src="screenshots/03.png" width="20%" /> <img src="screenshots/04.png" width="20%" />
<img src="screenshots/05.png" width="20%" /> <img src="screenshots/06.png" width="20%" />
<img src="screenshots/07.png" width="20%" /> <img src="screenshots/08.png" width="20%" />

### Building

See [CLAUDE.md](CLAUDE.md) for the architecture and the per-platform build commands, and `docs/rewrite-plan/` for how
the 4.0 rewrite was carried out, step by step.

### License
This software is licensed under GNU GPL 3.0. Any derivative works must follow the same open-source license.
