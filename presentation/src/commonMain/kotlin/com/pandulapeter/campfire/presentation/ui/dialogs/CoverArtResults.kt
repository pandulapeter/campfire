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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pandulapeter.campfire.data.model.domain.CoverArtCandidate
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cover_art_search_attribution
import com.pandulapeter.campfire.presentation.resources.cover_art_search_busy
import com.pandulapeter.campfire.presentation.resources.cover_art_search_failed
import com.pandulapeter.campfire.presentation.resources.cover_art_search_hint
import com.pandulapeter.campfire.presentation.resources.cover_art_search_loading
import com.pandulapeter.campfire.presentation.resources.cover_art_search_no_results
import com.pandulapeter.campfire.presentation.resources.ic_check
import com.pandulapeter.campfire.presentation.resources.retry
import com.pandulapeter.campfire.presentation.ui.components.CoverArt
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import org.jetbrains.compose.resources.painterResource
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent

/**
 * The grid of what was found, or what stands in its place: why there is nothing to show yet, that the search is
 * running or waiting for the service, that it failed, or that it found nothing worth showing. The catalogues answer
 * one after the other, so the grid is shown as soon as either has found something, the other's records joining it at
 * the end, with an indicator closing the grid for as long as one of them is still being waited for.
 *
 * The [fields] stay above the scrolling grid on larger windows and become its first full-width item on small ones.
 * The mode controls also scroll with the grid on small windows. The service credit and messages belong to the grid
 * in both layouts.
 */
@Composable
internal fun CoverArtResults(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    pinFields: Boolean,
    scrollingControls: (@Composable () -> Unit)?,
    state: CoverArtSearchState,
    unavailableKeys: Set<String>,
    selectedUrl: String?,
    onSelected: (CoverArtCandidate) -> Unit,
    onUnavailable: (CoverArtCandidate) -> Unit,
    onRetry: () -> Unit,
    fields: @Composable () -> Unit,
) {
    val results = (state as? CoverArtSearchState.Active)?.results
    val candidates = remember(results, unavailableKeys) { results?.candidates?.filterNot { it.key in unavailableKeys }.orEmpty() }
    val content = when {
        results == null -> ResultsContent.HINT
        candidates.isNotEmpty() -> ResultsContent.GRID
        // Only MusicBrainz ever asks to be waited for, so the line saying so is only true once nothing else is left.
        !results.isComplete -> if (results.busy.containsAll(results.pending)) ResultsContent.BUSY else ResultsContent.LOADING
        results.failed.isNotEmpty() -> ResultsContent.FAILED
        else -> ResultsContent.NO_RESULTS
    }
    val gridState = rememberLazyGridState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val keyboardDismissal = remember(keyboardController) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Bringing a focused field into view also scrolls the grid. Only a user scrolling towards the
                // covers should dismiss the keyboard, never the automatic scroll caused by opening it.
                if (source == NestedScrollSource.UserInput && available.y < 0f) keyboardController?.hide()
                return Offset.Zero
            }
        }
    }
    Column(modifier = modifier) {
        if (pinFields) {
            Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) { fields() }
        }
        LazyVerticalGrid(
            modifier = Modifier.bounceScrollableContent(gridState).weight(1f).fillMaxWidth().nestedScroll(keyboardDismissal).fadingTopEdge(
                scrolled = { if (gridState.firstVisibleItemIndex > 0) Int.MAX_VALUE else gridState.firstVisibleItemScrollOffset },
                backgroundColor = sheetContainerColor(),
            ),
            state = gridState,
            columns = GridCells.Adaptive(TILE_MIN_WIDTH),
            contentPadding = contentPadding.only(bottom = true, extraStart = 16.dp, extraEnd = 16.dp, extraTop = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (scrollingControls != null) {
                item(
                    key = MODE_CONTROLS_ITEM_KEY,
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    scrollingControls()
                }
            }
            if (!pinFields) {
                item(
                    key = FIELDS_ITEM_KEY,
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    fields()
                }
            }
            if (content == ResultsContent.GRID) {
                items(
                    items = candidates,
                    key = { it.key },
                ) { candidate ->
                    CoverArtTile(
                        modifier = Modifier.animateItem(),
                        candidate = candidate,
                        isSelected = candidate.coverArtUrl == selectedUrl,
                        onClick = { onSelected(candidate) },
                        onUnavailable = { onUnavailable(candidate) },
                    )
                }
                if (results?.isComplete == false) {
                    item(
                        key = LOADING_ITEM_KEY,
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Box(
                            modifier = Modifier.animateItem().fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            } else {
                item(
                    // Keyed by what it says, so that one message fades out as the next fades in rather than being swapped.
                    key = content.name,
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    when (content) {
                        ResultsContent.LOADING, ResultsContent.BUSY -> CoverArtSearchMessage(
                            modifier = Modifier.animateItem(),
                            text = stringResource(if (content == ResultsContent.BUSY) Res.string.cover_art_search_busy else Res.string.cover_art_search_loading),
                        ) {
                            CircularProgressIndicator()
                        }

                        ResultsContent.FAILED -> CoverArtSearchMessage(
                            modifier = Modifier.animateItem(),
                            text = stringResource(Res.string.cover_art_search_failed),
                        ) {
                            OutlinedButton(onClick = onRetry) {
                                Text(stringResource(Res.string.retry))
                            }
                        }

                        ResultsContent.NO_RESULTS -> CoverArtSearchMessage(
                            modifier = Modifier.animateItem(),
                            text = stringResource(Res.string.cover_art_search_no_results),
                        )

                        ResultsContent.HINT, ResultsContent.GRID -> CoverArtSearchMessage(
                            modifier = Modifier.animateItem(),
                            text = stringResource(Res.string.cover_art_search_hint),
                        )
                    }
                }
            }
            item(
                key = ATTRIBUTION_ITEM_KEY,
                span = { GridItemSpan(maxLineSpan) },
            ) {
                CoverArtAttribution(modifier = Modifier.animateItem())
            }
        }
    }
}

private enum class ResultsContent {
    HINT,
    LOADING,
    BUSY,
    FAILED,
    NO_RESULTS,
    GRID,
}

@Composable
private fun CoverArtSearchMessage(
    modifier: Modifier = Modifier,
    text: String,
    action: (@Composable () -> Unit)? = null,
) = Column(
    modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
) {
    action?.invoke()
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * One record: its cover, and under it the title, the artist and what kind of record it is from when. Selected, it is
 * ringed in the primary color and carries a check, the way a color disc of the settings screen is marked.
 */
@Composable
private fun CoverArtTile(
    modifier: Modifier = Modifier,
    candidate: CoverArtCandidate,
    isSelected: Boolean,
    onClick: () -> Unit,
    onUnavailable: () -> Unit,
) = Column(
    modifier = modifier
        .clip(MaterialTheme.shapes.medium)
        .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
        .padding(4.dp),
) {
    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(
                    if (isSelected) Modifier.border(width = 3.dp, color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) else Modifier
                ),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            AsyncImage(
                model = CoverArt(candidate.coverArtUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { onUnavailable() },
            )
        }
        if (isSelected) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    modifier = Modifier.padding(4.dp),
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = null,
                )
            }
        }
    }
    Text(
        modifier = Modifier.padding(top = 6.dp),
        text = candidate.title,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    val details = listOfNotNull(candidate.artist.takeIf { it.isNotBlank() }, candidate.type, candidate.year)
    if (details.isNotEmpty()) {
        Text(
            text = details.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The credit to the services, closing the search tab's grid: the records and the covers are theirs, and naming them is
 * what they ask of an app that shows them.
 */
@Composable
private fun CoverArtAttribution(
    modifier: Modifier = Modifier,
) = Text(
    modifier = modifier.fillMaxWidth().padding(top = 4.dp),
    text = stringResource(Res.string.cover_art_search_attribution),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)

private val TILE_MIN_WIDTH = 128.dp

private const val MODE_CONTROLS_ITEM_KEY = "mode_controls"

private const val FIELDS_ITEM_KEY = "fields"

private const val LOADING_ITEM_KEY = "loading"

private const val ATTRIBUTION_ITEM_KEY = "attribution"
