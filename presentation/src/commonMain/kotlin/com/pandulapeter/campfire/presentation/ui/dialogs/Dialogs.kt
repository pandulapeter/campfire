package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.delete
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.setlists_create
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist_confirmation
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist_title
import com.pandulapeter.campfire.presentation.resources.song_details_add_to_setlist
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.components.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.SettingsSectionTitle
import com.pandulapeter.campfire.presentation.ui.components.SongsControls
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongDisplayControls
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import com.pandulapeter.campfire.presentation.localization.stringResource

/**
 * Hosts whichever dialog or bottom sheet the view model asks for.
 */
@Composable
internal fun CampfireDialogs(
    viewModel: CampfireViewModel,
    urlOpener: (String) -> Unit
) {
    val visibleDialog by viewModel.visibleDialog.collectAsStateWithLifecycle()
    when (val dialog = visibleDialog) {
        CampfireViewModel.DialogType.NewSetlist -> NewSetlistDialog(
            onDismiss = viewModel::dismissDialog,
            onCreate = { title ->
                viewModel.createSetlist(title)
                viewModel.dismissDialog()
            }
        )

        CampfireViewModel.DialogType.SongsControls -> CampfireBottomSheet(onDismiss = viewModel::dismissDialog) {
            SongsControls(
                viewModel = viewModel,
                shouldIncludeSorting = true
            )
        }

        CampfireViewModel.DialogType.SetlistsControls -> CampfireBottomSheet(onDismiss = viewModel::dismissDialog) {
            SongsControls(
                viewModel = viewModel,
                shouldIncludeSorting = false
            )
        }

        is CampfireViewModel.DialogType.SetlistPicker -> SetlistPickerSheet(
            viewModel = viewModel,
            dialog = dialog
        )

        is CampfireViewModel.DialogType.SongDisplayControls -> CampfireBottomSheet(onDismiss = viewModel::dismissDialog) {
            SongDisplayControls(
                viewModel = viewModel,
                dialog = dialog
            )
        }

        is CampfireViewModel.DialogType.DeleteSetlist -> ConfirmationDialog(
            title = stringResource(Res.string.setlists_delete_setlist),
            text = stringResource(Res.string.setlists_delete_setlist_confirmation, dialog.setlist.title),
            confirmLabel = stringResource(Res.string.delete),
            onDismiss = viewModel::dismissDialog,
            onConfirm = {
                viewModel.deleteSetlist(dialog.setlist.id)
                viewModel.dismissDialog()
            }
        )

        null -> Unit
    }
}

@Composable
private fun ConfirmationDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = {
        TextButton(
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            onClick = onConfirm
        ) { Text(confirmLabel) }
    },
    dismissButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
    }
)

@Composable
private fun NewSetlistDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    val isValid = title.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.setlists_new_setlist)) },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = title,
                onValueChange = { title = it.replace("\n", "").take(MAX_SETLIST_TITLE_LENGTH) },
                label = { Text(stringResource(Res.string.setlists_new_setlist_title)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (isValid) onCreate(title) })
            )
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onCreate(title) }
            ) { Text(stringResource(Res.string.setlists_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetlistPickerSheet(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SetlistPicker
) {
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    CampfireBottomSheet(onDismiss = viewModel::dismissDialog) { sheetState, dismiss ->
        val coroutineScope = rememberCoroutineScope()
        SettingsSectionTitle(text = stringResource(Res.string.song_details_add_to_setlist))
        setlists.forEach { setlist ->
            CheckboxListItem(
                title = setlist.title,
                isEnabled = dialog.currentSetlistId != setlist.id,
                isChecked = dialog.songId in setlist.songIds,
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        viewModel.addSongToSetlist(songId = dialog.songId, setlistId = setlist.id)
                    } else {
                        viewModel.removeSongFromSetlist(songId = dialog.songId, setlistId = setlist.id)
                    }
                }
            )
        }
        ActionListItem(
            title = stringResource(Res.string.setlists_new_setlist),
            icon = painterResource(Res.drawable.ic_add),
            onClick = {
                coroutineScope.launch {
                    sheetState.hide()
                    viewModel.showDialog(CampfireViewModel.DialogType.NewSetlist)
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampfireBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable (sheetState: SheetState, dismiss: () -> Unit) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        content(sheetState) {
            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampfireBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) = CampfireBottomSheet(onDismiss) { _, _ -> content() }

private const val MAX_SETLIST_TITLE_LENGTH = 40
