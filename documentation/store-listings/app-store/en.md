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
New in 5.7.0: a redesigned song screen, its chord diagrams, scales, and tempo in a dock that follows your page, and Home Screen widgets for random picks and the next gig.
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
• Home Screen widgets and app icon quick actions
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

Network use is limited to features the user starts: optional sync with the user's own Dropbox account (Settings → Sync, not needed to review anything else); sharing a song or setlist as a public link in that same Dropbox; opening a shared link, which downloads it from dl.dropboxusercontent.com with no account; cover art for songs, an image downloaded from the address a song names and a search of MusicBrainz and the iTunes Search API, opened by tapping the cover in the "About the song" sheet; the same cover images for the songs a placed Home Screen widget can show, as its thumbnails (Settings → Features → Cover art turns covers off); the song details lookup, which searches the same two services; pictures named by an https address, downloaded once into the library when the user enters one with From a link in the editor's Image menu, taps Download on a missing picture, or opens the review of an import whose songs name one (Settings → Features → Pictures from links turns that off); and a web page shared to Campfire from the share sheet or pasted as its address alone, read once on the device, offscreen in the system's own web view (WKWebView), keeping only its text for the import (public https hosts only, never the local network; Settings → Features → Pages from links turns that off). Everything else works offline.

The "audio" background mode keeps the metronome clicking while the screen is locked; it plays only after the user starts it, and stops when they leave the screen it was started on.

On iOS 26 and later, an import the user started keeps working for a while after they leave the app, as a continued processing task shown in the system's own progress; it exists only while that import runs and ends with it.

New since the last approved version, and where to find it:
- Redesigned song screen: open any song on the Songs screen. Its chord diagrams, scale suggestions, and tempo are in a dock under the song (beside it on a wide iPad screen) that follows the page being read; tap the key chip in the top bar for the transposition and the capo, the tempo chip for the tempo sheet, and its play button to start the click.
- Home Screen widgets: touch and hold the Home Screen, tap Edit, then Add Widget, and choose Campfire's Random picks (songs drawn at random; Edit Widget sets its title, tags, languages, and number of songs) or Setlist (the next gig, or a setlist chosen in Edit Widget). Tapping a song opens it in the app.
- Quick actions: touch and hold the Campfire icon for Paste a song, Metronome, Tuner, Setlists, and New song.
```
