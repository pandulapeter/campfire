/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.ic_save
import com.pandulapeter.campfire.presentation.resources.share_save_qr_code
import com.pandulapeter.campfire.presentation.resources.share_stop
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import org.jetbrains.compose.resources.painterResource

/**
 * What a shared link offers besides copying it: its QR code saved as a picture, and the end of the share, which asks
 * first. Folding out under the format once the item is shared, and away once it is not.
 */
@Composable
internal fun LinkOptions(
    isShared: Boolean,
    onSaveQrCode: () -> Unit,
    onStopSharing: () -> Unit,
) = AnimatedVisibility(isShared, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
    Column(Modifier.padding(top = 8.dp)) {
        ActionListItem(
            title = stringResource(Res.string.share_save_qr_code),
            icon = painterResource(Res.drawable.ic_save),
            isEmphasized = false,
            onClick = onSaveQrCode,
        )
        ActionListItem(
            title = stringResource(Res.string.share_stop),
            icon = painterResource(Res.drawable.ic_delete),
            isEmphasized = false,
            onClick = onStopSharing,
        )
    }
}
