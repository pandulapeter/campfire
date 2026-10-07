/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.ChordProMetadataFields.Field
import com.pandulapeter.campfire.chordpro.ChordProTime
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_playing_description_library
import com.pandulapeter.campfire.presentation.resources.song_details_playing_description_setlist
import com.pandulapeter.campfire.presentation.resources.song_details_playing_edit
import com.pandulapeter.campfire.presentation.resources.song_details_playing_key_transposed
import com.pandulapeter.campfire.presentation.resources.song_details_playing_override_library
import com.pandulapeter.campfire.presentation.resources.song_details_playing_override_reset
import com.pandulapeter.campfire.presentation.resources.song_details_playing_override_setlist
import com.pandulapeter.campfire.presentation.resources.song_details_playing_override_transposition
import com.pandulapeter.campfire.presentation.resources.song_details_playing_range
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.rememberClearTextButton
import com.pandulapeter.campfire.presentation.ui.metronome.TimeSignaturePicker
import com.pandulapeter.campfire.presentation.ui.metronome.effectiveTempo
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.platform.numericPlatformImeOptions
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.effectiveCapo
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.transpositionLabel
import kotlin.math.absoluteValue

/**
 * "Song defaults": what the song's own file declares for the four values it is played by — its key, its capo, its
 * tempo and its time signature — opened from the song details editing menu and from the About the song sheet's Song
 * defaults group, edited as a draft and written on Save, the fields changed and only those. Opened from the editor's
 * overflow menu it writes into the text being typed instead, like the editor's other metadata sheets.
 *
 * The song's first section has a stepper for three of them, and those never touch the file: they override it for the
 * setlist the song is read through, or in the preferences for a song opened from the library. That difference is the
 * whole of what makes the four controls confusing, and the page has no room to explain it without putting the app's
 * own words among the song's, so it is explained here, where the two meet: the sheet opens with a line saying which
 * is which, and where the song is being played differently from its file, a card names how and takes it back. An
 * override equal to a value set here is no longer shown or named, since it plays what the file says; it is kept, so a
 * setlist that was told 100 still says 100 if the file later moves to 110. The editor has no steppers and reads the
 * song through no setlist, so there the sheet is the fields alone.
 *
 * Every field may be left empty, which declares nothing and lets the default stand — no key, no capo, the click's
 * [MetronomePattern.DEFAULT_BPM] and [TimeSignature.COMMON_TIME] — and the placeholders say what that default is.
 * Each field goes with its feature: the key and the capo with the chords, the tempo and the time signature with the
 * metronome, so a reader who switched one off is not asked about it here either. The key is typed in the reader's
 * notation. Like the metadata form it opens with no field focused, since it is opened to look the values up as often as
 * to change one.
 *
 * The key is the one the chords are written in, which a `{transpose}` the file opens with moves before anything is
 * shown: the page, the app bar and the stepper all name the moved one. Typed in from what the page reads, it would be
 * moved a second time, so where the file has one the key field says so.
 */
@Composable
internal fun SongPlayingDialog(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SongPlaying,
) {
    val offeredValues = dialog.values
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val shouldShowChords = userPreferences?.areChordsEnabled != false
    val shouldShowTempo = userPreferences?.isMetronomeEnabled != false
    var values by rememberSaveable(dialog.song.fileName, stateSaver = songPlayingSaver) { mutableStateOf(offeredValues) }
    // Read the way the click reads it, so that a {time: C} is the common time it stands for rather than nothing.
    val timeSignature = remember(values[Field.TIME]) {
        ChordProTime.parse(values[Field.TIME])?.let { (beats, unit) -> TimeSignature(beats, unit) } ?: TimeSignature.COMMON_TIME
    }
    val isValid = isValidSongPlayingDraft(
        capo = values[Field.CAPO].orEmpty(),
        tempo = values[Field.TEMPO].orEmpty(),
        offeredCapo = offeredValues[Field.CAPO].orEmpty(),
        offeredTempo = offeredValues[Field.TEMPO].orEmpty(),
        isCapoShown = shouldShowChords,
        isTempoShown = shouldShowTempo,
    )
    val scrollState = rememberScrollState()
    val fileTranspose = dialog.song.transpose
    val keyNote = if (fileTranspose == 0) {
        null
    } else {
        pluralStringResource(
            Res.plurals.song_details_playing_key_transposed,
            fileTranspose.absoluteValue,
            transpositionLabel(transposition = fileTranspose, key = null),
        )
    }
    val field: @Composable (Modifier, Field, ImeAction) -> Unit = { modifier, field, imeAction ->
        SongPlayingField(
            modifier = modifier,
            field = field,
            value = values[field].orEmpty(),
            note = keyNote.takeIf { field == Field.KEY },
            imeAction = imeAction,
            onValueChange = { value -> values = values + (field to value) },
        )
    }
    TextFieldBottomSheet(
        onDismissRequest = { viewModel.dismissSheet(dialog) },
        title = stringResource(Res.string.song_details_playing_edit),
        subtitle = songLabel(dialog.song),
        text = { contentPadding ->
            Column(
                modifier = Modifier.fillMaxWidth().fadingTopEdge(scrollState, sheetContainerColor()).bounceVerticalScroll(scrollState).padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!dialog.isEditorDraft) {
                    Text(
                        text = stringResource(
                            if (dialog.setlistFileName == null) {
                                Res.string.song_details_playing_description_library
                            } else {
                                Res.string.song_details_playing_description_setlist
                            },
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SongPlayingOverridesCard(
                        viewModel = viewModel,
                        song = dialog.song,
                        setlistFileName = dialog.setlistFileName,
                    )
                }
                // A field of a feature switched off is left out rather than shown: the value it holds is kept as the
                // file has it, since only the fields changed are written.
                if (shouldShowChords) {
                    field(Modifier.fillMaxWidth(), Field.KEY, ImeAction.Next)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (shouldShowChords) {
                        field(Modifier.weight(1f), Field.CAPO, if (shouldShowTempo) ImeAction.Next else ImeAction.Done)
                    }
                    if (shouldShowTempo) {
                        field(Modifier.weight(1f), Field.TEMPO, ImeAction.Done)
                    }
                }
                if (shouldShowTempo) {
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = stringResource(Field.TIME.label),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    TimeSignaturePicker(
                        timeSignature = timeSignature,
                        horizontalPadding = 0.dp,
                        onChange = { values = values + (Field.TIME to it.toString()) },
                    )
                }
            }
        },
        confirmButton = { close ->
            BottomSheetConfirmButton(
                enabled = isValid && SONG_PLAYING_FIELDS.any { values[it].orEmpty().trim() != offeredValues[it].orEmpty().trim() },
                onClick = {
                    viewModel.setSongPlaying(
                        fileName = dialog.song.fileName,
                        isEditorDraft = dialog.isEditorDraft,
                        values = values,
                        offeredValues = offeredValues,
                    )
                    close()
                },
            ) { Text(stringResource(Res.string.save)) }
        },
    )
}

/**
 * How the song is being played differently from its file where it is read, named one value at a time, and what takes
 * all of it back. Read live, so a reset empties it while whatever shows it is open.
 */
@Immutable
internal class SongPlayingOverrides(
    val labels: List<String>,
    val onReset: () -> Unit,
)

/**
 * The overrides of [song] read through [setlistFileName], or in the library where it is null. Only those
 * of the features switched on: the transposition and the capo go with the chords, and the transposition with a song
 * that has some, the tempo with the metronome, and an override nobody is shown is not one to be named or taken back
 * either.
 */
@Composable
internal fun songPlayingOverrides(
    viewModel: CampfireViewModel,
    song: Song,
    setlistFileName: String?,
): SongPlayingOverrides {
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val tempos by viewModel.tempos.collectAsStateWithLifecycle()
    val capos by viewModel.capos.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val shouldShowChords = userPreferences?.areChordsEnabled != false
    val shouldShowTempo = userPreferences?.isMetronomeEnabled != false
    val transposition = transpositions[song.fileName, setlistFileName].takeIf { shouldShowChords && song.hasChords } ?: 0
    val capo = effectiveCapo(song = song, setlistFileName = setlistFileName, capos = capos).takeIf { shouldShowChords }
    val tempo = effectiveTempo(song = song, setlistFileName = setlistFileName, tempos = tempos).takeIf { shouldShowTempo }
    val labels = listOfNotNull(
        transposition.takeIf { it != 0 }?.let {
            stringResource(Res.string.song_details_playing_override_transposition, transpositionLabel(transposition = it, key = null))
        },
        capo?.takeUnless { it.isDefault }?.let { stringResource(Res.string.song_details_capo, it.fret) },
        tempo?.takeUnless { it.isDefault }?.let { stringResource(Res.string.song_details_tempo, it.bpm.toString()) },
    )
    return SongPlayingOverrides(
        labels = labels,
        onReset = {
            if (transposition != 0) viewModel.resetTransposition(song.fileName, setlistFileName)
            if (capo?.isDefault == false) viewModel.resetCapo(song.fileName, setlistFileName)
            if (tempo?.isDefault == false) viewModel.resetTempo(song.fileName, setlistFileName)
        },
    )
}

/**
 * The card that names how the song is being played differently from its file where it is read, and takes all of it
 * back with one button. It reads the overrides live, so a reset folds it away while the sheet is open; the last values
 * it named stay in it while it does.
 */
@Composable
private fun SongPlayingOverridesCard(
    viewModel: CampfireViewModel,
    song: Song,
    setlistFileName: String?,
) {
    val overrides = songPlayingOverrides(viewModel = viewModel, song = song, setlistFileName = setlistFileName)
    var shownOverrides by remember { mutableStateOf(overrides.labels) }
    if (overrides.labels.isNotEmpty()) shownOverrides = overrides.labels
    AnimatedVisibility(
        visible = overrides.labels.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (setlistFileName == null) {
                                Res.string.song_details_playing_override_library
                            } else {
                                Res.string.song_details_playing_override_setlist
                            },
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        // The color the steppers draw an overridden value in, which is what ties the two together.
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(text = shownOverrides.joinToString("  •  "))
                }
                TextButton(onClick = overrides.onReset) { Text(stringResource(Res.string.song_details_playing_override_reset)) }
            }
        }
    }
}

/**
 * One typed value of the sheet. The capo and the tempo take digits alone and say their range under the field, turning
 * it into an error while what is typed is outside it; the key takes anything a directive can hold, as the metadata
 * form's fields do, and its placeholder-less emptiness is the song declaring no key. A [note] is said under the field
 * where no range is.
 */
@Composable
private fun SongPlayingField(
    modifier: Modifier = Modifier,
    field: Field,
    value: String,
    note: String?,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val isNumeric = field != Field.KEY
    val range = when (field) {
        Field.CAPO -> Song.CAPO_RANGE
        Field.TEMPO -> MetronomePattern.BPM_RANGE
        else -> null
    }
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = { newValue ->
            onValueChange(
                if (isNumeric) {
                    newValue.filter { it in '0'..'9' }.take(range?.last?.toString()?.length ?: Int.MAX_VALUE)
                } else {
                    newValue.filterNot { it == '{' || it == '}' || it == '\n' || it == '\r' }.take(MAX_KEY_LENGTH)
                },
            )
        },
        label = {
            Text(
                text = stringResource(field.label),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        placeholder = when (field) {
            Field.CAPO -> ({ Text(Song.CAPO_RANGE.first.toString()) })
            Field.TEMPO -> ({ Text(MetronomePattern.DEFAULT_BPM.toString()) })
            else -> null
        },
        supportingText = when {
            range != null -> ({ Text(stringResource(Res.string.song_details_playing_range, range.first, range.last)) })
            note != null -> ({ Text(note) })
            else -> null
        },
        isError = range != null && !isValidSongPlayingNumber(value, range),
        trailingIcon = rememberClearTextButton(isVisible = value.isNotEmpty(), onClear = { onValueChange("") }),
        singleLine = true,
        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = imeAction,
            platformImeOptions = numericPlatformImeOptions.takeIf { isNumeric },
        ),
    )
}

/**
 * Whether the sheet's typed numbers can be written: each field that is shown and was changed is empty or a whole number
 * within its range. A value the file already held is not the sheet's to judge - only the changed fields are written -
 * and a hidden one cannot have been changed.
 */
internal fun isValidSongPlayingDraft(
    capo: String,
    tempo: String,
    offeredCapo: String = "",
    offeredTempo: String = "",
    isCapoShown: Boolean = true,
    isTempoShown: Boolean = true,
) = (!isCapoShown || capo.trim() == offeredCapo.trim() || isValidSongPlayingNumber(capo, Song.CAPO_RANGE)) &&
    (!isTempoShown || tempo.trim() == offeredTempo.trim() || isValidSongPlayingNumber(tempo, MetronomePattern.BPM_RANGE))

private fun isValidSongPlayingNumber(value: String, range: IntRange) = value.isBlank() || value.trim().toIntOrNull()?.let { it in range } == true

/** The fields of the sheet, in the order the draft is saved in, which is what Android's saved state takes. */
private val SONG_PLAYING_FIELDS = listOf(
    Field.KEY,
    Field.CAPO,
    Field.TEMPO,
    Field.TIME,
)

private val songPlayingSaver = listSaver<Map<Field, String>, String>(
    save = { values -> SONG_PLAYING_FIELDS.map { values[it].orEmpty() } },
    restore = { saved -> SONG_PLAYING_FIELDS.zip(saved).toMap() },
)

/** Room for the longest key anybody writes (`C#m7b5`), and a little for a mode named in words. */
private const val MAX_KEY_LENGTH = 16
