<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# App Store (iPhone and iPad)

Store rules: plain text; no links in the description; never name a competing app or another platform (Android, Google
Play, Windows); keywords are 100 bytes at most, comma-separated without spaces, and never repeat a word of the name or
the subtitle; the promotional text can change at any time without a review, every other field only with a new version.
The app is the iOS and iPadOS build, so the text may name touch, the share sheet, and dragging from another app, but
not the wallpaper's colors.

## Name (30 characters max)

```
Campfire: Songbook & Chords
```

## Subtitle (30 characters max)

```
Chords, lyrics, and setlists
```

## Promotional text (170 characters max)

```
New in 5.2.1: tabs drawn like sheet music, Nashville and Roman numbers for songs with no written key, Campfire in Hebrew, and better imports of its own PDFs.
```

## Description (4,000 characters max)

```
Campfire is a songbook for the songs you play: your lyrics and chords, laid out to be read while your hands are busy with an instrument, with a metronome that knows the tempo of every song and a tuner for the minute before it starts.

Chords sit right above the syllables they belong to, and a song flows into as many columns as the screen has room for, so you turn as few pages as possible.

Campfire doesn't come with songs of its own. It is a home for yours. Write them in the built-in editor, paste a chord sheet copied from a website, or bring the ones you already have, even another songbook app's backup.

Reading
• Turn the page with a tap, a key, or a Bluetooth page-turner pedal, and reach every line with Up and Down alone
• Or one column that scrolls by itself, at a speed set for each song
• ABC tunes drawn as sheet music, in the PDF too
• Adjustable text size, foldable sections, cover art, and links to recordings
• A lyrics-only view for the people who only sing
• Read only mode keeps a stray tap from changing a song mid-set

Chords and scales
• Chord diagrams for guitar, ukulele, mandolin, banjo, or keyboard, in alternate tunings too, in the shapes you actually play
• A Chords tab with every key's common chords, and a Scales tab that draws and plays every scale on your instrument
• Every song suggests the scales that fit it, and its tabs are moved to your instrument
• Transpose and set the capo, and Campfire names the key the band hears
• Chords in standard, German (H), Latin (Do Re Mi), Nashville, or Roman numeral notation

Tuner
• Tune a guitar, bass, ukulele, violin, mandolin, or banjo string by string, in many tunings, or any instrument chromatically
• Reference tones to tune by ear, opened over the song you are about to play
• It listens only while it is on screen, and keeps nothing it hears

Metronome
• Every song keeps its own tempo, time signature, and accents, tempo changes included
• Tap tempo, subdivisions, and five sounds
• A flash on every beat, and it keeps clicking with the screen locked

Setlists
• Play through the whole set without stopping, the tempo changing on the next bar
• Every setlist keeps its own key, capo, and tempo for each song
• A gig date with a countdown, and the set's total running time
• Archive a setlist after the gig instead of deleting it

Organizing and editing
• Search by title, artist, or tag
• Select several songs at once to add them to setlists, tag them, or export them
• Tags and languages stored inside the song files, with 14 tag colors
• Find a song's cover art and details online, only when you ask
• A ChordPro editor with syntax highlighting, error marks, a live preview, and bold, italic, and underline

Import, export, share, and sync
• Paste, drop, or share a chord sheet into Campfire, and chords written above the lyrics become real chords you can transpose
• Import ChordPro, text, PDF, Word, and ABC files, zip archives, and other songbook apps' backups
• Print-ready PDF export with your paper size and columns
• Share a song or setlist as a link or QR code that anyone opens in Campfire, kept up to date with your changes
• Optional sync through your own Dropbox, automatic after every change

Make it yours
• Light and dark themes, a sepia slider, and ten color schemes
• The app icon follows the color you choose
• Switch off any feature you never use
• English, Hungarian, Romanian, German, Spanish, French, Italian, and Hebrew, switchable inside the app

Your songs are yours
Your library is a folder of ordinary ChordPro text files on your device. Campfire works offline, needs no account, and collects no data: no analytics, no tracking, and no ads. It goes online only for what you ask of it: to sync and share through your own Dropbox, with no server in between, to open a shared link, and to fetch cover images and song details. You can export everything at any time.

Campfire is open source under the Mozilla Public License 2.0.
```

## Keywords (100 bytes max)

```
tuner,metronome,chordpro,guitar,ukulele,mandolin,banjo,bass,piano,capo,transpose,tabs,scales,worship
```

## Support URL

```
https://campfire-songbook.com/support/
```

## Marketing URL

```
https://campfire-songbook.com/
```

## Copyright

```
2026 Péter Pandula
```

## App Review Information: Notes (4,000 characters max)

```
Campfire has no account and nothing to sign in to. Two public-domain demo songs and a setlist are loaded on first launch, so every feature can be reviewed without adding content. The first launch also opens a short onboarding, which can be skipped.

New in 5.2.1, and where to find it:
- Tabs: the demo song House of the Rising Sun has picking patterns as tabs, now drawn with ruled strings and bold fret numbers, on screen and in the PDF ("Share or export" in a song's three-dot menu).
- Chord notation: with Settings → Preferences → Chords → Chord notation set to Nashville numbers or Roman numerals (also under Preferences in a song's three-dot menu), a song whose file names no key is numbered from the key its chords suggest; removing the {key} line of a demo song in the editor shows it.
- Hebrew: picked in Settings → General → Language or followed from the system, laid out right to left. Songs written right to left, in any language of the app, are shown, edited, and printed from the right.
- PDF import: a PDF exported by Campfire and imported through Import files in the New button's menu on the Songs screen keeps more of its sections, tab and grid labels, comments, and chord positions.

The microphone is used only by the Tuner tab (and the tuner sheet opened from a song), and is asked for only when the user taps the button on that page. It is listened to only while that page is on screen; nothing it hears is recorded, kept, or sent anywhere.

Network use is limited to features the user starts: optional sync with the user's own Dropbox account (Settings → Sync, not needed to review anything else); sharing a song or setlist as a public link in that same Dropbox; opening a shared link, which downloads it from dl.dropboxusercontent.com with no account; cover art for songs, an image downloaded from the address a song names and a search of MusicBrainz and the iTunes Search API, opened by tapping the cover in the "About the song" sheet; and the song details lookup, which searches the same two services. Everything else works offline.

The "audio" background mode keeps the metronome clicking while the screen is locked; it plays only after the user starts it, and stops when they leave the screen it was started on.
```
