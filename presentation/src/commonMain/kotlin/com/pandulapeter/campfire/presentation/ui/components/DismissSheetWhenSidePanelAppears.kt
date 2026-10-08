/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * The panel and the sheet are the same controls shown two different ways, so a window resize that grows the panel
 * into view has to close whichever sheet was covering the screen instead of leaving both on screen at once.
 */
@Composable
internal fun DismissSheetWhenSidePanelAppears(
    isSidePanelVisible: Boolean,
    isSheetVisible: Boolean,
    onDismiss: () -> Unit,
) = LaunchedEffect(isSidePanelVisible) {
    if (isSidePanelVisible && isSheetVisible) onDismiss()
}
