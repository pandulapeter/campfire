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
  frame constants in `Promo.kt` and the matching ones in `Promo.tsx` (`SHEET_AT`, `STEPS`, the chips' `at`).
- Scenes are laid out once, in portrait coordinates; `Stage` fits them into the square and landscape frames.

## What assumes today's app

- The Chords tab's key (`KEY_A`), the setlist's grip indexes, the drag and swipe positions, and the frames above, all
  in `Promo.kt`; the tuner comes into tune around frame 90, bar 13's downbeat.
- The claims on screen are text in `Promo.tsx`: the feature chips, the instruments the Chords clip switches to, the
  transposition badge (`STEPS`), the platform list, and "Free. No ads. No account. Offline."
- Brand colors are the website's (`C` in `kit.tsx`); the tag colors are the app's `TagColor` dark variants.

## Decisions the owner made (keep them)

- Features are demoed live in recorded clips (a setlist dragged, a song transposed and renotated, the Chords tab across
  instruments, a PDF's pages swiped), not illustrated with graphics.
- Timeless: nothing calls a feature new.
- A static, flat backdrop: the app's own dark background (`#15121C`, the website's too), no gradient and no grain.
- No soft shadows or glows around devices, chips, or icons (they encoded as blocky halos); devices have a thin outline.
- Generic devices, so one video serves every store: no notch or island, and the laptop shows the app's window alone
  (`Device.LAPTOP`); the phones' and tablet's status bars stay.
- Text animates by moving and fading together, never masked or clipped.
- No gradient text or buttons (they read as AI-made, and neither the app nor the website has them): a headline's
  accented line is one flat color (`Accent`), and the URL plain text with nothing behind it.
- Two complementary accents: orange is the primary, for every headline's accented line (purple on the light scene,
  where orange reads poorly); lilac is the secondary, for the transposition badge, the off-beats, and the URL; chips
  alternate the two (one is green).
- Bold only for headlines and the name; the lines under the name, the chips, the badge's label, and the platform names
  are Regular or Medium; the badge's chord is JetBrains Mono Bold.
- The music is a backing track in A major for the owner's electric guitar: drums, bass, synths, and effects, no
  guitars, recorded rather than synthesized drums, no bells.
- Bar 16 is one thing a beat: the laptop in dark on the downbeat (where the music goes to E), the light theme sweeping
  across it on the second with the whoosh alone (`LIGHT_FROM`), then the iPad and the phone. Dark everywhere else.
- The whoosh is clearly heard: `whoosh(..., 3.0)` (`soundtrack.py`).

## Known limits

- The music was checked by measurement (each beat's chord, levels, spectrum, loudness), not by ear.
- Apple's App Store preview videos must be screen captures of the app, so these cannot be used as those.
