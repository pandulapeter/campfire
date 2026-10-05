/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.ic_text_decrease
import com.pandulapeter.campfire.presentation.resources.ic_text_increase
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_decrease
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_increase
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_reset
import com.pandulapeter.campfire.presentation.resources.song_details_transpose_down
import com.pandulapeter.campfire.presentation.resources.song_details_transpose_reset
import com.pandulapeter.campfire.presentation.resources.song_details_transpose_up
import com.pandulapeter.campfire.presentation.resources.song_editor_transpose_text_down
import com.pandulapeter.campfire.presentation.resources.song_editor_transpose_text_up
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource

/**
 * The transposition stepper of the song details screen for [song], reading the key it takes the song to. It is in the
 * app bar wherever the bar has the room for it, and a row at the end of the bar's overflow menu otherwise
 * ([MenuStepperRow]), since it is set once for a song rather than played with; the key itself is on the header's accent
 * line with the capo either way.
 */
@Composable
internal fun SongTranspositionControls(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    song: Song,
    setlistFileName: String?,
    transposition: Int,
    chordSpelling: UserPreferences.ChordSpelling,
) = TranspositionControls(
    modifier = modifier,
    transposition = transposition,
    key = viewModel.renderKey(song = song, transposition = transposition, spelling = chordSpelling),
    onStep = { viewModel.stepTransposition(song.fileName, setlistFileName, it) },
    onReset = { viewModel.resetTransposition(song.fileName, setlistFileName) },
)

/**
 * A stepper as a row of an overflow menu: the name of what it sets, in a menu entry's style, at its paddings and its
 * height, so that it lines up with the entries under it, and the stepper at the end. The row is not an entry that is
 * chosen and takes no press itself, and the menu stays open while the stepper's buttons are pressed, so that a song is
 * taken up three semitones in three taps with the result in sight.
 */
@Composable
internal fun MenuStepperRow(
    label: String,
    stepper: @Composable () -> Unit,
) = Row(
    modifier = Modifier
        .widthIn(min = MENU_ROW_MIN_WIDTH)
        .height(MENU_ROW_HEIGHT)
        .padding(horizontal = MENU_ROW_HORIZONTAL_PADDING),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        modifier = Modifier
            .weight(1f)
            .padding(end = MENU_ROW_LABEL_GAP),
        text = label,
        style = MaterialTheme.typography.labelLarge,
    )
    stepper()
}

/**
 * The text size stepper, reading the live font scale in a scope of its own so that a pinch recomposes the
 * stepper rather than whatever holds it.
 */
@Composable
internal fun LiveFontScaleControls(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
) = FontScaleControls(
    modifier = modifier,
    fontScale = viewModel.fontScale,
    onFontScaleAdjusted = viewModel::adjustFontScale,
    onFontScaleReset = { viewModel.setFontScale(CampfireViewModel.DEFAULT_FONT_SCALE) },
)

@Composable
internal fun TranspositionControls(
    modifier: Modifier = Modifier,
    transposition: Int,
    /** The key the song sounds in after transposing, shown next to the amount when the file declares one. */
    key: String? = null,
    onStep: (semitones: Int) -> Unit,
    onReset: () -> Unit,
) = Stepper(
    modifier = modifier,
    value = transpositionLabel(transposition, key),
    isDefault = transposition == 0,
    decreaseIcon = painterResource(Res.drawable.ic_subtract),
    decreaseLabel = stringResource(Res.string.song_details_transpose_down),
    canDecrease = true,
    onDecrease = { onStep(-1) },
    increaseIcon = painterResource(Res.drawable.ic_add),
    increaseLabel = stringResource(Res.string.song_details_transpose_up),
    canIncrease = true,
    onIncrease = { onStep(1) },
    resetLabel = stringResource(Res.string.song_details_transpose_reset),
    onReset = onReset,
)

/**
 * The same stepper for the editor, where transposing rewrites the file instead of changing how it is read. There is
 * no amount to show and nothing to reset to, so the value is the key the song is in - or a question mark, since a
 * file that declares no `{key}` still has chords that move.
 *
 * @param isEnabled False for a song with no chords, where both buttons would rewrite nothing.
 */
@Composable
internal fun TextTranspositionControls(
    modifier: Modifier = Modifier,
    key: String?,
    isEnabled: Boolean,
    onTransposed: (semitones: Int) -> Unit,
) = Stepper(
    modifier = modifier,
    value = key?.takeIf { it.isNotBlank() } ?: UNKNOWN_KEY,
    isDefault = true,
    decreaseIcon = painterResource(Res.drawable.ic_subtract),
    decreaseLabel = stringResource(Res.string.song_editor_transpose_text_down),
    canDecrease = isEnabled,
    onDecrease = { onTransposed(-1) },
    increaseIcon = painterResource(Res.drawable.ic_add),
    increaseLabel = stringResource(Res.string.song_editor_transpose_text_up),
    canIncrease = isEnabled,
    onIncrease = { onTransposed(1) },
    resetLabel = null,
    onReset = null,
)

@Composable
internal fun FontScaleControls(
    modifier: Modifier = Modifier,
    fontScale: Float,
    onFontScaleAdjusted: (steps: Int) -> Unit,
    onFontScaleReset: () -> Unit,
) {
    val value = fontScaleLabel(fontScale)
    Stepper(
        modifier = modifier,
        value = value,
        // The step the value is nearest to, so that a tap, a shortcut or a reset still cross-fades while a pinch, which
        // changes the percentage on almost every frame, counts it up in place and cross-fades once per step at most.
        valueKey = (fontScale / CampfireViewModel.FONT_SCALE_STEP).roundToInt(),
        isDefault = value == fontScaleLabel(CampfireViewModel.DEFAULT_FONT_SCALE),
        decreaseIcon = painterResource(Res.drawable.ic_text_decrease),
        decreaseLabel = stringResource(Res.string.song_details_text_size_decrease),
        canDecrease = fontScale > CampfireViewModel.MIN_FONT_SCALE,
        onDecrease = { onFontScaleAdjusted(-1) },
        increaseIcon = painterResource(Res.drawable.ic_text_increase),
        increaseLabel = stringResource(Res.string.song_details_text_size_increase),
        canIncrease = fontScale < CampfireViewModel.MAX_FONT_SCALE,
        onIncrease = { onFontScaleAdjusted(1) },
        resetLabel = stringResource(Res.string.song_details_text_size_reset),
        onReset = onFontScaleReset,
    )
}

/** What the transposition stepper reads for [transposition], with the [key] it takes the song to where there is one. */
internal fun transpositionLabel(transposition: Int, key: String?) = (if (transposition > 0) "+$transposition" else transposition.toString())
    .let { if (key.isNullOrBlank()) it else "$it $KEY_SEPARATOR $key" }

private fun fontScaleLabel(fontScale: Float) = "${(fontScale * 100).roundToInt()}%"

/**
 * A decrease button, the current value (highlighted when it differs from the default, tapping it resets it) and an
 * increase button, in a tonal pill that keeps the three of them together: two of these sit one under the other in
 * the song details menu, the editor's sits among its pane controls, and the text size one sits alone in the app bar in
 * performance mode, where loose icon buttons would blend into the row of controls around them.
 *
 * The pill is shorter and its buttons narrower than Material's, since full height buttons look oversized in a menu row
 * and in the app bars. On a touch screen the buttons still take
 * a touch 48dp across, since Compose extends a small target's touch area to the minimum touch target size; only their
 * drawn size shrinks.
 *
 * @param valueKey What decides whether a new [value] cross-fades in or replaces the old one in place: only a change
 * of the key is animated.
 * @param repeatsOnHold Whether a button held down keeps stepping, faster the longer it is held, for a value that is a
 * long way from where it starts: fine for a tempo, while a semitone or a text size is a few taps away at most.
 */
@Composable
internal fun Stepper(
    modifier: Modifier = Modifier,
    value: String,
    valueKey: Any = value,
    isDefault: Boolean,
    decreaseIcon: Painter,
    decreaseLabel: String,
    canDecrease: Boolean,
    onDecrease: () -> Unit,
    increaseIcon: Painter,
    increaseLabel: String,
    canIncrease: Boolean,
    onIncrease: () -> Unit,
    resetLabel: String?,
    onReset: (() -> Unit)?,
    repeatsOnHold: Boolean = false,
) = Surface(
    modifier = modifier.height(HEIGHT),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    // The buttons are laid out at the size of the pill, so the touch target enforcement of the icon buttons has
    // to be lowered to match, or it would grow them back to 48dp and the pill would no longer fit them.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides minOf(HEIGHT, BUTTON_WIDTH)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton(
                icon = decreaseIcon,
                label = decreaseLabel,
                isEnabled = canDecrease,
                repeatsOnHold = repeatsOnHold,
                onClick = onDecrease,
            )
            StepperValue(
                value = value,
                valueKey = valueKey,
                isDefault = isDefault,
                resetLabel = resetLabel,
                onReset = onReset,
            )
            StepperButton(
                icon = increaseIcon,
                label = increaseLabel,
                isEnabled = canIncrease,
                repeatsOnHold = repeatsOnHold,
                onClick = onIncrease,
            )
        }
    }
}

/**
 * One end of a [Stepper]. Held down with [repeatsOnHold] it steps on its own after a moment, faster and faster, and the
 * release that ends a hold is not one more step: a tap still steps once, on its release, like any button, which is
 * also what a keyboard or a screen reader activating it does.
 */
@Composable
private fun StepperButton(
    icon: Painter,
    label: String,
    isEnabled: Boolean,
    repeatsOnHold: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val holdState = remember { HoldState() }
    if (repeatsOnHold) {
        val isPressed by interactionSource.collectIsPressedAsState()
        val currentOnClick by rememberUpdatedState(onClick)
        LaunchedEffect(isPressed) {
            if (!isPressed) return@LaunchedEffect
            holdState.hasRepeated = false
            delay(HOLD_REPEAT_DELAY_MILLIS)
            var interval = HOLD_REPEAT_FIRST_INTERVAL_MILLIS.toFloat()
            while (true) {
                holdState.hasRepeated = true
                currentOnClick()
                delay(interval.toLong())
                interval = maxOf(HOLD_REPEAT_FASTEST_INTERVAL_MILLIS.toFloat(), interval * HOLD_REPEAT_ACCELERATION)
            }
        }
    }
    IconButton(
        modifier = Modifier.size(width = BUTTON_WIDTH, height = HEIGHT),
        enabled = isEnabled,
        interactionSource = interactionSource,
        onClick = { if (holdState.hasRepeated) holdState.hasRepeated = false else onClick() },
    ) {
        Icon(
            modifier = Modifier.size(ICON_SIZE),
            painter = icon,
            contentDescription = label,
        )
    }
}

/** Whether the press being released was a hold that already stepped. Not a state: nothing is drawn from it. */
private class HoldState(var hasRepeated: Boolean = false)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepperValue(
    value: String,
    valueKey: Any,
    isDefault: Boolean,
    resetLabel: String?,
    onReset: (() -> Unit)?,
) {
    // A progress value instead of an animated color, so that the label follows the color scheme immediately while it
    // is animating between the light and the dark theme (a color animation would chase it and trail behind).
    val changedProgress by animateFloatAsState(
        if (isDefault) 0f else 1f,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val color = lerp(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary, changedProgress)
    AnimatedContent(
        modifier = Modifier.fillMaxHeight(),
        targetState = StepperLabel(value = value, key = valueKey),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it.key },
    ) { label ->
        Text(
            modifier = Modifier
                .fillMaxHeight()
                .clickable(enabled = !isDefault && onReset != null, onClickLabel = resetLabel) { onReset?.invoke() }
                .widthIn(min = VALUE_MIN_WIDTH)
                .wrapContentHeight(),
            text = label.value,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

/** A stepper's value with the key that decides whether a change of it is animated, see [Stepper]. */
private data class StepperLabel(
    val value: String,
    val key: Any,
)

private const val HOLD_REPEAT_DELAY_MILLIS = 400L
private const val HOLD_REPEAT_FIRST_INTERVAL_MILLIS = 150L
private const val HOLD_REPEAT_FASTEST_INTERVAL_MILLIS = 30L
private const val HOLD_REPEAT_ACCELERATION = 0.85f

private val HEIGHT = 40.dp
private val BUTTON_WIDTH = 36.dp
private val VALUE_MIN_WIDTH = 44.dp

/** How wide a stepper is drawn, at least, for the app bar that has to leave its title room beside one. */
internal val STEPPER_WIDTH = BUTTON_WIDTH * 2 + VALUE_MIN_WIDTH
private val ICON_SIZE = 20.dp
private val MENU_ROW_HEIGHT = 48.dp // A menu entry's own.
private val MENU_ROW_HORIZONTAL_PADDING = 12.dp // A menu entry's own.
private val MENU_ROW_LABEL_GAP = 16.dp
private val MENU_ROW_MIN_WIDTH = 280.dp

internal const val KEY_SEPARATOR = "\u00B7"

/** What the editor's stepper shows for a song whose file names no key. */
private const val UNKNOWN_KEY = "?"
