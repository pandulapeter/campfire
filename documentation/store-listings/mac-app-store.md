<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Mac App Store

Store rules: the App Store's (`app-store.md`), on the same app record: the name, subtitle, support URL, marketing URL,
and copyright are shared with iOS and are kept there; the description, promotional text, keywords, and review notes
are the Mac version's own. Every entitlement is justified under App Sandbox Information. The app is the desktop build,
so the text names a click, the keyboard, right-clicking, the Dock, and dragging onto the window, never touch, the share
sheet, or the wallpaper's colors.

## Promotional text (170 characters max)

```
New in 5.2: songs that scroll by themselves at their own pace, ABC tunes drawn as sheet music, chord diagrams that follow key changes, and five more languages.
```

## Description (4,000 characters max)

```
Campfire is a songbook for the songs you play: your lyrics and chords, laid out to be read while your hands are busy with an instrument, with a metronome that knows the tempo of every song and a tuner for the minute before it starts.

Chords sit right above the syllables they belong to, and a song flows into as many columns as the screen has room for, so you turn as few pages as possible.

Campfire doesn't come with songs of its own. It is a home for yours. Write them in the built-in editor, paste a chord sheet copied from a website, or bring the ones you already have, even another songbook app's backup.

Reading
• Turn the page with a click, the arrow keys, or a Bluetooth page-turner pedal, and reach every line with Up and Down alone
• Or one column that scrolls by itself, at a speed set for each song
• ABC tunes drawn as sheet music, in the PDF too
• Adjustable text size, foldable sections, cover art, and links to recordings
• A lyrics-only view for the people who only sing
• Read only mode keeps a stray click from changing a song mid-set

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
• A flash on every beat, and it keeps going while you use other apps

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
• Right-click any song or setlist to open its menu
• A ChordPro editor with syntax highlighting, error marks, a live preview, and bold, italic, and underline

Import, export, share, and sync
• Paste a chord sheet or drag it onto the window, and chords written above the lyrics become real chords you can transpose
• Import ChordPro, text, PDF, Word, and ABC files, zip archives, and other songbook apps' backups
• Print-ready PDF export with your paper size and columns
• Share a song or setlist as a link or QR code that anyone opens in Campfire, kept up to date with your changes
• Optional sync through your own Dropbox, automatic after every change

Make it yours
• Light and dark themes, a sepia slider, and ten color schemes
• The Dock icon follows the color you choose
• Switch off any feature you never use
• English, Hungarian, Romanian, German, Spanish, French, and Italian, switchable inside the app

Your songs are yours
Your library is a folder of ordinary ChordPro text files on your device. Campfire works offline, needs no account, and collects no data: no analytics, no tracking, and no ads. It goes online only for what you ask of it: to sync and share through your own Dropbox, with no server in between, to open a shared link, and to fetch cover images and song details. You can export everything at any time.

Campfire is open source under the Mozilla Public License 2.0.
```

## Keywords (100 bytes max)

```
tuner,metronome,chordpro,guitar,ukulele,mandolin,banjo,bass,piano,capo,transpose,tabs,scales,worship
```

## App Review Information: Notes (4,000 characters max)

```
Campfire has no account and nothing to sign in to. Two public-domain demo songs and a setlist are loaded on first launch, so every feature can be reviewed without adding content. The first launch also opens a short onboarding, which can be skipped.

Files are only read and written where the user picks them in the system's open and save panels (Import files in the New button's menu on the Songs screen or in Settings → Library; Share or export in a song's or setlist's menu; Save QR code for a shared link), or dropped onto the window; the library itself stays inside the app's sandbox container.

The microphone is used only by the Tuner tab (and the tuner sheet opened from a song), and is asked for only when the user clicks the button on that page. It is listened to only while that page is on screen; nothing it hears is recorded, kept, or sent anywhere.

Network use is limited to features the user starts: optional sync with the user's own Dropbox account (Settings → Sync, not needed to review anything else); sharing a song or setlist as a public link in that same Dropbox; opening a shared link, which downloads it from dl.dropboxusercontent.com with no account; cover art for songs, an image downloaded from the address a song names and a search of MusicBrainz and the iTunes Search API, opened by clicking the cover in the "About the song" sheet; and the song details lookup, which searches the same two services. Everything else works offline.

New in 5.2.0, and where to find it:
- Continuous layout: Settings → Preferences → Song page → Layout (also under Preferences in a song's three-dot menu) switches between Pages and Continuous; the app also asks once, over the first song opened. In Continuous, the play button above the bottom step button starts the scroll, and a Scroll speed slider appears while it plays.
- Sheet music: the demo song Home on the Range has a Sheet music section, drawn as staff notation from its ABC text on the device by abc2svg, an engraving library bundled with the app and run in its embedded JavaScript engine (no code or content is downloaded). The editor's toolbar has a Sheet music button beside Tab and Grid, and "Share or export" has a Sheet music switch for the PDF.
- Languages: German, Spanish, French, Italian, and Romanian, picked in Settings → General → Language or followed from the system.
- Key changes: in a song whose key changes partway through, the chord diagrams at the top of the song are grouped under a heading for each key, and its Scales section suggests a scale for the part in the new key.
- PDF import: PDFs imported through Import files in the New button's menu on the Songs screen leave out page headers and footers and keep listed chord shapes, repeat signs, and labelled song details.
```

## App Sandbox Information

```
[com.apple.security.files.user-selected.read-write] Importing songs (ChordPro, text, PDF, Word, ABC, and zip files, and other songbook apps' backups) and exporting songs, setlists, the whole library, or a shared link's QR code, always through the system's open and save panels or files the user drops onto the window.
```

```
[com.apple.security.network.client] Features the user starts: optional sync and shared links through the user's own Dropbox account (Settings → Sync), off until the user connects one; opening a shared link from dl.dropboxusercontent.com; cover images a song names; and searches of MusicBrainz and the iTunes Search API for cover art and song details. Campfire has no server of its own.
```

```
[com.apple.security.network.server] Loopback-only (127.0.0.1) sockets: one receives the Dropbox OAuth redirect during sign-in (RFC 8252), the other passes files and links from a second launch of the app to the window that is already open. Nothing outside the Mac can connect to them.
```

```
[com.apple.security.device.audio-input] The tuner, which listens to the microphone only while its page is on screen, after the user clicks to start it, and keeps nothing it hears.
```
