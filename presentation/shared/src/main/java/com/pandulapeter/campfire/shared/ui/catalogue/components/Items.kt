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
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.model.domain.Song

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
internal fun CollectionItem(
    modifier: Modifier = Modifier,
    collection: Collection,
    onCollectionClicked: (Collection) -> Unit
) = RoundedCard(
    modifier = modifier
) {
    Text(
        modifier = Modifier
            .clickable { onCollectionClicked(collection) }
            .padding(8.dp)
            .fillMaxWidth(),
        text = collection.title
    )
}

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
internal fun SearchItem(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChanged: (String) -> Unit
) = RoundedCard(
    modifier = modifier
) {
    TextField(
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Icon(
                    modifier = Modifier.wrapContentSize().padding(8.dp).clip(shape = CircleShape).clickable { onQueryChanged("") }.padding(8.dp),
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                )
            }
        },
        label = { Text("Search") },
        singleLine = true,
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