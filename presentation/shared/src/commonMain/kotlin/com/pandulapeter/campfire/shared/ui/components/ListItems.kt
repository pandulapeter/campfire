package com.pandulapeter.campfire.shared.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.shared.resources.Res
import com.pandulapeter.campfire.shared.resources.songs_lyrics_only
import com.pandulapeter.campfire.shared.ui.theme.CampfireIcons
import com.pandulapeter.campfire.shared.localization.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SongListItem(
    modifier: Modifier = Modifier,
    song: Song,
    isDownloaded: Boolean,
    isBeingDragged: Boolean = false,
    onClick: () -> Unit
) {
    val alpha by animateFloatAsState(if (isDownloaded) 1f else 0.6f, MaterialTheme.motionScheme.defaultEffectsSpec())
    val containerColor by animateColorAsState(
        if (isBeingDragged) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        MaterialTheme.motionScheme.defaultEffectsSpec()
    )
    ListItem(
        modifier = modifier.clickable(onClick = onClick).alpha(alpha),
        colors = ListItemDefaults.colors(containerColor = containerColor),
        headlineContent = {
            Text(
                text = song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = song.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = if (song.hasChords) null else {
            {
                Text(
                    text = stringResource(Res.string.songs_lyrics_only),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/**
 * Header of a list section: a raised pill that floats above the items scrolling underneath it when used as a sticky
 * header.
 */
@Composable
internal fun SectionHeader(
    modifier: Modifier = Modifier,
    text: String
) = Box(
    modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
internal fun SettingsSectionTitle(
    modifier: Modifier = Modifier,
    text: String
) = Text(
    modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    text = text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary
)

@Composable
internal fun SwitchListItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) = ListItem(
    modifier = modifier.toggleable(value = isChecked, role = Role.Switch, onValueChange = onCheckedChange),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    supportingContent = description?.let { { Text(it) } },
    trailingContent = { Switch(checked = isChecked, onCheckedChange = null) }
)

@Composable
internal fun CheckboxListItem(
    modifier: Modifier = Modifier,
    title: String,
    isChecked: Boolean,
    isEnabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) = ListItem(
    modifier = modifier
        .toggleable(value = isChecked, enabled = isEnabled, role = Role.Checkbox, onValueChange = onCheckedChange)
        .alpha(if (isEnabled) 1f else 0.5f),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    leadingContent = { Checkbox(checked = isChecked, enabled = isEnabled, onCheckedChange = null) }
)

@Composable
internal fun RadioButtonListItem(
    modifier: Modifier = Modifier,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) = ListItem(
    modifier = modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    leadingContent = { RadioButton(selected = isSelected, onClick = null) }
)

@Composable
internal fun LinkListItem(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) = ListItem(
    modifier = modifier.clickable(onClick = onClick),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    leadingContent = { Icon(imageVector = icon, contentDescription = null) },
    trailingContent = {
        Icon(
            imageVector = CampfireIcons.openInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
)

@Composable
internal fun ActionListItem(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) = ListItem(
    modifier = modifier.clickable(onClick = onClick),
    colors = ListItemDefaults.colors(
        containerColor = Color.Transparent,
        headlineColor = MaterialTheme.colorScheme.primary,
        leadingIconColor = MaterialTheme.colorScheme.primary
    ),
    headlineContent = { Text(title) },
    leadingContent = { Icon(imageVector = icon, contentDescription = null) }
)

@Composable
internal fun EmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    hint: String
) = Column(
    modifier = modifier.padding(32.dp),
    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
) {
    Icon(
        modifier = Modifier.padding(bottom = 16.dp).alpha(0.6f),
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
    )
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
    Text(
        modifier = Modifier.padding(top = 4.dp),
        text = hint,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}
