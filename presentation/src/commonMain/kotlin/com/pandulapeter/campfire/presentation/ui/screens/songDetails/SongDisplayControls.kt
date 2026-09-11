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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.ic_text_decrease
import com.pandulapeter.campfire.presentation.resources.ic_text_increase
import com.pandulapeter.campfire.presentation.resources.song_details_display_options
import com.pandulapeter.campfire.presentation.resources.song_details_text_size
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_decrease
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_increase
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_reset
import com.pandulapeter.campfire.presentation.resources.song_details_transpose_down
import com.pandulapeter.campfire.presentation.resources.song_details_transpose_reset
import com.pandulapeter.campfire.presentation.resources.song_details_transpose_up
import com.pandulapeter.campfire.presentation.resources.song_details_transposition
import com.pandulapeter.campfire.presentation.resources.song_editor_transpose_text_down
import com.pandulapeter.campfire.presentation.resources.song_editor_transpose_text_up
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.SettingsSectionTitle
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource

/**
 * The transposition and text size steppers of the song details screen in a bottom sheet, for windows whose app bar
 * has no room for them.
 */
@Composable
internal fun SongDisplayControls(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SongDisplayControls,
) {
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val songTexts by viewModel.songTexts.collectAsStateWithLifecycle()
    val song = allSongs.firstOrNull { it.fileName == dialog.songFileName }
    val songTransposition = song?.let { transpositions[it.fileName, dialog.setlistFileName] } ?: 0
    val songText = song?.let { songTexts[it.fileName] }
    val chordSpelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
    // Memoized: the key comes from the parsed song, which is not worth re-deriving on every recomposition.
    val transposedKey = remember(songText, songTransposition, chordSpelling) {
        songText?.let { viewModel.renderSong(it, songTransposition, chordSpelling).metadata.key }
    }
    Column {
        SettingsSectionTitle(text = stringResource(Res.string.song_details_display_options))
        if (song?.hasChords == true && userPreferences?.isLyricsOnlyModeEnabled != true) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(Res.string.song_details_transposition)) },
                trailingContent = {
                    TranspositionControls(
                        transposition = songTransposition,
                        key = transposedKey,
                        onTranspositionChanged = { viewModel.setTransposition(song.fileName, dialog.setlistFileName, it) },
                    )
                },
            )
        }
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(stringResource(Res.string.song_details_text_size)) },
            trailingContent = {
                FontScaleControls(
                    fontScale = fontScale,
                    onFontScaleAdjusted = viewModel::adjustFontScale,
                    onFontScaleReset = { viewModel.setFontScale(CampfireViewModel.DEFAULT_FONT_SCALE) },
                )
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun TranspositionControls(
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    transposition: Int,
    /** The key the song sounds in after transposing, shown next to the amount when the file declares one. */
    key: String? = null,
    onTranspositionChanged: (Int) -> Unit,
) = Stepper(
    modifier = modifier,
    isCompact = isCompact,
    value = (if (transposition > 0) "+$transposition" else transposition.toString())
        .let { if (key.isNullOrBlank()) it else "$it $KEY_SEPARATOR $key" },
    isDefault = transposition == 0,
    decreaseIcon = painterResource(Res.drawable.ic_subtract),
    decreaseLabel = stringResource(Res.string.song_details_transpose_down),
    canDecrease = transposition > CampfireViewModel.MIN_TRANSPOSITION,
    onDecrease = { onTranspositionChanged(transposition - 1) },
    increaseIcon = painterResource(Res.drawable.ic_add),
    increaseLabel = stringResource(Res.string.song_details_transpose_up),
    canIncrease = transposition < CampfireViewModel.MAX_TRANSPOSITION,
    onIncrease = { onTranspositionChanged(transposition + 1) },
    resetLabel = stringResource(Res.string.song_details_transpose_reset),
    onReset = { onTranspositionChanged(0) },
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
    isCompact = true,
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
    isCompact: Boolean = false,
    fontScale: Float,
    onFontScaleAdjusted: (steps: Int) -> Unit,
    onFontScaleReset: () -> Unit,
) {
    val percentage = (fontScale * 100).roundToInt()
    Stepper(
        modifier = modifier,
        isCompact = isCompact,
        value = "$percentage%",
        isDefault = percentage == (CampfireViewModel.DEFAULT_FONT_SCALE * 100).roundToInt(),
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

/**
 * A decrease button, the current value (highlighted when it differs from the default, tapping it resets it) and an
 * increase button, in a tonal pill that keeps the three of them together: two of these sit next to each other in
 * the app bar of wide windows, where loose icon buttons would blend into one long row of controls.
 *
 * @param isCompact Trades the 48dp touch targets for a shorter pill. The app bar uses it, because there the height
 * of the buttons is what makes the two groups look oversized; the bottom sheet, which is what touch devices get,
 * does not.
 */
@Composable
private fun Stepper(
    modifier: Modifier = Modifier,
    isCompact: Boolean,
    value: String,
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
) {
    val height = if (isCompact) COMPACT_HEIGHT else DEFAULT_HEIGHT
    val buttonWidth = if (isCompact) COMPACT_BUTTON_WIDTH else DEFAULT_BUTTON_WIDTH
    val iconSize = if (isCompact) COMPACT_ICON_SIZE else DEFAULT_ICON_SIZE
    Surface(
        modifier = modifier.height(height),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        // The buttons are laid out at the size of the pill, so the touch target enforcement of the icon buttons has
        // to be lowered to match, or it would grow them back to 48dp and the pill would no longer fit them.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides minOf(height, buttonWidth)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepperButton(
                    width = buttonWidth,
                    height = height,
                    iconSize = iconSize,
                    icon = decreaseIcon,
                    label = decreaseLabel,
                    isEnabled = canDecrease,
                    onClick = onDecrease,
                )
                StepperValue(
                    value = value,
                    isDefault = isDefault,
                    resetLabel = resetLabel,
                    onReset = onReset,
                )
                StepperButton(
                    width = buttonWidth,
                    height = height,
                    iconSize = iconSize,
                    icon = increaseIcon,
                    label = increaseLabel,
                    isEnabled = canIncrease,
                    onClick = onIncrease,
                )
            }
        }
    }
}

@Composable
private fun StepperButton(
    width: Dp,
    height: Dp,
    iconSize: Dp,
    icon: Painter,
    label: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
) = IconButton(
    modifier = Modifier.size(width = width, height = height),
    enabled = isEnabled,
    onClick = onClick,
) {
    Icon(
        modifier = Modifier.size(iconSize),
        painter = icon,
        contentDescription = label,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepperValue(
    value: String,
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
        targetState = value,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
    ) { currentValue ->
        Text(
            modifier = Modifier
                .fillMaxHeight()
                .clickable(enabled = !isDefault && onReset != null, onClickLabel = resetLabel) { onReset?.invoke() }
                .widthIn(min = VALUE_MIN_WIDTH)
                .wrapContentHeight(),
            text = currentValue,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

private val DEFAULT_HEIGHT = 48.dp
private val DEFAULT_BUTTON_WIDTH = 48.dp
private val COMPACT_HEIGHT = 40.dp
private val COMPACT_BUTTON_WIDTH = 36.dp
private val VALUE_MIN_WIDTH = 44.dp
private val DEFAULT_ICON_SIZE = 24.dp
private val COMPACT_ICON_SIZE = 20.dp

private const val KEY_SEPARATOR = "\u00B7"

/** What the editor's stepper shows for a song whose file names no key. */
private const val UNKNOWN_KEY = "?"
