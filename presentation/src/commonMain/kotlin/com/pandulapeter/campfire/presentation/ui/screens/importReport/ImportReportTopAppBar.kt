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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.ui.components.CampfireTopAppBar
import org.jetbrains.compose.resources.painterResource

/**
 * Close, which drops an import that has not started writing and leaves anything else, the title of the stage the
 * import is at, and under it, where there is one, [subtitle]: what is being imported.
 */
@Composable
internal fun ImportReportTopAppBar(
    title: String,
    subtitle: String?,
    onClose: () -> Unit,
) = CampfireTopAppBar(
    navigationIcon = {
        IconButton(onClick = onClose) {
            Icon(
                painter = painterResource(Res.drawable.ic_clear),
                contentDescription = stringResource(Res.string.close),
            )
        }
    },
    title = {
        AnimatedContent(
            targetState = title to subtitle,
            transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
        ) { (title, subtitle) ->
            Column {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    },
)
