package com.pandulapeter.campfire.shared.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.shared.resources.Res
import com.pandulapeter.campfire.shared.resources.cancel
import com.pandulapeter.campfire.shared.resources.delete
import com.pandulapeter.campfire.shared.resources.remove
import com.pandulapeter.campfire.shared.resources.setlists_create
import com.pandulapeter.campfire.shared.resources.setlists_delete_setlist
import com.pandulapeter.campfire.shared.resources.setlists_delete_setlist_confirmation
import com.pandulapeter.campfire.shared.resources.setlists_new_setlist
import com.pandulapeter.campfire.shared.resources.setlists_new_setlist_title
import com.pandulapeter.campfire.shared.resources.settings_add
import com.pandulapeter.campfire.shared.resources.settings_add_new_database
import com.pandulapeter.campfire.shared.resources.settings_add_new_database_hint
import com.pandulapeter.campfire.shared.resources.settings_add_new_database_name
import com.pandulapeter.campfire.shared.resources.settings_add_new_database_url
import com.pandulapeter.campfire.shared.resources.settings_add_new_database_url_error
import com.pandulapeter.campfire.shared.resources.settings_remove_database
import com.pandulapeter.campfire.shared.resources.settings_remove_database_confirmation
import com.pandulapeter.campfire.shared.resources.song_details_add_to_setlist
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.components.ActionListItem
import com.pandulapeter.campfire.shared.ui.components.CheckboxListItem
import com.pandulapeter.campfire.shared.ui.components.SettingsSectionTitle
import com.pandulapeter.campfire.shared.ui.components.SongsControls
import com.pandulapeter.campfire.shared.ui.theme.CampfireIcons
import kotlinx.coroutines.launch
import com.pandulapeter.campfire.shared.localization.stringResource

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

        CampfireViewModel.DialogType.NewDatabase -> NewDatabaseDialog(
            onDismiss = viewModel::dismissDialog,
            onAdd = { name, url ->
                viewModel.addDatabase(name, url)
                viewModel.dismissDialog()
            },
            urlOpener = urlOpener
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

        is CampfireViewModel.DialogType.DeleteDatabase -> ConfirmationDialog(
            title = stringResource(Res.string.settings_remove_database),
            text = stringResource(Res.string.settings_remove_database_confirmation, dialog.database.name),
            confirmLabel = stringResource(Res.string.remove),
            onDismiss = viewModel::dismissDialog,
            onConfirm = {
                viewModel.removeDatabase(dialog.database)
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

@Composable
private fun NewDatabaseDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String) -> Unit,
    urlOpener: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    val isUrlValid = url.isBlank() || url.trim().isValidHttpUrl()
    val canAdd = name.isNotBlank() && url.isNotBlank() && isUrlValid
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_add_new_database)) },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    onValueChange = { name = it.replace("\n", "").take(MAX_DATABASE_NAME_LENGTH) },
                    label = { Text(stringResource(Res.string.settings_add_new_database_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = url,
                    onValueChange = { url = it.replace("\n", "") },
                    label = { Text(stringResource(Res.string.settings_add_new_database_url)) },
                    isError = !isUrlValid,
                    supportingText = if (isUrlValid) null else {
                        { Text(stringResource(Res.string.settings_add_new_database_url_error)) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canAdd) onAdd(name, url) })
                )
                TextButton(
                    modifier = Modifier.padding(top = 8.dp),
                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                    onClick = { urlOpener(ADD_DATABASE_HELP_URL) }
                ) {
                    Icon(
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                        imageVector = CampfireIcons.openInNew,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(Res.string.settings_add_new_database_hint))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canAdd,
                onClick = { onAdd(name, url) }
            ) { Text(stringResource(Res.string.settings_add)) }
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
            icon = CampfireIcons.add,
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
    val sheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
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

private fun String.isValidHttpUrl() = (startsWith("http://") || startsWith("https://")) && substringAfter("://").let { it.isNotBlank() && ' ' !in it }

private const val MAX_SETLIST_TITLE_LENGTH = 40
private const val MAX_DATABASE_NAME_LENGTH = 30
private const val ADD_DATABASE_HELP_URL = "https://pandulapeter.github.io/campfire/documents/adding-new-databases.html"
