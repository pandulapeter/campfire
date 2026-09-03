package com.pandulapeter.campfire.shared.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.pandulapeter.campfire.shared.resources.Res
import com.pandulapeter.campfire.shared.resources.songs_clear
import com.pandulapeter.campfire.shared.resources.songs_search
import com.pandulapeter.campfire.shared.ui.theme.CampfireIcons
import com.pandulapeter.campfire.shared.localization.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchField(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChanged: (String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    SearchBarDefaults.InputField(
        modifier = modifier,
        query = query,
        onQueryChange = { onQueryChanged(it.replace("\n", "")) },
        onSearch = { keyboardController?.hide() },
        expanded = false,
        onExpandedChange = {},
        placeholder = { Text(stringResource(Res.string.songs_search)) },
        leadingIcon = {
            Icon(
                imageVector = CampfireIcons.search,
                contentDescription = null
            )
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                // The text field would otherwise show the text cursor over the button on desktop.
                IconButton(
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Default, overrideDescendants = true),
                    onClick = { onQueryChanged("") }
                ) {
                    Icon(
                        imageVector = CampfireIcons.clear,
                        contentDescription = stringResource(Res.string.songs_clear)
                    )
                }
            }
        }
    )
}
