/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.widget

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_check
import com.pandulapeter.campfire.presentation.ui.theme.CampfireColorScheme
import com.pandulapeter.campfire.presentation.ui.theme.dynamicColorSchemePair
import com.pandulapeter.campfire.presentation.ui.theme.interfaceTypography
import org.jetbrains.compose.resources.painterResource

/**
 * A home screen widget's setup screen, which `:app:android`'s configuration activities show as a widget is added or
 * reconfigured: the snapshot's Set up the widget over the widget's own choices ([content]), and Done, which applies them
 * ([onDone]). Nothing is applied before Done: leaving the screen any other way leaves the widget as it was.
 *
 * Drawn in the colors the widget itself takes (the wallpaper's where the system has them, the app's own otherwise) and
 * in the system's dark mode, since the app's preferences are not read here, but in the app's typeface and in the
 * snapshot's language and direction, as the widget is.
 */
@Composable
internal fun WidgetSetupScaffold(
    snapshot: WidgetSnapshot,
    onDone: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val colorSchemePair = dynamicColorSchemePair() ?: CampfireColorScheme
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) colorSchemePair.dark else colorSchemePair.light,
        typography = interfaceTypography() ?: MaterialTheme.typography,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides if (snapshot.strings.isRightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box {
                    LazyColumn(contentPadding = contentPadding()) {
                        item(key = TITLE_KEY) {
                            Text(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                                text = snapshot.strings.setUpWidget,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                        content()
                    }
                    DoneButton(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        text = snapshot.strings.done,
                        onClick = onDone,
                    )
                }
            }
        }
    }
}

/** The system bars' and the keyboard's space around the list, and room under its end for Done to leave it uncovered. */
@Composable
private fun contentPadding(): PaddingValues {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = insets.calculateStartPadding(layoutDirection),
        top = insets.calculateTopPadding(),
        end = insets.calculateEndPadding(layoutDirection),
        bottom = insets.calculateBottomPadding() + DONE_BUTTON_ROOM,
    )
}

/** Described by its label, since Material's extended floating button clears the semantics of the text it draws. */
@Composable
private fun DoneButton(
    modifier: Modifier,
    text: String,
    onClick: () -> Unit,
) = ExtendedFloatingActionButton(
    modifier = modifier
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(16.dp)
        .semantics { contentDescription = text },
    onClick = onClick,
    icon = { Icon(painter = painterResource(Res.drawable.ic_check), contentDescription = null) },
    text = { Text(text = text) },
)

/** The list's own row's key, which no choice's key can be. */
private const val TITLE_KEY = "title"

private val DONE_BUTTON_ROOM = 88.dp
