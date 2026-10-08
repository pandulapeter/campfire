/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandulapeter.campfire.presentation.ui.contentEdges
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.platform.bounceHorizontalScroll
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.theme.LocalMonospaceFontFamily
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * The text itself. Monospaced, because a tab or a grid only lines up in one and because a ChordPro document is
 * source rather than prose.
 *
 * The text size preference deliberately does not reach here: it is how large the lyrics are read from across a
 * room, and this is the source of the file rather than the song. Scaling it would also move the columns of a tab
 * away from the width the monospaced font is keeping them at.
 *
 * Lines are never wrapped: the staff lines of a tab only stay in their columns while each of them is one line on
 * screen, and a source file is read by its lines, which a wrapped one shows as two. A line wider than the pane is
 * scrolled to sideways instead.
 *
 * @param fieldModifier Applied innermost, right over the field's own focus target: the sideways scrolling container
 * around it is a focus group, so a focus callback or requester outside it would see that group rather than the field.
 * @param scrollState How far the field is scrolled down, hoisted so that it survives the pane being composed again.
 * @param horizontalScrollState How far the field is scrolled sideways, hoisted for the same reason.
 */
@Composable
internal fun ChordProTextField(
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    textFieldState: TextFieldState,
    isCompactTyping: Boolean,
    scrollState: ScrollState,
    horizontalScrollState: ScrollState,
    contentPadding: PaddingValues,
) {
    val colorScheme = MaterialTheme.colorScheme
    val secondAccentColor = LocalSecondAccentColor.current
    val tokenCache = remember { ChordProTokenCache() }
    val outputTransformation = remember(colorScheme, secondAccentColor, tokenCache) {
        ChordProOutputTransformation.of(
            tokenCache = tokenCache,
            primaryColor = colorScheme.primary,
            chordColor = secondAccentColor,
            secondaryColor = colorScheme.onSurfaceVariant,
            outlineColor = colorScheme.outline,
            errorColor = colorScheme.error,
        )
    }
    val bodyLarge = MaterialTheme.typography.bodyLarge
    // The room after the last line is inside the scroll, as at the end of every list, so the text runs under the
    // navigation bar and comes clear of it once scrolled to the end. It is part of the field (its decorator), so a
    // press there still places the caret, and it never changes while the field is scrolled: a viewport resized at the
    // end of a drag sends the scroll back to the caret and interrupts the fling.
    val restingBottomInset = WindowInsets.contentEdges.asPaddingValues().calculateBottomPadding()
    val layoutDirection = LocalLayoutDirection.current
    BasicTextField(
        modifier = modifier
            // The keyboard reaches the field only through the content padding this screen was handed, see CampfireApp,
            // and only once it reaches above the navigation bar it covers, which is when the padding outgrows that bar:
            // the viewport then ends at the keyboard, so the caret the field brings into view is never behind it.
            .padding(EditorFieldPadding(contentPadding = contentPadding, restingBottomInset = restingBottomInset))
            .fadingTopEdge(scrollState, MaterialTheme.colorScheme.background)
            // The field's own scrolling cannot hold any room after the last line, so it is never given less height than
            // its text and the scrolling is a container around it, one per axis. The vertical one hands its viewport
            // height on as the minimum, so a short song is still a field down to the bottom of the pane. The sideways
            // one measures the field against an unbounded width, which is what keeps the text from wrapping, and
            // still passes the pane's width on as the minimum, so the whole pane stays the field and a press
            // anywhere in it places the caret. The field asks its ancestors to bring the caret into view as it moves,
            // so both follow the typing on their own.
            .bounceVerticalScroll(scrollState)
            .bounceHorizontalScroll(horizontalScrollState)
            .then(fieldModifier),
        state = textFieldState,
        // The landscape keyboard leaves the smallest phone about 150 dp, much of it taken by the 48 dp title row and the
        // field's padding: a tighter leading fits three lines in what is left without making the letters any smaller.
        textStyle = bodyLarge.copy(
            lineHeight = if (isCompactTyping) 18.sp else bodyLarge.lineHeight,
            fontFamily = LocalMonospaceFontFamily.current,
            color = colorScheme.onSurface,
        ),
        // Autocorrect and automatic capitalization fight with a format whose words are "[Am]" and "{start_of_verse}".
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        lineLimits = TextFieldLineLimits.MultiLine(),
        outputTransformation = outputTransformation,
        cursorBrush = SolidColor(colorScheme.primary),
        // The margins are part of the field rather than of the scrolling around it, so that the field starts at the
        // edge of what it is scrolled in: the focus brings a field wider than the pane into view by its start edge,
        // which would scroll a margin outside it out of sight on every tap that places the caret.
        decorator = { innerTextField ->
            Box(
                modifier = Modifier.padding(
                    start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    bottom = restingBottomInset + 32.dp,
                ),
                propagateMinConstraints = true,
            ) {
                innerTextField()
            }
        },
    )
}

/**
 * The keyboard's part of the padding the editor's field is handed, asked for while the field is laid out rather than
 * while it is composed: it changes on every frame the keyboard slides for, and reading it while composing would
 * compose the whole field again on each of those frames.
 *
 * The handed padding never falls below the navigation bar, [restingBottomInset], which the field reaches under, so
 * until the keyboard rises past that bar there is nothing to take off; from then on it is the keyboard's height
 * whole, since the keyboard covers the bar rather than pushing it up.
 */
@Stable
private class EditorFieldPadding(
    private val contentPadding: PaddingValues,
    private val restingBottomInset: Dp,
) : PaddingValues {

    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp

    override fun calculateTopPadding() = 0.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp

    override fun calculateBottomPadding() = contentPadding.calculateBottomPadding().let { if (it > restingBottomInset) it else 0.dp }
}
