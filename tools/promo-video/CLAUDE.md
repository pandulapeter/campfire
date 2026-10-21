<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# tools/promo-video

A 30-second promotional video in three formats (1080×1920, 1080×1080, 1920×1080, 60 fps) over a rock backing track for
a lead guitar. The app is recorded headlessly from its real UI (`PromoShot`s in `tools/screenshots`' `Promo.kt`), the
motion graphics are a Remotion composition (`src/`: `Promo.tsx` the scenes and timing, `kit.tsx` the backdrop, device
frames, headline, chips, and per-format layout), and the music is a Python sequencer (`music/soundtrack.py`, its
docstring the chord chart and the mix). `./regenerate.sh [all|footage|music|video]` builds it into
`~/Desktop/Campfire Promo Video` (`CAMPFIRE_PROMO_OUT`), overwriting what is there; running and checking it is the
`promo-video` skill. Not a Gradle module, and CI runs none of it; what it installs, fetches, and encodes is gitignored.

Remotion is not open source: free for individuals and companies of up to three people, a company license for others
(remotion.dev/license). It is a tool, never shipped, so the app does not credit it.

## Timing

- 160 BPM in 4/4 at 60 fps: a beat is 22.5 frames, a bar 90, and the video exactly 20 bars (1800 frames), bar 1's
  downbeat on frame 0 and bar 20's chord ringing to the end. Every scene starts on a bar or half bar (`S` in
  `Promo.tsx`, which `cue-sheet.md` tells in words); its exit runs `OVERLAP` frames into the next while that one's
  devices come in from varied sides. `soundtrack.py` places its hits with `t_of(bar, beat)` against the same bars.
- **A scene's length or the tempo changes `Promo.tsx`, `soundtrack.py`, `cue-sheet.md`, and `VIDEO_BPM` in `Promo.kt`
  together.** A clip starts on its scene's first frame, so what it does on a beat is at a frame of the clip: the
  frame constants in `Promo.kt` and the matching ones in `Promo.tsx` (`STEPS`, `SCROLL_AT`, the chips' `at`).
- Scenes are laid out once, in portrait coordinates; `Stage` fits them into the square and landscape frames.

## What assumes today's app

- The Songs screen's search button (`songs_search`) and House of the Rising Sun heading what "hou" finds; the
  Chords and Scales tabs' key (`KEY_A`), how far the Scales tab is scrolled (`SCALE_FAMILIES_Y`) and the cell it
  taps, the setlist's grip indexes, the drag and swipe positions, the paste that opens the editor (`PastedChordSheet.kt`),
  and the frames above, all in `Promo.kt`; the tuner comes into tune around frame 90, bar 11's downbeat.
- The claims on screen are text in `Promo.tsx`: the feature chips, the instruments the Chords clip switches to, the
  transposition badge (`STEPS`), the platform list, and "Free. No ads. No account. Offline."
- Brand colors are the website's (`C` in `kit.tsx`); the tag colors are the app's `TagColor` dark variants. The chips'
  icons are the app's vector drawables, converted to SVG by `drawables_to_svg.py` and painted through a mask (`Icon`).

## Decisions the owner made (keep them)

- Features are demoed live in recorded clips (a setlist dragged, a song transposed and renotated, the Chords tab across
  instruments, a scale's sheet opened on the Scales tab, a chord sheet pasted into the editor, a PDF's pages swiped),
  not illustrated with graphics.
- Timeless: nothing calls a feature new.
- A static backdrop, the store listings' linear gradient (`GRADIENT`: `#5A49CA` into `#F57C00` at 135°, as Screenshot
  Bro frames them), the same through the light-theme scene; no grain.
- Type is the app's bundled Inter, and JetBrains Mono for the badge's chord, with no fallback font: a symbol the fonts
  lack is an icon instead.
- No soft shadows or glows around devices, chips, or icons (they encoded as blocky halos); devices have a thin outline.
- Generic devices, so one video serves every store: no notch or island, and the laptop shows the app's window alone
  (`Device.LAPTOP`); the phones' and tablet's status bars stay.
- Text animates by moving and fading together, never masked or clipped.
- No gradient text or buttons (they read as AI-made, and neither the app nor the website has them); the URL is plain
  text with nothing behind it.
- Headlines look exactly like the store listings' in Screenshot Bro, only their size differs: Inter Regular, white,
  letter spacing −5% of the size, line height 0.9, centred, both lines alike.
- In portrait and square, a headline is centred vertically in the gap between the frame's edge and its scene's device:
  above the device's top, or (the opening's, in portrait) below its bottom (`deviceTop`, `deviceBottom`).
- Type over the gradient is white (the lines under the name and the subtitle at 80%), since neither brand color reads
  on a backdrop made of both. Dark chips carry the accents: orange and lilac by turns for their icons (one is green),
  lilac for the transposition badge. A chip stays at least 40 px inside the portrait frame.
- Bold only for the name and the four promises; the headlines, the lines under the name, the chips, the badge's label,
  and the platform names are Regular or Medium; the badge's chord is JetBrains Mono Bold.
- Chips name what the clip does not already show (no "search" chip over a search), and no other app by name.
- The music is a backing track in A major for the owner's electric guitar: drums, bass, synths, and effects, no
  guitars, recorded rather than synthesized drums, no bells.
- The chord chart (`soundtrack.py`'s docstring) is the owner's, who records guitars and likely the bass over it: the
  scenes are fitted to the music, never the music to the scenes.
- The first frame already shows the app (the song list, under the name), since many feeds freeze on it.
- The transposition, the notations, the metronome, and the tuner scenes stay as they are.
- The opening: the phone's screen fills the frame and snaps back into place by bar 1's second beat, where the icon
  lands; the list scrolls from bar 2, and on bar 3 the phone rises over its headline while the search opens and "hou"
  is typed on a generic keyboard, a chip with each letter, leaving the song the iPad then shows.
- The Chords scene has one headline, with no "every", and one instrument switch, keyboard to ukulele, never a quick run
  of them.
- Bar 14 is one thing a beat: the laptop in dark on the downbeat (where the music goes to E), the light theme sweeping
  across it on the second with the whoosh alone (`LIGHT_FROM`), then the iPad and the phone; bar 15 lands a platform
  name an eighth note. The app is dark everywhere else.
- After the light theme the pace is a scene a bar: the setlist, the import, the export, the promises, the name.
- The whoosh is clearly heard: `whoosh(..., 3.0)` (`soundtrack.py`).

## Known limits

- The music was checked by measurement (each beat's chord, levels, spectrum, loudness), not by ear.
- Apple's App Store preview videos must be screen captures of the app, so these cannot be used as those.
