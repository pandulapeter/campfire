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
## Bugs
- Issue on touchscreen devices, Edit song screen: after placing the cursor somewhere in the text then performing a scroll / fling gesture on mobile, the scroll position jumps back to caret position
- Window inset handling issues related to bottom sheets

## Improvements
- Maybe all bottom sheets with song names in their headers could show the cover art for the song
- Add cover arts to the Song assignments bottom sheet list items
- Cover art bottom sheet: do we even need search input fields here?
- Rename master branch to main
- Once the Mac App Store listing is approved, update included URL-s + this Readme

## Features
- Multi-select songs for bulk export or bulk edit (assign tags, languages, setlists)
- Implement optionally auto-indexing similar sections within a song
- Metronome: documentation/plans/metronome.md
- Haptic effects, especially for the fast scroller

## Ideas
- Global sync status display ?
- Fast scroller on the song details screen ?
- Simplify adding comments / annotations
- Optional close confirmation dialog on relevant platforms
- Add support for Latin and Nashville notations
- Chord diagrams (guitar, ukulele, keyboard) - user library, variations
- External monitor support for lyrics only...? Maybe as a new window on desktop
- Streaming zip writer on all platforms
- First time user experience tutorial ?

## Other
- Test support with external control devices
- Improve test coverage
- Each top-level Composable should be defined in a separate file