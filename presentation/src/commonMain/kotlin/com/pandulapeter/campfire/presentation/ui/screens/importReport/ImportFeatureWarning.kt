/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.import_warning_setlists_off
import com.pandulapeter.campfire.presentation.resources.import_warning_songs_off

/**
 * What the import screen warns about at its top: an import goes into the library whatever the Features switches say,
 * since they only hide, so one made with the songs switched off, or bringing setlists with the setlists switched off,
 * writes what nobody will see until the switch is back on.
 */
internal enum class ImportFeatureWarning {
    SONGS_OFF,
    SETLISTS_OFF,
}

/** The warning an import holding setlists or not ([hasSetlists]) gets with the switches as they are, if any. */
internal fun importFeatureWarning(
    areSongsEnabled: Boolean,
    areSetlistsEnabled: Boolean,
    hasSetlists: Boolean,
) = when {
    // The setlists go with the songs, so one warning says it for both.
    !areSongsEnabled -> ImportFeatureWarning.SONGS_OFF
    !areSetlistsEnabled && hasSetlists -> ImportFeatureWarning.SETLISTS_OFF
    else -> null
}

@Composable
internal fun ImportFeatureWarning.text() = stringResource(
    when (this) {
        ImportFeatureWarning.SONGS_OFF -> Res.string.import_warning_songs_off
        ImportFeatureWarning.SETLISTS_OFF -> Res.string.import_warning_setlists_off
    },
)
