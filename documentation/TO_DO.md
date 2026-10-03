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
- Bottom sheet keyboard handling inconsistencies: when the keyboard disappears, some remain large (with empty space on the bottom), others shrink.
- Bottom sheet keyboard handling: a back gesture should always hide the keyboard before the bottom sheet
- Checkbox lists: always put the checked items first, recently unchecked items should also remain at top until something changes
- Edit song screen fling issue on mobile (scroll snaps back to caret position)
- Cover art bottom sheet: pinned header does not get updated by keyboard IME. Do we even need search input fields here?
- Edit song screen should automatically hide the shortcuts section if the keyboard is visible on small screens
- Export PDF screen: the FAB should float. Currently, in portrait mode the scrollable content is unnecessarily padded below it
- Song selection bottom sheet should persist the tag selection. On start, the pre-selected chips should come first
- Multi-select songs for bulk export or bulk edit (assign tags, languages, setlists)
- Implement optionally auto-indexing similar sections within a song
- Metronome: documentation/plans/metronome.md
- Global sync status display ?
- Rename master branch to main
- Fast scroller on the song details screen ?
- Optional close confirmation dialog on relevant platforms
- Haptic effects
- Simplify adding comments / annotations
- External monitor support for lyrics only...? Maybe as a new window on desktop
- Add support for Latin and Nashville notations
- Chord diagrams (guitar, ukulele, keyboard) - user library, variations
- Once the Mac App Store listing is approved, update included URL-s + this Readme
- Test support with external control devices
- Improve test coverage
- Streaming zip writer on all platforms
- Each top-level Composable should be defined in a separate file
- Multi-cursor in editor ?
- First time user experience tutorial ?
