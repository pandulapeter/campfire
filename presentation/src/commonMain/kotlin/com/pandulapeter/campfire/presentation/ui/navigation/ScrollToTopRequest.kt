/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

/**
 * A press of the item of the top level screen that is already open: [destination] scrolls to its top, and one that is
 * there already opens [destinationWhenAtTop] instead, where there is one. Only the chrome's Tools item names
 * one (the first tool switched on), so that it steps back to its first tab the way the Settings item steps back to
 * General; a tool's own item on a taller rail and a tool's tab only ever scroll.
 */
internal class ScrollToTopRequest(
    val destination: CampfireDestination.TopLevel,
    val destinationWhenAtTop: CampfireDestination.TopLevel?,
)
