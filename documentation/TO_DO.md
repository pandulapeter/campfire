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

## Improvements
- Each setlist should display a total of the song durations for the setlist, right after the countdown if enabled (separated by a dot character), or instead of the countdown if it isn't. If some songs don't have a valid duration set, add a + sign after the total duration of the setlist to indicate that it's a minimum value. If none of the songs have valid durations set, don't display the total duration
- The sort by popups on main screens (Songs and Setlists) should have titles above the radio buttons
- Add cover arts to the Song assignments bottom sheet list items
- Maybe all bottom sheets with song names in their headers could show the cover art for the song
- Cover art bottom sheet: do we even need search input fields here? Right now it has two primary actions (Search and Save / Done)
- Rename master branch to main

## Features
- Multi-select songs for bulk export or bulk edit (assign tags, languages, setlists) - rearrange mode in Setlists could be used for UX inspiration
- Metronome: documentation/plans/metronome.md
- Haptic effects, especially for the fast scroller

## Ideas
- Global sync status display ?
- Fast scroller on the song details screen ?
- Simplify adding comments / annotations
- Optional close confirmation dialog on relevant platforms
- Add support for Latin and Nashville notations
- Chord diagrams (guitar, ukulele, keyboard) - user library, variations
- External monitor support for lyrics only...? Maybe as a new window on desktop, lyric projection via AirPlay / Chromecast, etc
- Streaming zip writer on all platforms
- Backing tracks?
- First time user experience tutorial ?
- Native iOS, macOS, feel (overscroll, touch feedback, fonts, icons, colors, themes - Liquid Glass)

## Other
- Test support with external control devices
- Improve test coverage
- Each top-level Composable should be defined in a separate file