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
## Short-term (in this version)
### Bugs / issues

### Improvements
- Implement support for chord diagrams (guitar, ukulele, keyboard): documentation/plans/chord-diagrams.md. Answer this question before starting: How would {define} work with ukulele / keyboard? What if we have definitions for guitar, but the user prefers another instrument?
- Add support for Latin and Nashville notations: documentation/plans/latin-nashville-notation.md - don't forget to apply the changes to the chord diagrams feature too

## Mid-term (in the next versions)
- Multi-select for songs: documentation/plans/multi-select.md
- Duplicate song button
- Find a way to allow entering tempo using the keyboard
- Haptic effects, especially for the fast scroller
- Tuner: documentation/plans/tuner.md (question - do we want to add it to the toolbar?)
- Comments in setlists (between songs)
- Simplify adding comments / annotations to songs
- Optional close confirmation dialog on relevant platforms
- Improve test coverage
- Refactor, improve architecture, each top-level Composable should be defined in a separate file

## Long-term
- Rename master branch to main
- Onboarding: integrate feature toggle-presets (singers, drummers, etc)
- Settings: promote Dropbox sign-in
- Streaming zip writer on all platforms

## Ideas (not detailed enough yet)
- Global sync status display
- Fast scroller on the song details screen
- External monitor support for lyrics only...? Maybe as a new window on desktop, lyric projection via AirPlay / Chromecast, etc
- Backing tracks?
- Native iOS, macOS, feel (overscroll, touch feedback, fonts, icons, colors, themes - Liquid Glass)

## Other
- Test support with external control devices