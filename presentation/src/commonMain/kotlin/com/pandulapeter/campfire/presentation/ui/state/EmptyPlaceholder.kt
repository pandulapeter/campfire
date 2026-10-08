/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.presentation.ui.components.Placeholder

/**
 * What an empty list has in its place: it is only an error once the load that would have filled it has actually
 * failed, and only [whenEmpty] once a load has finished - until then it is still loading, and saying anything
 * else would have the screen answer a question it cannot answer yet.
 *
 * @param isImporting An empty library with an import running is a library being filled rather than an empty
 *   one, and is worth the same answer as a scan that has not finished. It is what keeps the first launch of the
 *   app from flashing "Your library is empty" over the songs it is planting, and any import into an empty
 *   library from doing the same.
 */
internal fun DataState<ScreenData>.emptyPlaceholder(whenEmpty: Placeholder, isImporting: Boolean) = when {
    this is DataState.Loading || isImporting -> Placeholder.LOADING
    this is DataState.Failure -> Placeholder.ERROR
    else -> whenEmpty
}
