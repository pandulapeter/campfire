<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/screens/songs

The songs screen.

Before the first search interaction, Songs lays arriving section headers out fully expanded so loading cannot retain a collapsed first row as a scroll offset. `ListTopFade` treats leading collapsed header slots as zero scroll distance: its mask is absent at the real top and grows over the first 24dp of scrolling, including when the first card is item 1.
