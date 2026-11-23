/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.data.model.importing.LoadedPage

/**
 * What the import screen says of a page that was not read, by what the user can do about it: one that never arrived is
 * worth trying again once the connection is back, and anything else is better opened in the browser and brought as the
 * page itself.
 */
internal fun pageFailureOf(reason: LoadedPage.NotRead.Reason) = when (reason) {
    LoadedPage.NotRead.Reason.UNREACHABLE -> ImportFailure.PAGE_UNREACHABLE
    LoadedPage.NotRead.Reason.UNREADABLE -> ImportFailure.PAGE_NOT_READ
}
