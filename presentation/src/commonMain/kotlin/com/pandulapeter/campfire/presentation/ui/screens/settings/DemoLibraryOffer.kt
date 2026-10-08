/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/** The settings screen's offer to add the demo library, see [CampfireViewModel.demoLibraryOffer]. */
enum class DemoLibraryOffer {
    AVAILABLE,

    /** Shown, but not to be taken while an import is running, this one included. */
    UNAVAILABLE,
}
