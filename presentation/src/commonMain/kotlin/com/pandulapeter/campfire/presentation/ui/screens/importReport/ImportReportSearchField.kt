/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.ic_search
import com.pandulapeter.campfire.presentation.resources.import_files_empty
import com.pandulapeter.campfire.presentation.resources.import_report_clean
import com.pandulapeter.campfire.presentation.resources.import_report_converted
import com.pandulapeter.campfire.presentation.resources.import_report_edit
import com.pandulapeter.campfire.presentation.resources.import_report_left_out
import com.pandulapeter.campfire.presentation.resources.import_report_search
import com.pandulapeter.campfire.presentation.resources.import_status_duplicates
import com.pandulapeter.campfire.presentation.resources.import_status_failed
import com.pandulapeter.campfire.presentation.resources.import_status_left_out
import com.pandulapeter.campfire.presentation.resources.import_status_oversized
import com.pandulapeter.campfire.presentation.resources.import_status_partial
import com.pandulapeter.campfire.presentation.resources.import_status_setlists
import com.pandulapeter.campfire.presentation.resources.import_status_skipped
import com.pandulapeter.campfire.presentation.resources.import_status_songs
import com.pandulapeter.campfire.presentation.resources.import_status_unknown_failure
import com.pandulapeter.campfire.presentation.resources.import_status_unprocessed
import com.pandulapeter.campfire.presentation.resources.import_status_unreadable
import com.pandulapeter.campfire.presentation.resources.songs_clear
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.EDGE_FADE_SIZE
import com.pandulapeter.campfire.presentation.ui.components.SearchState
import com.pandulapeter.campfire.presentation.ui.components.appBar.TruncateSearchQuery
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource

/**
 * One line on a tonal pill, as the list screens' search field is, with the magnifier in front of it and a button that
 * empties it once there is something to empty. It never takes the focus by itself, since it is there whether or not
 * anybody means to search and a keyboard coming up over the result on a phone would hide what the screen is for;
 * Ctrl / Cmd + F gives it the caret through `Navigator.openCurrentSearch`, with what it holds selected.
 */
@Composable
internal fun ImportReportSearchField(
    modifier: Modifier = Modifier,
    searchState: SearchState,
    placeholder: String,
) = Surface(
    modifier = modifier
        .fillMaxWidth()
        .height(SEARCH_FIELD_HEIGHT),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(searchState) {
        searchState.focusRequests.collect {
            searchState.textFieldState.edit { selectAll() }
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    Row(
        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.padding(end = 8.dp),
            painter = painterResource(Res.drawable.ic_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BasicTextField(
            modifier = Modifier.weight(1f).padding(end = 8.dp).focusRequester(focusRequester),
            state = searchState.textFieldState,
            inputTransformation = TruncateSearchQuery,
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            // What is searched for is file names, which autocorrect has no dictionary for.
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
            onKeyboardAction = KeyboardActionHandler { keyboardController?.hide() },
            decorator = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (searchState.textFieldState.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        AnimatedVisibility(
            visible = searchState.textFieldState.text.isNotEmpty(),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            IconButton(onClick = { searchState.textFieldState.clearText() }) {
                Icon(
                    painter = painterResource(Res.drawable.ic_clear),
                    contentDescription = stringResource(Res.string.songs_clear),
                )
            }
        }
    }
}

private val SEARCH_FIELD_HEIGHT = 48.dp

/**
 * The search of the list, standing right above the files it narrows and pinned at the top of the list once they are
 * scrolled under it: always there rather than behind a button, since the list is the screen's whole content and a
 * name is what somebody comes to it to find. The rows fade out under it the way they fade out under a bar, by a
 * gradient drawn below the field over what passes under it, as strong as the rows have been scrolled under it.
 */
internal fun LazyListScope.searchField(
    searchState: SearchState,
    listState: LazyListState,
    index: Int,
) = stickyHeader(key = "search", contentType = "search") {
    val backgroundColor = MaterialTheme.colorScheme.background
    val fadeHeight = with(LocalDensity.current) { EDGE_FADE_SIZE.toPx() }
    Box(
        modifier = ItemWidth
            .drawWithContent {
                drawContent()
                val scrolledUnder = when {
                    listState.firstVisibleItemIndex > index -> fadeHeight
                    listState.firstVisibleItemIndex == index -> listState.firstVisibleItemScrollOffset.toFloat()
                    else -> 0f
                }
                val strength = (scrolledUnder / fadeHeight).coerceIn(0f, 1f)
                if (strength > 0f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(backgroundColor, backgroundColor.copy(alpha = 0f)),
                            startY = size.height,
                            endY = size.height + fadeHeight,
                        ),
                        topLeft = Offset(0f, size.height),
                        size = Size(size.width, fadeHeight),
                        alpha = strength,
                    )
                }
            }
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        ImportReportSearchField(
            searchState = searchState,
            placeholder = stringResource(Res.string.import_report_search),
        )
    }
}
