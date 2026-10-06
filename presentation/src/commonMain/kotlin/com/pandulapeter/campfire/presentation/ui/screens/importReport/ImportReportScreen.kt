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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportProgress
import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.ic_check
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.ic_import
import com.pandulapeter.campfire.presentation.resources.ic_search
import com.pandulapeter.campfire.presentation.resources.import_conflicts
import com.pandulapeter.campfire.presentation.resources.import_conflicts_apply_all
import com.pandulapeter.campfire.presentation.resources.import_conflicts_confirm
import com.pandulapeter.campfire.presentation.resources.import_conflicts_duplicates
import com.pandulapeter.campfire.presentation.resources.import_conflicts_files
import com.pandulapeter.campfire.presentation.resources.import_conflicts_keep_both
import com.pandulapeter.campfire.presentation.resources.import_conflicts_keep_both_description
import com.pandulapeter.campfire.presentation.resources.import_conflicts_new
import com.pandulapeter.campfire.presentation.resources.import_conflicts_question
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace_description
import com.pandulapeter.campfire.presentation.resources.import_conflicts_skip
import com.pandulapeter.campfire.presentation.resources.import_conflicts_skip_description
import com.pandulapeter.campfire.presentation.resources.import_conflicts_skipped
import com.pandulapeter.campfire.presentation.resources.import_conflicts_summary
import com.pandulapeter.campfire.presentation.resources.import_failed
import com.pandulapeter.campfire.presentation.resources.import_files_empty
import com.pandulapeter.campfire.presentation.resources.import_oversized
import com.pandulapeter.campfire.presentation.resources.import_progress_title
import com.pandulapeter.campfire.presentation.resources.import_report_clean
import com.pandulapeter.campfire.presentation.resources.import_report_converted
import com.pandulapeter.campfire.presentation.resources.import_report_edit
import com.pandulapeter.campfire.presentation.resources.import_report_left_out
import com.pandulapeter.campfire.presentation.resources.import_report_search
import com.pandulapeter.campfire.presentation.resources.import_status_duplicates
import com.pandulapeter.campfire.presentation.resources.import_status_failed
import com.pandulapeter.campfire.presentation.resources.import_status_oversized
import com.pandulapeter.campfire.presentation.resources.import_status_partial
import com.pandulapeter.campfire.presentation.resources.import_status_setlists
import com.pandulapeter.campfire.presentation.resources.import_status_skipped
import com.pandulapeter.campfire.presentation.resources.import_status_skipped_conflicts
import com.pandulapeter.campfire.presentation.resources.import_status_songs
import com.pandulapeter.campfire.presentation.resources.import_status_stopped
import com.pandulapeter.campfire.presentation.resources.import_status_title
import com.pandulapeter.campfire.presentation.resources.import_status_unknown_failure
import com.pandulapeter.campfire.presentation.resources.import_status_unprocessed
import com.pandulapeter.campfire.presentation.resources.import_status_unreadable
import com.pandulapeter.campfire.presentation.resources.import_unreadable_documents
import com.pandulapeter.campfire.presentation.resources.songs_clear
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel.ImportReport
import com.pandulapeter.campfire.presentation.ui.components.CampfireTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.EDGE_FADE_SIZE
import com.pandulapeter.campfire.presentation.ui.components.HideKeyboardWhenScrolledDown
import com.pandulapeter.campfire.presentation.ui.components.RadioListItem
import com.pandulapeter.campfire.presentation.ui.components.SearchState
import com.pandulapeter.campfire.presentation.ui.components.TruncateSearchQuery
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.dialogs.ImportProgressContent
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource

/**
 * Everything about an import that went anywhere but the one way a progress dialog and a snackbar are enough for (see
 * `CampfireViewModel.importReport`), on one screen that goes through the import's stages in place rather than as a
 * run of dialogs replacing one another: the question about names that are taken, the import its answer decides on
 * being written, and every file of what it came to, grouped by what became of it. It is a screen of the back stack,
 * so a song opened from its list comes back to it, and a dialog that asks for one confirmation goes over it.
 *
 * Leaving it while it asks is cancelling the import, and needs no confirmation since nothing has been written yet;
 * leaving it while the import is written lets that finish on its own, see `CampfireViewModel.onImportReportLeft`.
 */
@Composable
internal fun ImportReportScreen(
    viewModel: CampfireViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val current by viewModel.importReport.collectAsStateWithLifecycle()
    // The screen is still composed while it slides away, after the view model has let go of what it showed.
    var report by remember { mutableStateOf(current) }
    current?.let { report = it }
    val shown = report ?: return
    val progress by viewModel.importProgress.collectAsStateWithLifecycle()
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val searchState = viewModel.importReportSearch
    val query by searchState.activeQuery.collectAsStateWithLifecycle("")
    var resolution by rememberSaveable { mutableStateOf(ImportConflictResolution.KEEP_BOTH) }
    val sections = remember(shown, songs, setlists) {
        when (shown) {
            is ImportReport.Review -> importReportSections(shown.summary)
            ImportReport.Importing -> emptyList()
            is ImportReport.Finished -> shown.result?.let { importReportSections(it, songs, setlists) }.orEmpty()
        }
    }
    val matchingSections = remember(sections, query) { sections.matching(query, viewModel::normalizeForSearch) }
    val isSearchAvailable = sections.isNotEmpty()
    val layoutDirection = LocalLayoutDirection.current
    // Narrowed rather than taken apart, so that the keyboard of the search field lays the list out again as it slides
    // instead of recomposing the screen on every frame of it.
    val listPadding = contentPadding.only(bottom = true, extraBottom = FLOATING_BUTTON_CLEARANCE)
    Column(Modifier.fillMaxSize()) {
        ImportReportTopAppBar(
            title = shown.title(),
            onClose = onBack,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = contentPadding.calculateStartPadding(layoutDirection), end = contentPadding.calculateEndPadding(layoutDirection)),
        ) {
            AnimatedContent(
                targetState = shown,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                // The stage, so that a result arriving for the same stage, or the library renaming a song in it, is
                // the same list going on rather than a second one fading in over it.
                contentKey = { it::class },
            ) { stage ->
                when (stage) {
                    is ImportReport.Review -> ReportList(
                        contentPadding = listPadding,
                        searchFieldIndex = REVIEW_HEADER_ITEM_COUNT.takeIf { isSearchAvailable },
                    ) { listState ->
                        reviewHeader(
                            summary = stage.summary,
                            resolution = resolution,
                            onResolutionSelected = { resolution = it },
                        )
                        if (isSearchAvailable) searchField(searchState = searchState, listState = listState, index = REVIEW_HEADER_ITEM_COUNT)
                        sections(
                            sections = matchingSections,
                            isFiltered = query.isNotBlank(),
                            isPerformanceModeEnabled = isPerformanceModeEnabled,
                            onOpenSong = viewModel::openReportedSong,
                            onEditSong = { viewModel.openEditor(it) },
                        )
                    }

                    ImportReport.Importing -> Box(
                        modifier = Modifier.fillMaxSize().padding(contentPadding.only(bottom = true)),
                        contentAlignment = Alignment.Center,
                    ) {
                        ImportProgressContent(
                            modifier = Modifier.widthIn(max = PROGRESS_MAX_WIDTH).fillMaxWidth().padding(24.dp),
                            // The comparison before the first entry is reported is part of writing the import too.
                            progress = progress ?: ImportProgress(ImportProgress.Phase.IMPORTING),
                        )
                    }

                    is ImportReport.Finished -> ReportList(
                        contentPadding = listPadding,
                        searchFieldIndex = FINISHED_HEADER_ITEM_COUNT.takeIf { isSearchAvailable },
                    ) { listState ->
                        finishedHeader(stage.result)
                        if (isSearchAvailable) searchField(searchState = searchState, listState = listState, index = FINISHED_HEADER_ITEM_COUNT)
                        sections(
                            sections = matchingSections,
                            isFiltered = query.isNotBlank(),
                            isPerformanceModeEnabled = isPerformanceModeEnabled,
                            onOpenSong = viewModel::openReportedSong,
                            onEditSong = { viewModel.openEditor(it) },
                        )
                    }
                }
            }
            ImportReportButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(contentPadding.only(bottom = true, extraBottom = 16.dp))
                    .padding(end = 16.dp),
                report = shown,
                onImport = {
                    val summary = (shown as? ImportReport.Review)?.summary
                    if (summary != null && resolution == ImportConflictResolution.REPLACE) {
                        viewModel.showDialog(CampfireViewModel.DialogType.ConfirmImportReplace(summary.conflictingFileNames.size))
                    } else {
                        viewModel.resolveImport(resolution)
                    }
                },
                onDone = onBack,
            )
        }
    }
}

@Composable
private fun ImportReport.title() = stringResource(
    when (this) {
        is ImportReport.Review -> Res.string.import_conflicts
        ImportReport.Importing -> Res.string.import_progress_title
        is ImportReport.Finished -> when {
            result == null -> Res.string.import_failed
            result.isFailed -> Res.string.import_status_stopped
            else -> Res.string.import_status_title
        }
    },
)

/** Close, which cancels a question and leaves anything else, and the title of the stage the import is at. */
@Composable
private fun ImportReportTopAppBar(
    title: String,
    onClose: () -> Unit,
) = CampfireTopAppBar(
    navigationIcon = {
        IconButton(onClick = onClose) {
            Icon(
                painter = painterResource(Res.drawable.ic_clear),
                contentDescription = stringResource(Res.string.close),
            )
        }
    },
    title = {
        AnimatedContent(
            targetState = title,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { text ->
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    },
)

/**
 * The search of the list, standing right above the files it narrows and pinned at the top of the list once they are
 * scrolled under it: always there rather than behind a button, since the list is the screen's whole content and a
 * name is what somebody comes to it to find. The rows fade out under it the way they fade out under a bar, by a
 * gradient drawn below the field over what passes under it, as strong as the rows have been scrolled under it.
 */
private fun LazyListScope.searchField(
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

/**
 * One line on a tonal pill, as the list screens' search field is, with the magnifier in front of it and a button that
 * empties it once there is something to empty. It never takes the focus by itself, since it is there whether or not
 * anybody means to search and a keyboard coming up over the result on a phone would hide what the screen is for;
 * Ctrl / Cmd + F gives it the caret through `CampfireViewModel.openCurrentSearch`, with what it holds selected.
 */
@Composable
private fun ImportReportSearchField(
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

/**
 * The list both the question and the result are, centered and no wider than a line of text reads well at on a wide
 * window. It fades out under the bar rather than the bar lifting over it, like every list of the app - until the search
 * field at [searchFieldIndex] is pinned, which the fade would otherwise take the top of: from there the field is what
 * the rows fade under, see [searchField], and the list's own fade gives way to it as the field arrives at the top.
 */
@Composable
private fun ReportList(
    contentPadding: PaddingValues,
    searchFieldIndex: Int?,
    content: LazyListScope.(LazyListState) -> Unit,
) {
    val listState = rememberLazyListState()
    val fadeHeight = with(LocalDensity.current) { EDGE_FADE_SIZE.roundToPx() }
    HideKeyboardWhenScrolledDown(listState)
    LazyColumn(
        modifier = Modifier.bounceScrollableContent(listState)
            .fillMaxSize()
            .fadingTopEdge {
                val field = searchFieldIndex?.let { index -> listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } }
                when {
                    searchFieldIndex != null && listState.firstVisibleItemIndex >= searchFieldIndex -> 0
                    // The fade is as strong as the field is far from the top, so it is gone by the time the field pins.
                    field != null && field.offset < fadeHeight -> field.offset.coerceAtLeast(0)
                    listState.firstVisibleItemIndex > 0 -> Int.MAX_VALUE
                    else -> listState.firstVisibleItemScrollOffset
                }
            },
        state = listState,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = { content(listState) },
    )
}

/** The width every item of [ReportList] is laid out at, see there. */
private val ItemWidth get() = Modifier.widthIn(max = LIST_MAX_WIDTH).fillMaxWidth()

/**
 * The question first and the names it is about after it, since the names can run to hundreds and the answer is one
 * for all of them. What the import does with everything else is said above the question, so that it is answered in
 * full sight of the rest.
 */
private fun LazyListScope.reviewHeader(
    summary: ImportPlan.Summary,
    resolution: ImportConflictResolution,
    onResolutionSelected: (ImportConflictResolution) -> Unit,
) {
    item(key = "summary", contentType = "text") {
        Column(
            modifier = ItemWidth.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = pluralStringResource(
                    Res.plurals.import_conflicts_summary,
                    summary.conflictingFileNames.size,
                    summary.conflictingFileNames.size,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (summary.newSongCount > 0 || summary.newSetlistCount > 0) {
                ReportNote(text = stringResource(Res.string.import_conflicts_new, summary.newSongCount, summary.newSetlistCount))
            }
            if (summary.duplicateCount > 0) {
                ReportNote(text = pluralStringResource(Res.plurals.import_conflicts_duplicates, summary.duplicateCount, summary.duplicateCount))
            }
            if (summary.skippedCount > 0) {
                ReportNote(text = pluralStringResource(Res.plurals.import_conflicts_skipped, summary.skippedCount, summary.skippedCount))
            }
            if (summary.oversizedCount > 0) {
                ReportNote(text = pluralStringResource(Res.plurals.import_oversized, summary.oversizedCount, summary.oversizedCount))
            }
            if (summary.unreadableDocumentCount > 0) {
                ReportNote(text = pluralStringResource(Res.plurals.import_unreadable_documents, summary.unreadableDocumentCount, summary.unreadableDocumentCount))
            }
        }
    }
    item(key = "question", contentType = "question") {
        Column(modifier = ItemWidth.padding(top = 16.dp)) {
            SectionTitle(text = stringResource(Res.string.import_conflicts_question))
            ReportNote(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(Res.string.import_conflicts_apply_all),
            )
            ImportConflictResolution.entries.forEach { option ->
                RadioListItem(
                    title = stringResource(option.label),
                    description = stringResource(option.description),
                    isSelected = option == resolution,
                    onSelected = { onResolutionSelected(option) },
                )
            }
        }
    }
}

/** What the import came to in one sentence, above the files it is about. */
private fun LazyListScope.finishedHeader(result: ImportResult?) {
    item(key = "summary", contentType = "text") {
        val isError = result == null || result.isFailed
        Text(
            modifier = ItemWidth.padding(horizontal = 16.dp),
            text = stringResource(
                when {
                    result == null -> Res.string.import_status_unknown_failure
                    result.isFailed -> Res.string.import_status_partial
                    result.isClean -> Res.string.import_report_clean
                    else -> Res.string.import_report_left_out
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val ImportResult.isClean
    get() = skippedFileNames.isEmpty() && skippedConflictingFileNames.isEmpty() && oversizedFileNames.isEmpty() &&
        unreadableDocumentFileNames.isEmpty()

/**
 * Every section with a heading and a count, and its files under it. A song the library holds opens in a pager over
 * the songs of its section; one that was converted from another format says so, and opens in the editor from its own
 * button outside performance mode, since a conversion is a best attempt worth checking.
 */
private fun LazyListScope.sections(
    sections: List<ImportReportSection>,
    isFiltered: Boolean,
    isPerformanceModeEnabled: Boolean,
    onOpenSong: (songFileNames: List<String>, index: Int) -> Unit,
    onEditSong: (String) -> Unit,
) {
    if (isFiltered && sections.isEmpty()) {
        item(key = "empty", contentType = "text") {
            ReportNote(
                modifier = ItemWidth.animateItem().padding(horizontal = 16.dp, vertical = 24.dp),
                text = stringResource(Res.string.import_files_empty),
            )
        }
    }
    sections.forEach { section ->
        item(key = "header_${section.kind}", contentType = "header") {
            SectionTitle(
                modifier = ItemWidth.animateItem().padding(top = 16.dp),
                text = stringResource(section.kind.label),
                count = section.rows.size,
            )
        }
        val songFileNames = section.rows.filter { it.isSong }.map { it.fileName }.distinct()
        items(
            items = section.rows,
            key = { row -> "${section.kind}_${row.key}" },
            contentType = { "row" },
        ) { row ->
            ImportReportRowItem(
                modifier = ItemWidth.animateItem(),
                row = row,
                canEdit = row.isConverted && row.isSong && !isPerformanceModeEnabled,
                onClick = if (row.isSong) {
                    { onOpenSong(songFileNames, songFileNames.indexOf(row.fileName).coerceAtLeast(0)) }
                } else {
                    null
                },
                onEdit = { onEditSong(row.fileName) },
            )
        }
    }
}

@Composable
private fun ImportReportRowItem(
    modifier: Modifier = Modifier,
    row: ImportReportRow,
    canEdit: Boolean,
    onClick: (() -> Unit)?,
    onEdit: () -> Unit,
) = ListItem(
    modifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = {
        Text(
            text = row.title ?: row.fileName,
            maxLines = 1,
            overflow = if (row.title == null) TextOverflow.MiddleEllipsis else TextOverflow.Ellipsis,
        )
    },
    supportingContent = listOfNotNull(
        row.subtitle,
        if (row.isConverted) stringResource(Res.string.import_report_converted) else null,
    ).takeIf { it.isNotEmpty() }?.let { lines ->
        {
            Text(
                text = lines.joinToString(" · "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    },
    trailingContent = if (canEdit) {
        {
            IconButton(onClick = onEdit) {
                Icon(
                    painter = painterResource(Res.drawable.ic_edit),
                    contentDescription = stringResource(Res.string.import_report_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        null
    },
)

/** A heading of the list, in the color the list screens' section headers are drawn in, with its count at the end. */
@Composable
private fun SectionTitle(
    modifier: Modifier = Modifier,
    text: String,
    count: Int? = null,
) = Row(
    modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    Text(
        modifier = Modifier.weight(1f),
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = LocalSecondAccentColor.current,
    )
    count?.let {
        Text(
            text = it.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReportNote(
    modifier: Modifier = Modifier,
    text: String,
) = Text(
    modifier = modifier,
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/**
 * Import while the screen asks, Done once it has an answer, and nothing while the import is being written: there is
 * nothing to do then but wait, and Close is still there for leaving it to finish on its own.
 */
@Composable
private fun ImportReportButton(
    modifier: Modifier = Modifier,
    report: ImportReport,
    onImport: () -> Unit,
    onDone: () -> Unit,
) = AnimatedContent(
    modifier = modifier,
    targetState = report::class,
    transitionSpec = { (fadeIn() + scaleIn()) togetherWith (fadeOut() + scaleOut()) },
    contentAlignment = Alignment.BottomEnd,
) { stage ->
    when (stage) {
        ImportReport.Review::class -> ExtendedFloatingActionButton(
            onClick = onImport,
            icon = { Icon(painter = painterResource(Res.drawable.ic_import), contentDescription = null) },
            text = { Text(stringResource(Res.string.import_conflicts_confirm)) },
        )

        ImportReport.Finished::class -> ExtendedFloatingActionButton(
            onClick = onDone,
            icon = { Icon(painter = painterResource(Res.drawable.ic_check), contentDescription = null) },
            text = { Text(stringResource(Res.string.done)) },
        )

        else -> Box(Modifier)
    }
}

private val ImportReportSection.Kind.label: StringResource
    get() = when (this) {
        ImportReportSection.Kind.CONFLICTS -> Res.string.import_conflicts_files
        ImportReportSection.Kind.FAILED -> Res.string.import_status_failed
        ImportReportSection.Kind.UNPROCESSED -> Res.string.import_status_unprocessed
        ImportReportSection.Kind.SKIPPED_CONFLICTS -> Res.string.import_status_skipped_conflicts
        ImportReportSection.Kind.SKIPPED -> Res.string.import_status_skipped
        ImportReportSection.Kind.OVERSIZED -> Res.string.import_status_oversized
        ImportReportSection.Kind.UNREADABLE -> Res.string.import_status_unreadable
        ImportReportSection.Kind.SONGS -> Res.string.import_status_songs
        ImportReportSection.Kind.SETLISTS -> Res.string.import_status_setlists
        ImportReportSection.Kind.DUPLICATES -> Res.string.import_status_duplicates
    }

private val ImportConflictResolution.label
    get() = when (this) {
        ImportConflictResolution.KEEP_BOTH -> Res.string.import_conflicts_keep_both
        ImportConflictResolution.REPLACE -> Res.string.import_conflicts_replace
        ImportConflictResolution.SKIP -> Res.string.import_conflicts_skip
    }

private val ImportConflictResolution.description
    get() = when (this) {
        ImportConflictResolution.KEEP_BOTH -> Res.string.import_conflicts_keep_both_description
        ImportConflictResolution.REPLACE -> Res.string.import_conflicts_replace_description
        ImportConflictResolution.SKIP -> Res.string.import_conflicts_skip_description
    }

private val LIST_MAX_WIDTH = 720.dp
private val PROGRESS_MAX_WIDTH = 480.dp

/** How many items [reviewHeader] and [finishedHeader] put above the search field, which is where it pins from. */
private const val REVIEW_HEADER_ITEM_COUNT = 2
private const val FINISHED_HEADER_ITEM_COUNT = 1
private val SEARCH_FIELD_HEIGHT = 48.dp

/** Room under the last row for the floating button to sit beside rather than over it, as the export screen leaves. */
private val FLOATING_BUTTON_CLEARANCE: Dp = 88.dp
