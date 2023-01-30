package com.pandulapeter.campfire.shared.ui.catalogue.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireIcons
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings

@Composable
internal fun HeaderItem(
    modifier: Modifier = Modifier,
    text: String
) = Text(
    modifier = modifier.padding(horizontal = 8.dp, vertical = 16.dp).fillMaxWidth(),
    text = text,
    style = TextStyle.Default.copy(fontWeight = FontWeight.Bold),
    color = MaterialTheme.colors.primary
)

@Composable
internal fun SongItem(
    modifier: Modifier = Modifier,
    song: Song,
    onSongClicked: (Song) -> Unit
) = RoundedCard(
    modifier = modifier
) {
    Text(
        modifier = Modifier
            .clickable { onSongClicked(song) }
            .padding(8.dp)
            .fillMaxWidth(),
        text = "${song.artist} - ${song.title}"
    )
}

@Composable
internal fun CheckboxItem(
    modifier: Modifier = Modifier,
    text: String,
    isChecked: Boolean,
    onCheckedChanged: (Boolean) -> Unit
) = RoundedCard(
    modifier = modifier
) {
    Row(
        modifier = Modifier.clickable { onCheckedChanged(!isChecked) }
    ) {
        Checkbox(
            modifier = Modifier.align(Alignment.CenterVertically),
            checked = isChecked,
            onCheckedChange = onCheckedChanged
        )
        Spacer(
            modifier = Modifier.width(4.dp)
        )
        Text(
            modifier = Modifier.fillMaxWidth().align(Alignment.CenterVertically).padding(vertical = 16.dp).padding(end = 8.dp),
            text = text
        )
    }
}

@Composable
internal fun RadioButtonItem(
    modifier: Modifier = Modifier,
    text: String,
    isChecked: Boolean,
    onClick: () -> Unit
) = RoundedCard(
    modifier = modifier
) {
    Row(
        modifier = Modifier.clickable { onClick() }
    ) {
        RadioButton(
            modifier = Modifier.align(Alignment.CenterVertically),
            selected = isChecked,
            onClick = onClick
        )
        Spacer(
            modifier = Modifier.width(4.dp)
        )
        Text(
            modifier = Modifier.fillMaxWidth().align(Alignment.CenterVertically).padding(vertical = 16.dp).padding(end = 8.dp),
            text = text
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchItem(
    modifier: Modifier = Modifier,
    query: String,
    uiStrings: CampfireStrings,
    onQueryChanged: (String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        modifier = modifier,
        leadingIcon = {
            Icon(
                imageVector = CampfireIcons.search,
                contentDescription = uiStrings.songsSearch
            )
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Icon(
                    modifier = Modifier.wrapContentSize().padding(8.dp).clip(shape = CircleShape).clickable { onQueryChanged("") }.padding(8.dp),
                    imageVector = CampfireIcons.clear,
                    contentDescription = uiStrings.songsClear,
                )
            }
        },
        label = { Text(uiStrings.songsSearch) },
        singleLine = true,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            keyboardController?.hide()
        }),
        value = query,
        onValueChange = onQueryChanged
    )
}

@Composable
internal fun ClickableControlItem(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit
) = RoundedCard(
    modifier = modifier
) {
    Text(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        text = text
    )
}