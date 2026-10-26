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
New in 5.1: chords named as they sound under a capo, chord degrees, a preview of every song you import, bold, italic, and underlined lyrics, and ABC tunes as chord grids.
```

## Description (4,000 characters max)

```
Campfire is a songbook for the songs you play: your lyrics and chords, laid out to be read while your hands are busy with an instrument, with a metronome that knows the tempo of every song and a tuner for the minute before it starts.

Chords sit right above the syllables they belong to, and a song flows into as many columns as the screen has room for, so you turn as few pages as possible.

Campfire doesn't come with songs of its own. It is a home for yours. Write them in the built-in editor, paste a chord sheet copied from a website, or bring the ones you already have: text files, PDFs, Word documents, ChordPro files, zip archives, even another songbook app's backup.

Reading
• Turn the page with a click, the arrow keys, or a Bluetooth page-turner pedal, and reach every line with Up and Down alone
• Long songs fit on fewer pages, and short ones fill the screen in centered columns
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
• Every setlist remembers its own key, capo, and tempo for each song
• A date and a countdown for the gig, and the set's total running time
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
• English and Hungarian, switchable inside the app

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

Files are only read and written where the user picks them in the system's open and save panels (Import files in the New button's menu on the Songs screen or in Settings → Library; Export in a song's or setlist's menu; Save QR code for a shared link), or dropped onto the window; the library itself stays inside the app's sandbox container.

The microphone is used only by the Tuner tab (and the tuner sheet opened from a song), and is asked for only when the user clicks the button on that page. It is listened to only while that page is on screen; nothing it hears is recorded, kept, or sent anywhere.

Network use is limited to features the user starts: optional sync with the user's own Dropbox account (Settings → Sync, not needed to review anything else); sharing a song or setlist as a public link in that same Dropbox; opening a shared link, which downloads it from dl.dropboxusercontent.com with no account; cover art for songs, an image downloaded from the address a song names and a search of MusicBrainz and the iTunes Search API, opened by clicking the cover in the "About the song" sheet; and the song details lookup, which searches the same two services. Everything else works offline.

New in 5.1.0, and where to find it:
- Capo: a song's chords, key, and scales are now named as they sound. The Capo control at the top of a song keeps the pitch and redraws the diagrams as the shapes fingered above the capo. The Chords and Scales tabs have a capo picker after the instrument.
- Chord degrees: each chord diagram at the top of a song names its degree in the song's key, such as V7 beside G7. The switch is in Settings → Preferences, and under Preferences in the song's three-dot menu.
- Bold, italic, and underline: the editor's Shortcuts row has a button for each, and Cmd + B, I, and U toggle them.
- Import preview: importing a single song (Import files in the New button's menu on the Songs screen, or dropping a file onto the window) opens a preview before anything is saved; in a review of several songs, clicking a song's row opens it.
- ABC tunes: .abc files import as one song per tune, with a chord grid and the tune kept as text; the app does not draw sheet music.
- PDF options: "Export" in a song's or setlist's menu has a Tablature switch, and a setlist's "Include song sheets" leaves only the running order when turned off.
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
