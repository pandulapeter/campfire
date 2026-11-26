/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.instrument

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument_choose
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_choose
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_standard
import com.pandulapeter.campfire.presentation.ui.components.controls.StepperDivider
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.TOOL_LINE_Z_INDEX
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.TabChromeElement
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.drawnContainerColor
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.rememberSharedTabChrome
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.sharedTabChrome
import com.pandulapeter.campfire.presentation.ui.songView.songControlHeight
import com.pandulapeter.campfire.presentation.ui.tuner.tuningLabel
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The instrument in the player's hands and its tuning as one tonal pill of two halves split by a stepper's divider, each
 * opening its own sheet: the instrument's name, and after it the tuning's ("Drop D", its note in the chord [notation])
 * for an instrument with more than one, so that the tuning is a choice in sight rather than one at the end of a long
 * list. Two choices rather than one, since diagrams for the wrong instrument or tuning look just as right as the ones for
 * the right one. It is as tall as a song's playing controls set in [style] are (`songControlHeight`), so that on a
 * song's page it lines up with the transposition stepper under it, unless a page of pickers gives it theirs ([height]), and keeps to one line: the tuning's name, the longer
 * of the two, is cut short first where the line is too narrow for both, down to a share of the pill, and then the
 * instrument's name (see [InstrumentPillMeasurePolicy]). The tuning half fades in and out with the instrument's
 * tunings and the pill resizes with it.
 *
 * @param height How tall the pill is: a song's playing controls' height, that of the picker chips it stands among, or
 *   the dock's controls'.
 * @param isStacked Whether the tuning half stands under the instrument's rather than after it, the two parted by a
 *   line, for a pill as tall as two lines (song details' dock beside its diagrams, see [InstrumentPillStackedHalves]),
 *   which then takes a [shape] with corners rounded less than a pill's.
 * @param border The outline of a pill on a surface its fill alone is too close to (song details' dock).
 * @param isTabChromePart Whether it is the instrument pill of a tool's tab, which a tab swap carries over to the other
 *   tool's as one pill (see SharedTabChrome), drawing its container in its stead.
 * @param tuningVisibility Where the tuning half fades in and out, for a caller that measures the pill and so has to know
 * when a leaving half is no longer laid out; its target is kept by the caller at whether there is a tuning to show.
 * @param stackedHalfPadding How far a stacked pill's names are from its sides, less where a narrow window wants the
 *   pill narrow (song details' dock on a phone).
 * @param trailing What the pill ends in after its halves, in the same container (song details' dock: the tuner, which
 *   tunes the strings the pill names); the halves give up their room to it first. A pill made wider than both centres
 *   them together.
 */
@Composable
internal fun InstrumentPill(
    modifier: Modifier = Modifier,
    instrument: UserPreferences.Instrument,
    tuning: InstrumentTuning?,
    notation: ChordNotation,
    style: TextStyle = MaterialTheme.typography.titleSmall,
    height: Dp = songControlHeight(style),
    isStacked: Boolean = false,
    shape: Shape = CircleShape,
    border: BorderStroke? = null,
    isEnabled: Boolean = true,
    isTabChromePart: Boolean = false,
    tuningVisibility: MutableTransitionState<Boolean>? = null,
    stackedHalfPadding: Dp = INSTRUMENT_PILL_HALF_PADDING,
    trailing: (@Composable () -> Unit)? = null,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
) {
    // Dimmed the way the list items of Settings are while their section is switched off. The container a tab swap draws
    // is outside the dimmed layer, so it is given the dimmed color, which an opaque one drawn at half its alpha is.
    val alpha = if (isEnabled) 1f else DISABLED_ALPHA
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val chrome = if (isTabChromePart) {
        rememberSharedTabChrome(element = TabChromeElement.INSTRUMENT_PILL, containerColor = containerColor.copy(alpha = containerColor.alpha * alpha))
    } else {
        null
    }
    Surface(
        modifier = modifier
            .sharedTabChrome(chrome = chrome, zIndex = TOOL_LINE_Z_INDEX, isContainerDrawnOutsideSwaps = true)
            .height(height)
            .alpha(alpha),
        shape = shape,
        color = chrome.drawnContainerColor(containerColor),
        border = border,
    ) {
        val halves = @Composable {
            if (isStacked) {
                InstrumentPillStackedHalves(
                    instrument = instrument,
                    tuning = tuning,
                    notation = notation,
                    style = style,
                    height = height,
                    horizontalPadding = stackedHalfPadding,
                    isEnabled = isEnabled,
                    onInstrumentClicked = onInstrumentClicked,
                    onTuningClicked = onTuningClicked,
                )
            } else {
                InstrumentPillHalves(
                    instrument = instrument,
                    tuning = tuning,
                    notation = notation,
                    style = style,
                    isEnabled = isEnabled,
                    tuningVisibility = tuningVisibility,
                    onInstrumentClicked = onInstrumentClicked,
                    onTuningClicked = onTuningClicked,
                )
            }
        }
        if (trailing == null) {
            halves()
        } else {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f, fill = false).fillMaxHeight()) { halves() }
                trailing()
            }
        }
    }
}

/** The halves of an [InstrumentPill], laid out by [InstrumentPillMeasurePolicy]. */
@Composable
private fun InstrumentPillHalves(
    instrument: UserPreferences.Instrument,
    tuning: InstrumentTuning?,
    notation: ChordNotation,
    style: TextStyle,
    isEnabled: Boolean,
    tuningVisibility: MutableTransitionState<Boolean>?,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
) {
    // Only the tuning half coming and going and a name changing animate the pill's width, each by itself. Animating every
    // change of the row's size instead would have the pill trail behind its text while a pinch scales a song's page.
    Layout(measurePolicy = InstrumentPillMeasurePolicy, content = {
        val shownTuning = shownTuning(instrument, tuning)
        // Kept while it fades out, so that it leaves as it was rather than as nothing. Not a state: nothing is redrawn
        // because of it.
        val lastShownTuning = remember { LastTuning() }
        shownTuning?.let { lastShownTuning.value = it }
        val tuningState = tuningVisibility ?: remember { MutableTransitionState(shownTuning != null) }.apply { targetState = shownTuning != null }
        // The room between the pill's rounded end and the name is the last half's, so that its press and ripple reach the
        // end of the pill; the instrument half gives it up as the tuning half comes in, at the pace that half grows.
        val instrumentEndPadding by animateDpAsState(
            targetValue = if (tuningState.targetState) INSTRUMENT_PILL_HALF_PADDING else INSTRUMENT_PILL_HALF_PADDING + INSTRUMENT_PILL_EDGE_PADDING,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        )
        InstrumentPillHalf(
            text = instrumentLabel(instrument),
            onClickLabel = stringResource(Res.string.instrument_choose),
            style = style,
            isEnabled = isEnabled,
            startPadding = INSTRUMENT_PILL_HALF_PADDING + INSTRUMENT_PILL_EDGE_PADDING,
            endPadding = instrumentEndPadding,
            onClick = onInstrumentClicked,
        )
        AnimatedVisibility(
            visibleState = tuningState,
            enter = fadeIn() + expandHorizontally(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()),
            exit = fadeOut() + shrinkHorizontally(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()),
        ) {
            val lastTuning = lastShownTuning.value ?: return@AnimatedVisibility
            // A leaving half names a tuning the instrument picked now does not have, so it neither opens the sheet nor is
            // offered as a button; it is not disabled for it, which would dim it in one frame in the middle of its fade.
            val isLeaving = shownTuning == null
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepperDivider()
                InstrumentPillHalf(
                    modifier = if (isLeaving) Modifier.clearAndSetSemantics { } else Modifier,
                    text = tuningName(lastTuning, notation),
                    onClickLabel = stringResource(Res.string.instrument_tuning_choose),
                    style = style,
                    isEnabled = isEnabled,
                    startPadding = INSTRUMENT_PILL_HALF_PADDING,
                    endPadding = INSTRUMENT_PILL_HALF_PADDING + INSTRUMENT_PILL_EDGE_PADDING,
                    onClick = { if (!isLeaving) onTuningClicked() },
                )
            }
        }
    })
}

/**
 * Lays out the halves of an [InstrumentPill] side by side, giving the tuning half its room first: whatever the instrument's
 * name leaves, but never less than its share of the pill (`tuningHalfMaxWidth`), so that the tuning's name is cut short
 * before the instrument's and is never left no room at all. The tuning half is measured at its animated width, so while
 * it expands the instrument gives up only what it actually takes. Both halves are measured from no minimum width, since
 * the `Surface` hands its own minimums on to its content, and at their natural widths where the width is unbounded,
 * which is also how a parent's intrinsic queries are answered (the sum of the halves', as a `Row` gives). A pill made
 * wider than its halves centres them.
 */
private val InstrumentPillMeasurePolicy = MeasurePolicy { measurables, constraints ->
    val halfConstraints = constraints.copy(minWidth = 0)
    val instrument = measurables[0]
    // A tuning half that has fully left emits nothing.
    val tuning = measurables.getOrNull(1)
    val tuningPlaceable: Placeable?
    val instrumentPlaceable: Placeable
    if (tuning == null || !constraints.hasBoundedWidth) {
        tuningPlaceable = tuning?.measure(halfConstraints)
        instrumentPlaceable = instrument.measure(halfConstraints)
    } else {
        val instrumentWidth = instrument.maxIntrinsicWidth(constraints.maxHeight)
        tuningPlaceable = tuning.measure(halfConstraints.copy(maxWidth = tuningHalfMaxWidth(constraints.maxWidth, instrumentWidth)))
        instrumentPlaceable = instrument.measure(halfConstraints.copy(maxWidth = constraints.maxWidth - tuningPlaceable.width))
    }
    val contentWidth = instrumentPlaceable.width + (tuningPlaceable?.width ?: 0)
    val width = constraints.constrainWidth(contentWidth)
    val height = constraints.constrainHeight(maxOf(instrumentPlaceable.height, tuningPlaceable?.height ?: 0))
    // Centred in a pill stretched wider than its halves (the side dock's column of controls).
    val start = maxOf(0, (width - contentWidth) / 2)
    layout(width, height) {
        instrumentPlaceable.placeRelative(start, (height - instrumentPlaceable.height) / 2)
        tuningPlaceable?.placeRelative(start + instrumentPlaceable.width, (height - tuningPlaceable.height) / 2)
    }
}

/**
 * One half of an [InstrumentPill]: the whole height of the pill is its touch target, its name crossfading as it changes.
 * On a touch screen a half still takes a touch 48 dp tall, since Compose extends a small target's touch area to the
 * minimum touch target size, as the `Stepper`'s buttons do; only its drawn height is the song's control height.
 */
@Composable
private fun InstrumentPillHalf(
    modifier: Modifier = Modifier,
    text: String,
    onClickLabel: String,
    style: TextStyle,
    isEnabled: Boolean,
    startPadding: Dp,
    endPadding: Dp,
    onClick: () -> Unit,
) = Box(
    modifier = modifier
        .fillMaxHeight()
        .clickable(enabled = isEnabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
        .padding(start = startPadding, end = endPadding),
    contentAlignment = Alignment.Center,
) {
    val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = text,
        transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
    ) { shownText ->
        Text(
            text = shownText,
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The halves of a stacked [InstrumentPill], [height] tall: the instrument over the tuning, each the whole width of the
 * pill (as wide as the wider name, or as the pill is made), a line between them stopping short of its sides as a
 * stepper's divider does. The tuning half grows in from nothing and shrinks away with the instrument's tunings, the
 * instrument half then taking the pill's whole height, so that the pill keeps its height.
 */
@Composable
private fun InstrumentPillStackedHalves(
    instrument: UserPreferences.Instrument,
    tuning: InstrumentTuning?,
    notation: ChordNotation,
    style: TextStyle,
    height: Dp,
    horizontalPadding: Dp,
    isEnabled: Boolean,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
) = Column(modifier = Modifier.width(IntrinsicSize.Max)) {
    val shownTuning = shownTuning(instrument, tuning)
    val lastShownTuning = remember { LastTuning() }
    shownTuning?.let { lastShownTuning.value = it }
    val tuningShare by animateFloatAsState(
        targetValue = if (shownTuning == null) 0f else 0.5f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    // Its corners are rounded less than a pill's ends, so the names need none of the room a round end takes.
    InstrumentPillHalf(
        modifier = Modifier.fillMaxWidth().weight(1f),
        text = instrumentLabel(instrument),
        onClickLabel = stringResource(Res.string.instrument_choose),
        style = style,
        isEnabled = isEnabled,
        startPadding = horizontalPadding,
        endPadding = horizontalPadding,
        onClick = onInstrumentClicked,
    )
    val lastTuning = lastShownTuning.value
    if (lastTuning != null && tuningShare > 0f) {
        val isLeaving = shownTuning == null
        Column(modifier = Modifier.fillMaxWidth().height(height * tuningShare).clipToBounds().alpha(tuningShare * 2)) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = horizontalPadding),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            InstrumentPillHalf(
                modifier = (if (isLeaving) Modifier.clearAndSetSemantics { } else Modifier).fillMaxWidth().weight(1f),
                text = tuningName(lastTuning, notation),
                onClickLabel = stringResource(Res.string.instrument_tuning_choose),
                style = style,
                isEnabled = isEnabled,
                startPadding = horizontalPadding,
                endPadding = horizontalPadding,
                onClick = { if (!isLeaving) onTuningClicked() },
            )
        }
    }
}

/**
 * What the tuning half of an [InstrumentPill] says for [tuning]: named in full where it is the standard one, which
 * "Standard" alone does not say out of the sheet's context. Asked of the tuning rather than of the instrument, since a
 * half fading out is still drawing the tuning of the instrument picked before, and the one picked now may have no
 * tunings at all (the keyboard, Other).
 */
@Composable
private fun tuningName(tuning: InstrumentTuning, notation: ChordNotation) = if (tuning.isStandard) {
    stringResource(Res.string.instrument_tuning_standard)
} else {
    tuningLabel(tuning, notation)
}

/** How opaque a disabled [InstrumentPill] is. */
private const val DISABLED_ALPHA = 0.5f

/** What the tuning half of [InstrumentPill] last named. */
private class LastTuning(var value: InstrumentTuning? = null)

/** The tuning an [InstrumentPill] names beside [instrument]: [tuning], where the instrument has more than one to pick from. */
internal fun shownTuning(instrument: UserPreferences.Instrument, tuning: InstrumentTuning?) = tuning?.takeIf { instrument.tunings.size > 1 }
