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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.TranspositionKey
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
    dialog: CampfireViewModel.DialogType.SongDisplayControls
) {
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val song = allSongs.firstOrNull { it.id == dialog.songId }
    Column {
        SettingsSectionTitle(text = stringResource(Res.string.song_details_display_options))
        if (song?.hasChords == true && userPreferences?.isLyricsOnlyModeEnabled != true) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(Res.string.song_details_transposition)) },
                trailingContent = {
                    TranspositionControls(
                        transposition = transpositions[TranspositionKey(song.id, dialog.setlistId)] ?: 0,
                        onTranspositionChanged = { viewModel.setTransposition(song.id, dialog.setlistId, it) }
                    )
                }
            )
        }
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(stringResource(Res.string.song_details_text_size)) },
            trailingContent = {
                FontScaleControls(
                    fontScale = fontScale,
                    onFontScaleAdjusted = viewModel::adjustFontScale,
                    onFontScaleReset = { viewModel.setFontScale(CampfireViewModel.DEFAULT_FONT_SCALE) }
                )
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun TranspositionControls(
    transposition: Int,
    onTranspositionChanged: (Int) -> Unit
) = Stepper(
    value = if (transposition > 0) "+$transposition" else transposition.toString(),
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
    onReset = { onTranspositionChanged(0) }
)

@Composable
internal fun FontScaleControls(
    fontScale: Float,
    onFontScaleAdjusted: (steps: Int) -> Unit,
    onFontScaleReset: () -> Unit
) {
    val percentage = (fontScale * 100).roundToInt()
    Stepper(
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
        onReset = onFontScaleReset
    )
}

/**
 * A decrease button, the current value (highlighted when it differs from the default, tapping it resets it) and an
 * increase button in a row.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Stepper(
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
    resetLabel: String,
    onReset: () -> Unit
) = Row(
    verticalAlignment = Alignment.CenterVertically
) {
    IconButton(
        enabled = canDecrease,
        onClick = onDecrease
    ) {
        Icon(
            painter = decreaseIcon,
            contentDescription = decreaseLabel
        )
    }
    // A progress value instead of an animated color, so that the label follows the color scheme immediately while it
    // is animating between the light and the dark theme (a color animation would chase it and trail behind).
    val changedProgress by animateFloatAsState(
        if (isDefault) 0f else 1f,
        MaterialTheme.motionScheme.defaultEffectsSpec()
    )
    val color = lerp(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary, changedProgress)
    AnimatedContent(
        targetState = value,
        transitionSpec = { fadeIn() togetherWith fadeOut() }
    ) { currentValue ->
        Text(
            modifier = Modifier
                .defaultMinSize(minWidth = 40.dp)
                .clickable(enabled = !isDefault, onClickLabel = resetLabel, onClick = onReset)
                .padding(vertical = 8.dp),
            text = currentValue,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
    IconButton(
        enabled = canIncrease,
        onClick = onIncrease
    ) {
        Icon(
            painter = increaseIcon,
            contentDescription = increaseLabel
        )
    }
}
