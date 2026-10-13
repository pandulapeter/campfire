/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_check
import org.jetbrains.compose.resources.painterResource

/**
 * One answer of an onboarding page, as a card big enough to be hit without aiming: an instrument of a group of which one
 * is picked ([Role.RadioButton]), or a feature of a list of which any are kept ([Role.Checkbox]). The selection is said
 * three ways at once, the container, the border and the mark at the end, so that it reads in either theme, in every one
 * of the colors, and to somebody who cannot tell the container colors apart.
 *
 * @param icon Drawn in the content color at the start, decoration only: the title says what the card is.
 * @param preview Drawn before the mark at the end, an example of what the answer changes, kept out of the icon's place
 *   so that a preview wider than an icon does not push its card's text out of line with the other cards'.
 */
@Composable
internal fun OnboardingChoiceCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    preview: (@Composable () -> Unit)? = null,
    isSelected: Boolean,
    role: Role,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(if (isSelected) colors.secondaryContainer else colors.surfaceContainerLow)
    val contentColor by animateColorAsState(if (isSelected) colors.onSecondaryContainer else colors.onSurface)
    val borderColor by animateColorAsState(if (isSelected) colors.primary else colors.outlineVariant)
    val borderWidth by animateDpAsState(if (isSelected) 2.dp else 1.dp)
    // Clipped before it is made selectable, so that the ripple stays inside the rounded card rather than filling the
    // rectangle around it.
    Surface(
        modifier = modifier.clip(MaterialTheme.shapes.large).selectable(selected = isSelected, role = role, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Row(
            modifier = Modifier.defaultMinSize(minHeight = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            icon?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) contentColor else colors.onSurfaceVariant,
                    )
                }
            }
            preview?.invoke()
            SelectionMark(isSelected = isSelected)
        }
    }
}

/** A disc that fills with a check as the card is picked, and is an empty ring while it is not. */
@Composable
private fun SelectionMark(
    isSelected: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val fillColor by animateColorAsState(if (isSelected) colors.primary else colors.primary.copy(alpha = 0f))
    val ringColor by animateColorAsState(if (isSelected) colors.primary else colors.outline)
    val checkAlpha by animateFloatAsState(if (isSelected) 1f else 0f)
    Surface(
        modifier = Modifier.size(24.dp),
        shape = CircleShape,
        color = fillColor,
        border = BorderStroke(2.dp, ringColor),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                modifier = Modifier.size(16.dp).alpha(checkAlpha),
                painter = painterResource(Res.drawable.ic_check),
                contentDescription = null,
                tint = colors.onPrimary,
            )
        }
    }
}
