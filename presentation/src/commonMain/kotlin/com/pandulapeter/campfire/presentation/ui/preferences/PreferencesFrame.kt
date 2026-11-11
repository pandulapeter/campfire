/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.LocalSheetHeightLag
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor
import com.pandulapeter.campfire.presentation.ui.settingsPage.MAX_COLUMN_WIDTH

/**
 * The Preferences sheet's [content] with the buttons that pick its page ([PreferencesPageButtons]) pinned under it: the
 * content scrolls by itself above them, fading out at its bottom edge as it would under a bar, so that another page is
 * picked from wherever this one has been scrolled to, and the buttons sit where the thumb is. The sheet takes each page's
 * height, its top edge moving as another is picked; the buttons stay on its bottom edge meanwhile, over the sheet's own
 * color, so that what the page has not uncovered yet passes under them. A single page has no buttons, and the content
 * reaches the bottom.
 *
 * The content hangs from the sheet's top and is measured at its new height at once, while the sheet's height animates to
 * it, so the buttons are placed under the content shifted by what that height still lags behind ([LocalSheetHeightLag]).
 * The shift is applied here rather than by a modifier of the buttons: this layout is placed again in the very pass in
 * which a new page changes the lag, as its content's size changed, whereas the buttons, the same size as before, are only
 * moved, and a read of their own would only be noticed a frame later. On Android a new page's first frame can take long
 * to compose, and with a stale shift it would hold the buttons under the new page's height, above the sheet's bottom edge.
 *
 * @param buttonsPadding Around the buttons: the window's inset under them, and the sides the content keeps.
 */
@Composable
internal fun PreferencesFrame(
    modifier: Modifier = Modifier,
    pages: List<PreferencesPage>,
    selectedPage: PreferencesPage,
    isKeyboard: Boolean,
    buttonsPadding: PaddingValues,
    onPageSelected: (PreferencesPage) -> Unit,
    content: @Composable () -> Unit,
) {
    val heightLag = LocalSheetHeightLag.current
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Box { content() }
            AnimatedVisibility(
                modifier = Modifier.background(sheetContainerColor()),
                visible = pages.size > 1,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                PreferencesPageButtons(
                    // As wide as a settings page's column at most, whose rows they pick.
                    modifier = Modifier.padding(buttonsPadding).widthIn(max = MAX_COLUMN_WIDTH),
                    pages = pages,
                    selectedPage = shownPreferencesPage(pages, selectedPage),
                    isKeyboard = isKeyboard,
                    onPageSelected = onPageSelected,
                )
            }
        },
    ) { (contentMeasurable, buttonsMeasurable), constraints ->
        val looseConstraints = constraints.copy(minHeight = 0)
        val buttons = buttonsMeasurable.measure(looseConstraints)
        val maxContentHeight = if (constraints.hasBoundedHeight) (constraints.maxHeight - buttons.height).coerceAtLeast(0) else Constraints.Infinity
        val content = contentMeasurable.measure(looseConstraints.copy(maxHeight = maxContentHeight))
        val height = (content.height + buttons.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, height) {
            content.placeRelative(0, 0)
            buttons.placeRelative(0, content.height - heightLag())
        }
    }
}
