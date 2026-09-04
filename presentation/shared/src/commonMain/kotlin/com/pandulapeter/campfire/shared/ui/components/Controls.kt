package com.pandulapeter.campfire.shared.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.shared.resources.Res
import com.pandulapeter.campfire.shared.resources.filters
import com.pandulapeter.campfire.shared.resources.songs_show_explicit
import com.pandulapeter.campfire.shared.resources.songs_show_without_chords
import com.pandulapeter.campfire.shared.resources.songs_sorting_mode
import com.pandulapeter.campfire.shared.resources.songs_sorting_mode_by_artist
import com.pandulapeter.campfire.shared.resources.songs_sorting_mode_by_title
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.localization.stringResource

/**
 * [SongsControls] in a panel that spans the full height of the screen next to its app bar and content, shown on
 * expanded windows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongsControlsSidePanel(
    isVisible: Boolean,
    viewModel: CampfireViewModel,
    shouldIncludeSorting: Boolean,
    contentPadding: PaddingValues
) = AnimatedVisibility(
    visible = isVisible,
    enter = expandHorizontally() + fadeIn(),
    exit = shrinkHorizontally() + fadeOut()
) {
    val endPadding = contentPadding.calculateEndPadding(LocalLayoutDirection.current)
    Row {
        VerticalDivider()
        SongsControls(
            modifier = Modifier.width(SIDE_PANEL_WIDTH + endPadding).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow),
            viewModel = viewModel,
            shouldIncludeSorting = shouldIncludeSorting,
            contentPadding = PaddingValues(
                // The panel sits next to the app bar instead of below it, so it handles the top inset on its own.
                top = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding(),
                end = endPadding,
                bottom = contentPadding.calculateBottomPadding()
            )
        )
    }
}

/**
 * The padding of the content shown next to a [SongsControlsSidePanel]: while the panel is visible, the end inset
 * belongs to the panel.
 */
@Composable
internal fun PaddingValues.besideSidePanel(isSidePanelVisible: Boolean): PaddingValues {
    if (!isSidePanelVisible) return this
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection),
        top = calculateTopPadding(),
        bottom = calculateBottomPadding()
    )
}

/**
 * Sorting and filter controls of the song list, shown in a side panel on expanded windows and in a bottom sheet
 * otherwise.
 */
@Composable
internal fun SongsControls(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    shouldIncludeSorting: Boolean,
    contentPadding: PaddingValues = PaddingValues()
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val databases by viewModel.databases.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding)
    ) {
        if (shouldIncludeSorting) {
            SettingsSectionTitle(text = stringResource(Res.string.songs_sorting_mode))
            SegmentedChoice(
                options = listOf(
                    UserPreferences.SortingMode.BY_ARTIST to stringResource(Res.string.songs_sorting_mode_by_artist),
                    UserPreferences.SortingMode.BY_TITLE to stringResource(Res.string.songs_sorting_mode_by_title)
                ),
                selected = userPreferences?.sortingMode,
                onSelected = viewModel::setSortingMode
            )
        }
        SettingsSectionTitle(text = stringResource(Res.string.filters))
        CheckboxListItem(
            title = stringResource(Res.string.songs_show_explicit),
            isChecked = userPreferences?.shouldShowExplicitSongs == true,
            onCheckedChange = viewModel::setShouldShowExplicitSongs
        )
        CheckboxListItem(
            title = stringResource(Res.string.songs_show_without_chords),
            isChecked = userPreferences?.shouldShowSongsWithoutChords == true,
            onCheckedChange = viewModel::setShouldShowSongsWithoutChords
        )
        databases.filter { it.isEnabled }.forEach { database ->
            CheckboxListItem(
                title = database.name,
                isChecked = userPreferences?.unselectedDatabaseUrls?.contains(database.url) == false,
                onCheckedChange = { viewModel.setDatabaseSelected(database, it) }
            )
        }
    }
}

private val SIDE_PANEL_WIDTH = 320.dp
