/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.launch

/** Why an app link was not followed, see `LaunchState.navigateToLink`. */
internal enum class LinkRefusal {

    /** An editor holds unsaved text, which only its own ways out may take away. */
    UNSAVED_EDITOR_TEXT,

    /** The onboarding is open, which a first run finishes before anything else. */
    ONBOARDING,

    /** An import is being reviewed or previewed: leaving it throws the import away, and its files may not be readable again. */
    IMPORT,

    /** The link resolved to no place at all. */
    NOWHERE,
}
