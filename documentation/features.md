<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Features

The long version of what Campfire does. The [README](../README.md) has the short one.

### Reading

- Chords sit above the syllables they belong to, and sections are flowed into as few, as wide columns as the screen
  allows, so a song takes as little scrolling as it can.
- Text size is adjustable by pinching, or with Ctrl/Cmd + scroll.
- **Lyrics-only mode** hides the chords for the people in the room who only sing.
- **Performance mode** takes everything which could change the library out of the app while you are playing from it.
- The **arrow keys** scroll the song and step through the setlist — which is what a page turner pedal sends, so one
  paired with a phone or a tablet works as it is.

### Chords

- **Transpose** by ear, a semitone at a time, or by key, in which case the app picks sharps or flats to match.
- Read every song in the spelling you are used to: sharps, flats, or German notation (`H`) if that is what you grew
  up with.
- A transposition is remembered per song, and a setlist remembers its own for each song in it — the same song can be
  in one gig's key and another gig's.

### Writing and editing

- A built-in editor with ChordPro syntax highlighting and a live preview of the finished song.
- Nothing is written until you save it, and leaving with unsaved changes asks first. Ctrl/Cmd + S saves.
- Metadata is edited through the song's header rather than by hand, and tags and languages are put on or taken off
  there too.

### Getting songs in and out

- **Import** `.cho` files (and `.chopro`, `.chordpro`, `.crd`, `.chord`, `.pro`, `.txt`) or whole `.zip` archives
  of them.
- **Export** a single song, a setlist, or the entire library as a zip of plain text files that any other tool can
  read.
- **Open with**: a ChordPro file opened from a file manager, an email or a browser download lands straight in
  Campfire.
- An import **decides before it writes**. Every incoming file is held against the name it wants; one whose name is
  already taken by a file with exactly the same content is quietly disregarded, and the ones taken by something
  *different* are put to you as one question about the whole batch — keep both (the newcomer becomes `…_2`),
  replace, skip, or cancel the import. Replacing is the only thing in the app that ever overwrites a file.

### Organizing

- **Setlists** hold the songs you are going to play, in the order you are going to play them, each with its own
  transposition. They live next to the songs as small JSON files, so they travel with an export.
- A setlist that has been played can be **archived** rather than deleted, which puts it away without losing the
  songs in it. That, too, is written in the file.
- A setlist always shows every song it names, whatever the Songs screen happens to be filtered to: the filters
  narrow a view of the library, while a setlist is the list somebody wrote down.
- **Tags** are part of the song file — `{tag: …}` directives — so a tag travels with the file through an export, an
  import or a sync run. The library's set of tags is whatever the songs carry, and the Songs screen offers them
  counted and most used first, matched as any or all.
- **Languages** are carried the same way (`{meta: language en}`) and are their own category rather than one more
  tag. The filter appears once the library holds more than one language. Campfire ships no list of language names:
  it asks the platform what each code is called in the language the app is set to.

### Appearance

- Light, dark or system theme in eight colour schemes, plus the one Android 12+ takes from your wallpaper.
- English and Hungarian, switchable inside the app rather than only with the system language.

### Platforms

Android, iOS, macOS / Windows / Linux desktop and the web, from one Compose Multiplatform codebase. The web build
keeps its library in the browser's Origin Private File System — a real directory tree in the browser's own storage,
private to the origin — and asks the browser to make that storage persistent as it starts. Whether that is granted
is the browser's business, so Settings reports the answer rather than the app insisting on it.
