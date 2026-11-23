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
New in 5.5.0: pictures on a song's pages, PDFs, recordings, and videos attached to songs, and backups of up to 16 GB that bring them all back.
```

## Description (4,000 characters max)

```
Campfire is a songbook for the songs you play: your lyrics and chords, laid out to be read while your hands are busy with an instrument, with a metronome that knows the tempo of every song and a tuner for the minute before it starts.

Chords sit right above the syllables they belong to, and a song flows into as many columns as the screen has room for, so you turn as few pages as possible.

Campfire doesn't come with songs of its own. It is a home for yours.

Reading
• Turn a page or a column with a tap, a key, or a Bluetooth page-turner pedal, and reach every line with Up and Down alone
• Or one column that scrolls by itself at each song's own speed
• ABC tunes drawn as sheet music, in the PDF too
• Pictures on a song's pages, such as a photo of the sheet music, in the PDF too
• Adjustable text size, foldable sections, cover art, and links to recordings
• A lyrics-only view for singers
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
• Play through the whole set without stopping, the tempo changing on the next bar or beat
• Every setlist keeps its own key, capo, and tempo for each song
• A gig date with a countdown, and the set's total running time
• Archive a setlist after the gig instead of deleting it

Organizing and editing
• Search by title, artist, or tag
• Select several songs at once to add them to setlists, tag them, or export them
• Tags and languages stored in the song files, with 14 tag colors
• Find a song's cover art and details online, only when you ask
• A ChordPro editor with syntax highlighting, error marks, a live preview, and bold, italic, underline, and colors
• Attach a PDF, a recording, or a video to a song, and open it from the song

Import, export, share, and sync
• Paste, drop, or share a chord sheet, a web page, or just its link: the page's clutter is left out, and the chords become chords you can transpose
• Import ChordPro, text, PDF, Word, and ABC files, zip archives, and other songbook apps' backups
• Print-ready PDF export with your paper size and columns
• Share a song or setlist as a link or QR code that anyone opens in Campfire, kept up to date
• Optional sync through your own Dropbox, automatic after every change

Make it yours
• Light and dark themes, a sepia slider, and ten color schemes
• The app icon follows the color you choose
• Switch off any feature you never use
• English, Hungarian, Romanian, German, Spanish, French, Italian, Hebrew, Portuguese, Polish, and Czech, switchable inside the app

Your songs are yours
Your library is a folder of ordinary ChordPro text files on your device. Campfire works offline, needs no account, and collects no data: no analytics, no tracking, and no ads. It goes online only for what you ask of it, with no Campfire server in between. You can export everything at any time.

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

The microphone is used only by the Tuner tab (and the tuner sheet opened from a song), and is asked for only when the user taps the button on that page. It is listened to only while that page is on screen; nothing it hears is recorded, kept, or sent anywhere.

Network use is limited to features the user starts: optional sync with the user's own Dropbox account (Settings → Sync, not needed to review anything else); sharing a song or setlist as a public link in that same Dropbox; opening a shared link, which downloads it from dl.dropboxusercontent.com with no account; cover art for songs, an image downloaded from the address a song names and a search of MusicBrainz and the iTunes Search API, opened by tapping the cover in the "About the song" sheet; the song details lookup, which searches the same two services; and pictures named by an https address, downloaded once into the library when the user enters one with From a link in the editor's Image menu, taps Download on a missing picture, or opens the review of an import whose songs name one (Settings → Features → Pictures from links turns that off). Everything else works offline.

The "audio" background mode keeps the metronome clicking while the screen is locked; it plays only after the user starts it, and stops when they leave the screen it was started on.

On iOS 26 and later, an import the user started keeps working for a while after they leave the app, as a continued processing task shown in the system's own progress; it exists only while that import runs and ends with it.

New since the last approved version, and where to find it:
- Pictures in songs: open a song, tap Edit, then Image in the editor's toolbar (Photos, Files, or From a link). After Save, the picture shows on the song's page, opens full screen when tapped, and is printed in the PDF made from Share or export in the song's three-dot menu.
- Attachments: in the editor, Attachment (after Link in the toolbar) adds a document, recording, or video of up to 50 MB. It is listed in the About the song sheet (the song's three-dot menu, or a tap on the title at the song's top), where a tap opens it with the app iOS picks. Settings → Features → Attachments hides them.
- Large backups: Import files (the New button's menu on the Songs screen) takes archives of up to 16 GB, with their pictures and attachments, and shows how far it has got.
```
