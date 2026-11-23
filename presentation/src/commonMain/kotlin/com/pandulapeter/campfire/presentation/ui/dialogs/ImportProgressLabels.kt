/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_downloading
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_downloading_so_far
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_receiving
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_receiving_so_far
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_unpacking
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_unpacking_so_far
import com.pandulapeter.campfire.presentation.resources.import_progress_comparing
import com.pandulapeter.campfire.presentation.resources.import_progress_downloading
import com.pandulapeter.campfire.presentation.resources.import_progress_downloading_pictures
import com.pandulapeter.campfire.presentation.resources.import_progress_finishing
import com.pandulapeter.campfire.presentation.resources.import_progress_importing
import com.pandulapeter.campfire.presentation.resources.import_progress_reading
import com.pandulapeter.campfire.presentation.resources.import_progress_reading_page
import com.pandulapeter.campfire.presentation.resources.import_progress_receiving
import com.pandulapeter.campfire.presentation.resources.import_progress_unpacking

/** Only the phases that count bytes (`countsBytes`) reach these; any other says how far it is in the unpacking's words. */
internal val ImportProgress.Phase.bytesOfTotalLabel
    get() = when (this) {
        ImportProgress.Phase.RECEIVING -> Res.string.import_progress_bytes_receiving
        ImportProgress.Phase.DOWNLOADING -> Res.string.import_progress_bytes_downloading
        else -> Res.string.import_progress_bytes_unpacking
    }

internal val ImportProgress.Phase.bytesSoFarLabel
    get() = when (this) {
        ImportProgress.Phase.RECEIVING -> Res.string.import_progress_bytes_receiving_so_far
        ImportProgress.Phase.DOWNLOADING -> Res.string.import_progress_bytes_downloading_so_far
        else -> Res.string.import_progress_bytes_unpacking_so_far
    }

/** What the import screen calls the phase, and the notification the work it is part of. */
internal val ImportProgress.Phase.label
    get() = when (this) {
        ImportProgress.Phase.RECEIVING -> Res.string.import_progress_receiving
        ImportProgress.Phase.DOWNLOADING -> Res.string.import_progress_downloading
        ImportProgress.Phase.READING_PAGE -> Res.string.import_progress_reading_page
        ImportProgress.Phase.UNPACKING -> Res.string.import_progress_unpacking
        ImportProgress.Phase.DOWNLOADING_PICTURES -> Res.string.import_progress_downloading_pictures
        ImportProgress.Phase.READING -> Res.string.import_progress_reading
        ImportProgress.Phase.COMPARING -> Res.string.import_progress_comparing
        ImportProgress.Phase.IMPORTING -> Res.string.import_progress_importing
        ImportProgress.Phase.FINISHING -> Res.string.import_progress_finishing
    }
