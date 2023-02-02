package com.pandulapeter.campfire.shared.ui.catalogue.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Checkbox
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireIcons
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings

@Composable
internal fun HeaderItem(
    modifier: Modifier = Modifier,
    text: String,
    shouldUseLargePadding: Boolean = true
) = Text(
    modifier = modifier.padding(horizontal = 8.dp, vertical = if (shouldUseLargePadding) 16.dp else 8.dp),
    text = text,
    style = TextStyle.Default.copy(fontWeight = FontWeight.Bold),
    color = MaterialTheme.colors.primary
)

@Composable
internal fun SongItem(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    song: Song,
    isDownloaded: Boolean,
    onSongClicked: (Song) -> Unit
) = RoundedCard(
    modifier = modifier.alpha(if (isDownloaded) 1f else 0.5f)
) {
    Column(
        modifier = Modifier
            .clickable { onSongClicked(song) }
            .padding(8.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = song.title
        )
        Spacer(
            modifier = Modifier.height(4.dp)
        )
        Row(
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.caption,
                text = song.artist
            )
            if (!song.hasChords) {
                Text(
                    style = MaterialTheme.typography.caption,
                    text = uiStrings.songsLyricsOnly,
                    textAlign = TextAlign.End,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
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

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterialApi::class)
@Composable
fun SearchItem(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    query: String,
    onQueryChanged: (String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val interactionSource = remember { MutableInteractionSource() }
    val colors = TextFieldDefaults.textFieldColors()

    BasicTextField(
        modifier = modifier.background(colors.backgroundColor(true).value, MaterialTheme.shapes.small),
        value = query,
        textStyle = LocalTextStyle.current.merge(TextStyle(color = colors.textColor(true).value)),
        cursorBrush = SolidColor(colors.cursorColor(false).value),
        onValueChange = onQueryChanged,
        interactionSource = interactionSource,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            keyboardController?.hide()
        }),
        singleLine = true
    ) {
        TextFieldDefaults.TextFieldDecorationBox(
            value = query,
            innerTextField = it,
            singleLine = true,
            enabled = true,
            visualTransformation = VisualTransformation.None,
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
                    IconButton(onClick = { onQueryChanged("") }) {
                        Icon(
                            imageVector = CampfireIcons.clear,
                            contentDescription = uiStrings.songsClear
                        )
                    }
                }
            },
            placeholder = { Text(text = uiStrings.songsSearch) },
            interactionSource = interactionSource,
            contentPadding = TextFieldDefaults.textFieldWithoutLabelPadding(
                top = 2.dp,
                bottom = 2.dp
            )
        )
    }
}

@Composable
internal fun ClickableControlItem(
    modifier: Modifier = Modifier,
    text: String,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) = RoundedCard(
    modifier = modifier
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Spacer(modifier = Modifier.width(4.dp))
            it()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            modifier = Modifier.padding(8.dp),
            text = text
        )
    }
}