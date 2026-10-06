<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# To do
## Bugs / issues
- Overscroll bounce can make content on bottom sheets scroll underneath their header without fade
- The Song details screen is still wasteful: too much padding. Even in one-column mode some sections could be displayed in two columns
- Per-section metronome / time signature changes are not yet supported
- No way to enter BPM using the keyboard
- Incorrect ripple effect on top of time signature and metronome sound chips in light mode
- Improve touch feedback on top of metronome accent bars (clip to content)
- Active metronome icon color in light mode is wrong (for the default theme)
- More width for setlist song cards
- Reorder songs should hide the other setlists while trying to persist the focused song

## Improvements
- Support importing libraries from other apps
- Rename master branch to main
- Onboarding: integrate feature toggle-presets (singers, drummers, etc)
- Settings: promote Dropbox sign-in
- New song template should include default values for capo, tempo and time signature. The artist tag should also be included with no value

## Features
- Comments in setlists (between songs)
- Multi-select songs for bulk export or bulk edit (assign tags, languages, setlists) - rearrange mode in Setlists could be used for UX inspiration
- Haptic effects, especially for the fast scroller
- Duplicate song button

## Ideas
- Global sync status display ?
- Fast scroller on the song details screen ?
- Simplify adding comments / annotations
- Optional close confirmation dialog on relevant platforms
- Add support for Latin and Nashville notations
- Chord diagrams (guitar, ukulele, keyboard) - user library, variations
- Grid formatting could be improved
- External monitor support for lyrics only...? Maybe as a new window on desktop, lyric projection via AirPlay / Chromecast, etc
- Streaming zip writer on all platforms
- Backing tracks?
- First time user experience tutorial ?
- Native iOS, macOS, feel (overscroll, touch feedback, fonts, icons, colors, themes - Liquid Glass)

## Other
- Test support with external control devices
- Improve test coverage
- Each top-level Composable should be defined in a separate file