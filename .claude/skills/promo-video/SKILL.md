---
name: promo-video
description: Regenerate Campfire's promotional video (three formats, its backing track for a guitar, the stems, and the cue sheet) from the app's current UI with tools/promo-video, check the result, and leave it in ~/Desktop/Campfire Promo Video. Invoke this skill WHENEVER the user asks to regenerate, re-render, refresh, or update the promo video, its music or backing track, or to change one of its scenes, clips, chips, timings, or sounds. Not for the store screenshots (that is store-screenshots).
---

<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Campfire promo video

How the video is built, and the owner's decisions it keeps, are in `tools/promo-video/CLAUDE.md`; read it first.

## What a run needs

- `tools/screenshots/library/` (gitignored), the store screenshots' copy of a Campfire data folder. If it is missing,
  stop and ask; never point the tool at the user's real data folder.
- JDK/Gradle as the repo needs, Node 24 with npm, ffmpeg, and Python 3. The script creates `.venv/` (numpy, scipy),
  runs `npm ci`, and fetches the samples (about 950 MB) when they are missing.

## Steps

1. **Make the change** asked for. A scene's timing touches `Promo.kt`, `Promo.tsx`, `soundtrack.py`, and
   `cue-sheet.md` together; a new or changed clip is a `PromoShot` in `Promo.kt` (load `code-style` first).
2. **Run** `tools/promo-video/regenerate.sh` in the background: `all` (about 10 minutes, most of it recording the app)
   after a change to the app or to `Promo.kt`; `music` then `video` (about 3 minutes) for the soundtrack alone;
   `video` for `src/` alone. It writes `~/Desktop/Campfire Promo Video`: the three `campfire-promo-*.mp4`,
   `campfire-promo-backing-track.wav` (−14 LUFS), `stems/`, and a copy of `cue-sheet.md`.
3. **Look at the result**: a renamed song file, label, or destination fails the Gradle step loudly, but a changed
   layout records the wrong thing quietly. A contact sheet of each changed clip
   (`ffmpeg -i tools/promo-video/public/clips/<clip>.mp4 -vf "select='not(mod(n\,8))',scale=200:-1,tile=7x2" -frames:v 1 sheet.jpg`)
   shows whether it does what it should on its frames, and one of the vertical video
   (`select='not(mod(n\,45))',scale=180:-1,tile=10x4`) that every scene shows today's UI. For a sound, compare
   `volumedetect` over its window in `stems/` before and after; the music is judged by measurement, not by ear.
4. **Report** what changed on screen and in the sound, with numbers where they were measured, and that the Desktop
   folder was overwritten. Never commit unless asked.
