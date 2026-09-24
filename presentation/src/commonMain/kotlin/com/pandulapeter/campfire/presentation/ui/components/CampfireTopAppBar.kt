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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The app bar of the song details and the editor: flat, in the screen's background color, whatever is scrolled
 * under it. What scrolls below it fades out into it instead (see [fadingTopEdge]), the way the list screens' cards
 * fade out under their pinned headers, rather than the bar tinting and lifting over it. The two list screens have no
 * bar of this kind, see [SearchableTopAppBar].
 *
 * A screen that needs more than one row of controls puts the rest in [bottomContent], which is drawn inside the same
 * surface: the editor's toolbar is part of the bar rather than a strip floating under it.
 *
 * The background is drawn by the wrapping [Surface] and the bar itself is transparent, because [TopAppBar] cross
 * fades its own container color with a spring of its own. That spring would chase the color scheme while it is
 * animating between the light and the dark theme, leaving the bar visibly trailing behind the rest of the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CampfireTopAppBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable ColumnScope.() -> Unit = {},
) = Surface(
    modifier = modifier,
    color = MaterialTheme.colorScheme.background,
) {
    Column {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
        bottomContent()
    }
}
