/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.ChordProSummaryCache
import com.pandulapeter.campfire.chordpro.ChordProTransposer
import com.pandulapeter.campfire.chordpro.model.displayTitle
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.ic_error
import com.pandulapeter.campfire.presentation.resources.ic_redo
import com.pandulapeter.campfire.presentation.resources.ic_refresh
import com.pandulapeter.campfire.presentation.resources.ic_save
import com.pandulapeter.campfire.presentation.resources.ic_undo
import com.pandulapeter.campfire.presentation.resources.retry
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_details_no_data
import com.pandulapeter.campfire.presentation.resources.song_details_no_data_hint
import com.pandulapeter.campfire.presentation.resources.song_editor_edit
import com.pandulapeter.campfire.presentation.resources.song_editor_preview
import com.pandulapeter.campfire.presentation.resources.song_editor_redo
import com.pandulapeter.campfire.presentation.resources.song_editor_revert
import com.pandulapeter.campfire.presentation.resources.song_editor_split
import com.pandulapeter.campfire.presentation.resources.song_editor_hide_shortcuts
import com.pandulapeter.campfire.presentation.resources.song_editor_shortcuts
import com.pandulapeter.campfire.presentation.resources.song_editor_show_shortcuts
import com.pandulapeter.campfire.presentation.resources.song_editor_undo
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.contentEdges
import com.pandulapeter.campfire.presentation.ui.components.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.components.CampfireTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.CoverArtImage
import com.pandulapeter.campfire.presentation.ui.components.DelayedLoadingIndicator
import com.pandulapeter.campfire.presentation.ui.components.EmptyState
import com.pandulapeter.campfire.presentation.ui.components.EmptyStateAction
import com.pandulapeter.campfire.presentation.ui.components.ExpandChevron
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.overlappingAction
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.components.WindowSize
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.APP_BAR_COVER_GAP
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.APP_BAR_COVER_SIZE
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SectionMotion
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongLyrics
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongLyricsInputs
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.TextTranspositionControls
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.prepareSongLyrics
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.rememberDefaultSectionLabels
import com.pandulapeter.campfire.presentation.ui.theme.LocalMonospaceFontFamily
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

/**
 * The raw ChordPro text of one song, with what it will look like next to it. Wide windows show both at once, narrow
 * ones one at a time.
 *
 * The text lives in a [TextFieldState] here rather than in the view model: it is the field's own state, undo history
 * included, and it lives through a configuration change whole, in the view model's keeping, and through process death
 * as its text and caret, see [EditorFieldSaver]. Nothing is ever written on its own: the file changes when the user
 * saves it, and leaving with something unsaved asks first (see [CampfireViewModel.navigateBack]). What the screen does report as it is typed is the text itself, which is
 * what lets that question be answered - and the save be finished - after the screen is gone.
 */
@Composable
internal fun SongEditorScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    destination: CampfireDestination.SongEditor,
    windowSize: WindowSize,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val songTexts by viewModel.songTexts.collectAsStateWithLifecycle()
    val failedSongFileNames by viewModel.failedSongFileNames.collectAsStateWithLifecycle()
    // The text the field starts from, taken once. What the file holds afterwards is only ever compared with what has
    // been typed: an editor that followed songTexts would be taken apart, the field and the draft with it, by a
    // sync run deleting the file or a rescan failing to read it - the two moments the draft is the only copy left.
    //
    // An editor that is composed again over a file that has gone - after a rotation, or in a process the system
    // restored - still has its field to come back to, retained by the view model or saved with the screen, so it
    // opens on that rather than waiting for a file that is not coming back. Whether it had opened is saved for the
    // same reason: in a new process that flag is all there is to tell a draft worth restoring from a song that was
    // never loaded. The empty text it opens with is only what the field is compared with, which is what the file
    // holds now.
    var hasOpened by rememberSaveable(destination.fileName) { mutableStateOf(false) }
    var initialText by remember(destination.fileName) {
        mutableStateOf(viewModel.songTexts.value[destination.fileName] ?: viewModel.retainedEditorField(destination.fileName)?.let { "" })
    }
    LaunchedEffect(destination.fileName) {
        viewModel.loadSongContent(destination.fileName).join()
        if (initialText == null && hasOpened) initialText = viewModel.songTexts.value[destination.fileName] ?: ""
        initialText = initialText ?: viewModel.songTexts.mapNotNull { it[destination.fileName] }.first()
        hasOpened = true
    }
    AnimatedContent(
        modifier = modifier.fillMaxSize(),
        targetState = initialText,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it != null },
    ) { text ->
        if (text == null) {
            SongNotLoadedPane(
                contentPadding = contentPadding,
                hasFailed = destination.fileName in failedSongFileNames,
                onRetry = { viewModel.loadSongContent(destination.fileName) },
                onClose = onBack,
            )
        } else {
            LoadedSongEditor(
                viewModel = viewModel,
                destination = destination,
                initialText = text,
                hasSavedText = songTexts[destination.fileName] != null,
                windowSize = windowSize,
                contentPadding = contentPadding,
                onBack = onBack,
            )
        }
    }
}

/**
 * What the editor shows until it has a text to open with. A read that failed says so and offers the way out as
 * well as the retry, because this state has no app bar to leave by.
 */
@Composable
private fun SongNotLoadedPane(
    contentPadding: PaddingValues,
    hasFailed: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) = Box(
    modifier = Modifier.fillMaxSize().padding(contentPadding),
    contentAlignment = Alignment.Center,
) {
    if (hasFailed) {
        EmptyState(
            icon = painterResource(Res.drawable.ic_error),
            title = stringResource(Res.string.song_details_no_data),
            hint = stringResource(Res.string.song_details_no_data_hint),
            actions = listOf(
                EmptyStateAction(text = stringResource(Res.string.retry), onClick = onRetry),
                EmptyStateAction(text = stringResource(Res.string.close), onClick = onClose),
            ),
        )
    } else {
        DelayedLoadingIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun LoadedSongEditor(
    viewModel: CampfireViewModel,
    destination: CampfireDestination.SongEditor,
    initialText: String,
    hasSavedText: Boolean,
    windowSize: WindowSize,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    // Keyed on the file name so that opening another song starts a new field with its own undo history, and saved
    // so that a rotation or a trip through process death does not lose what has been typed, see EditorFieldSaver.
    val fileText by rememberUpdatedState(initialText)
    val editorField = rememberSaveable(
        destination.fileName,
        saver = remember(viewModel, destination.fileName) {
            EditorFieldSaver(
                retain = { viewModel.retainEditorField(destination.fileName, it) },
                retained = { viewModel.retainedEditorField(destination.fileName) },
                fileText = { fileText },
            )
        },
    ) {
        // A field the view model already holds for this file is taken as it is: the draft a previous run left, reopened
        // on launch. Otherwise a new one over the file's text.
        EditorField(
            viewModel.retainedEditorField(destination.fileName) ?: TextFieldState(
                initialText = initialText,
                initialSelection = if (destination.shouldStartInsideFirstSection) {
                    TextRange(initialText.caretInsideFirstSection())
                } else {
                    TextRange.Zero
                },
            )
        )
    }
    val textFieldState = editorField.textFieldState
    // Owned here rather than by the panes, because a pane is composed again from scratch whenever the layout changes -
    // Edit and Preview are one AnimatedContent, Split is a Row - and a scroll position kept inside it would go back to
    // the first line on every switch, rotation and resize across the split width.
    val fieldScrollState = rememberScrollState()
    val fieldHorizontalScrollState = rememberScrollState()
    val previewScrollState = rememberScrollState()
    LaunchedEffect(editorField) {
        if (editorField.isDraftLost) viewModel.onEditorDraftLost()
    }
    // The text as one string, copied once per edit and shared by everything that follows it: the draft, the summary
    // in the bar, the toolbar and the preview would otherwise each copy and scan the whole song on every keystroke.
    val text = remember(textFieldState) { derivedStateOf { textFieldState.text.toString() } }
    ReportDraft(viewModel = viewModel, fileName = destination.fileName, text = text)

    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSavingSong.collectAsStateWithLifecycle()
    // The bar above the text follows the text as it is typed, so retitling a song shows up there right away. It
    // comes from the parser rather than from a regex of this screen's own, so that it is the same title, artist and
    // key the rest of the app will show once the file is written - the fallback to the file name included. One
    // summary rather than a parse per field: it also answers whether there is anything left to transpose.
    val summaryCache = remember(textFieldState) { ChordProSummaryCache() }
    val summary by remember(textFieldState) { derivedStateOf { summaryCache.summaryOf(text.value) } }
    val hasUnsavedChanges by viewModel.hasUnsavedEditorChanges.collectAsStateWithLifecycle()
    RevertOnRequest(viewModel = viewModel, fileName = destination.fileName, textFieldState = textFieldState, summaryCache = summaryCache)
    FollowFileWhileUntouched(viewModel = viewModel, fileName = destination.fileName, textFieldState = textFieldState, summaryCache = summaryCache)
    // The one way the file is ever written, reached from the app bar's button and from Ctrl / Cmd + S alike.
    val onSaveRequested = {
        if (hasUnsavedChanges) {
            viewModel.saveSongContent(destination.fileName, textFieldState.text.toString())
        }
        Unit
    }
    // Split is only offered where two panes fit, but the choice survives a window that narrows and widens again
    // rather than being forgotten the moment it cannot be honored.
    val hasRoomForSplitPanes = windowSize == WindowSize.EXPANDED
    var selectedPanes by rememberSaveable(stateSaver = EditorPanes.Saver) { mutableStateOf(EditorPanes.SPLIT) }
    val panes = if (selectedPanes == EditorPanes.SPLIT && !hasRoomForSplitPanes) EditorPanes.EDIT else selectedPanes
    val hasSideBySidePreview = panes == EditorPanes.SPLIT
    val fontScale = userPreferences?.fontScale ?: CampfireViewModel.DEFAULT_FONT_SCALE
    val chordSpelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
    // The two rows of insertions are taller than what a phone has left for the text once the keyboard is up, so a
    // phone opens the editor with them folded away. The shorter side of the window is what decides that rather than
    // the width class: a phone turned sideways is wide enough to count as a large screen and is the one with the
    // least height of all.
    val windowContainerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    var isToolbarExpanded by rememberSaveable {
        mutableStateOf(with(density) { minOf(windowContainerSize.width, windowContainerSize.height).toDp() } >= SMALL_SCREEN_SIZE)
    }
    // Key events only travel along the focus path, and nothing in the editor is focused until the text is clicked,
    // nor at all while the preview is the only pane - or after the panes change places, which composes the field
    // again. So the editor takes the focus itself as it opens and whenever the panes change, and the save shortcut
    // sits in its preview pass, where it hears the key wherever inside the editor the focus has gone since: the field
    // once it is clicked, the preview, a button of the bar.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(panes) { focusRequester.requestFocus() }

    val layoutDirection = LocalLayoutDirection.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            // The same save as the app bar's button, for the hand that reaches for the keyboard instead. Not with Alt
            // held: AltGr arrives as Ctrl + Alt on Windows and the web, and AltGr + S types a character on some layouts.
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.S && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && !keyEvent.isAltPressed) {
                    onSaveRequested()
                    true
                } else {
                    false
                }
            }
    ) {
        CampfireTopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_clear),
                        contentDescription = stringResource(Res.string.close),
                    )
                }
            },
            title = {
                EditorTitle(
                    title = summary.metadata.displayTitle(destination.fileName.removeSuffix(LibraryFiles.SONG_EXTENSION)),
                    artist = summary.metadata.artist.orEmpty(),
                    coverArtUrl = summary.metadata.coverArt.takeIf { userPreferences?.isCoverArtEnabled == true },
                )
            },
            actions = {
                IconButton(
                    enabled = textFieldState.undoState.canUndo,
                    onClick = { textFieldState.undoState.undo() },
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_undo),
                        contentDescription = stringResource(Res.string.song_editor_undo),
                    )
                }
                IconButton(
                    modifier = Modifier.overlappingAction(start = ACTION_BUTTON_OVERLAP, end = 0.dp),
                    enabled = textFieldState.undoState.canRedo,
                    onClick = { textFieldState.undoState.redo() },
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_redo),
                        contentDescription = stringResource(Res.string.song_editor_redo),
                    )
                }
                IconButton(
                    modifier = Modifier.overlappingAction(start = ACTION_BUTTON_OVERLAP, end = 0.dp),
                    enabled = hasUnsavedChanges && !isSaving,
                    onClick = onSaveRequested,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_save),
                        contentDescription = stringResource(Res.string.save),
                    )
                }
                EditorMenu(
                    modifier = Modifier.overlappingAction(start = ACTION_BUTTON_OVERLAP, end = 0.dp),
                    canRevert = hasUnsavedChanges && hasSavedText && !isSaving,
                    onRevert = { viewModel.showDialog(CampfireViewModel.DialogType.RevertChanges) },
                )
            },
            bottomContent = {
                // Transposing, switching pane and folding the insertions away are the things here that do not write at
                // the caret, so they are the ones that stay when the insertions leave.
                Row(
                    modifier = Modifier.padding(
                        start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                        end = contentPadding.calculateEndPadding(layoutDirection) + 4.dp,
                        bottom = 8.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextTranspositionControls(
                        key = summary.metadata.key,
                        // A key is enough on its own: it says what the song is in, and moving it is a transposition
                        // even before a chord has been written under it.
                        isEnabled = summary.hasChords || !summary.metadata.key.isNullOrBlank(),
                        onTransposed = { semitones ->
                            textFieldState.replaceWithTransposition(viewModel.transposeText(textFieldState.text.toString(), semitones, chordSpelling.accidentals))
                        },
                    )
                    SegmentedChoice(
                        // Not the whole of a desktop window's width: the row is a pair of controls rather than a
                        // tab bar, and the stepper next to it would be lost at the end of a metre of segments.
                        modifier = Modifier.weight(1f, fill = false).widthIn(max = PANE_CHOICE_MAX_WIDTH),
                        options = listOfNotNull(
                            EditorPanes.EDIT to stringResource(Res.string.song_editor_edit),
                            EditorPanes.PREVIEW to stringResource(Res.string.song_editor_preview),
                            if (hasRoomForSplitPanes) EditorPanes.SPLIT to stringResource(Res.string.song_editor_split) else null,
                        ),
                        selected = panes,
                        isInline = true,
                        onSelected = { selectedPanes = it },
                    )
                    // Disabled rather than hidden over a preview, so that the segments next to it do not change
                    // width under the finger that has just picked one of them.
                    EditorToolbarToggle(
                        isExpanded = isToolbarExpanded,
                        isLabeled = windowSize != WindowSize.COMPACT,
                        isEnabled = panes != EditorPanes.PREVIEW,
                        onToggled = { isToolbarExpanded = !isToolbarExpanded },
                    )
                }
                // Nothing below can act on a preview, so the insertions leave with the field they write into.
                AnimatedVisibility(visible = isToolbarExpanded && panes != EditorPanes.PREVIEW) {
                    EditorToolbar(
                        textFieldState = textFieldState,
                        text = text,
                        contentPadding = contentPadding,
                    )
                }
            },
        )
        val editor: @Composable (Modifier) -> Unit = { paneModifier ->
            ChordProTextField(
                modifier = paneModifier,
                textFieldState = textFieldState,
                scrollState = fieldScrollState,
                horizontalScrollState = fieldHorizontalScrollState,
                // Next to the preview the divider is the end of this pane, not the window.
                contentPadding = contentPadding.only(start = true, end = !hasSideBySidePreview, bottom = true),
            )
        }
        val preview: @Composable (Modifier) -> Unit = { paneModifier ->
            SongPreview(
                modifier = paneModifier,
                viewModel = viewModel,
                text = text,
                scrollState = previewScrollState,
                transposition = transpositions[destination.fileName, null],
                fontScale = fontScale,
                chordSpelling = chordSpelling,
                contentPadding = contentPadding.only(start = !hasSideBySidePreview, end = true, bottom = true),
                isSingleColumn = hasSideBySidePreview,
            )
        }
        // The panes take what the app bar and the toggle above them leave, rather than the whole window: a Column
        // measures a child that does not weigh anything against an unbounded height, and a song longer than the
        // screen then lays the field out past the bottom edge of it instead of scrolling inside it.
        if (hasSideBySidePreview) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                editor(Modifier.weight(1f).fillMaxHeight())
                VerticalDivider()
                preview(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            AnimatedContent(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                targetState = panes == EditorPanes.PREVIEW,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { showPreview ->
                if (showPreview) preview(Modifier.fillMaxSize()) else editor(Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * The song as the text being typed names it, with its cover in front of the title the way the song details screen
 * draws it, so that an address written or changed by hand is seen before it is saved.
 *
 * The cover follows the text only once the typing has paused ([COVER_ART_DELAY]): every half-typed address that happens
 * to be a valid one would otherwise be a download of its own, and one that failed is not asked again for a while. A
 * cover taken out of the text goes at once, since that asks nothing. It takes no press here, unlike the details
 * screen's: the cover search writes into the file, under a draft that has not been saved.
 */
@Composable
private fun EditorTitle(
    title: String,
    artist: String,
    coverArtUrl: String?,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
) {
    var shownCoverArtUrl by remember { mutableStateOf(coverArtUrl) }
    LaunchedEffect(coverArtUrl) {
        if (coverArtUrl != null) delay(COVER_ART_DELAY)
        shownCoverArtUrl = coverArtUrl
    }
    // Keyed on whether there is a cover rather than on its address, so that one address changing to another is the
    // image crossfading in its place rather than the room for it closing and opening again.
    AnimatedContent(
        targetState = shownCoverArtUrl,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it != null },
    ) { url ->
        if (url != null) {
            CoverArtImage(
                modifier = Modifier.padding(end = APP_BAR_COVER_GAP).size(APP_BAR_COVER_SIZE),
                url = url,
            )
        }
    }
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EditorToolbarToggle(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    isLabeled: Boolean,
    isEnabled: Boolean,
    onToggled: () -> Unit,
) {
    val icon: @Composable () -> Unit = {
        ExpandChevron(
            isExpanded = isExpanded,
            contentDescription = stringResource(if (isExpanded) Res.string.song_editor_hide_shortcuts else Res.string.song_editor_show_shortcuts),
        )
    }
    if (isLabeled) {
        // The label names the rows rather than the action, so that it keeps one width while the chevron turns: a
        // "Show" becoming a "Hide" would move the segments next to it on every tap.
        TextButton(
            modifier = modifier,
            enabled = isEnabled,
            onClick = onToggled,
            colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
        ) {
            Text(text = stringResource(Res.string.song_editor_shortcuts))
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            icon()
        }
    } else {
        IconButton(
            modifier = modifier,
            enabled = isEnabled,
            onClick = onToggled,
            content = icon,
        )
    }
}

/**
 * Which of the two panes the editor shows. [SPLIT] needs a window wide enough for both and is the choice a window
 * that has the room opens with, since seeing the song take shape is the reason the preview exists at all.
 */
private enum class EditorPanes {
    EDIT, PREVIEW, SPLIT;

    companion object {
        /** Saved by name rather than left to the automatic saver, which only stores what a platform can serialize. */
        val Saver = Saver<EditorPanes, String>(save = { it.name }, restore = ::valueOf)
    }
}

/**
 * The text itself. Monospaced, because a tab or a grid only lines up in one and because a ChordPro document is
 * source rather than prose.
 *
 * The text size preference deliberately does not reach here: it is how large the lyrics are read from across a
 * room, and this is the source of the file rather than the song. Scaling it would also move the columns of a tab
 * away from the width the monospaced font is keeping them at.
 *
 * Lines are never wrapped: the staff lines of a tab only stay in their columns while each of them is one line on
 * screen, and a source file is read by its lines, which a wrapped one shows as two. A line wider than the pane is
 * scrolled to sideways instead.
 *
 * @param scrollState The field's own scroll position, hoisted so that it survives the pane being composed again.
 * @param horizontalScrollState How far the field is scrolled sideways, hoisted for the same reason.
 */
@Composable
private fun ChordProTextField(
    modifier: Modifier = Modifier,
    textFieldState: TextFieldState,
    scrollState: ScrollState,
    horizontalScrollState: ScrollState,
    contentPadding: PaddingValues,
) {
    val colorScheme = MaterialTheme.colorScheme
    val secondAccentColor = LocalSecondAccentColor.current
    val tokenCache = remember { ChordProTokenCache() }
    val outputTransformation = remember(colorScheme, secondAccentColor, tokenCache) {
        ChordProOutputTransformation.of(
            tokenCache = tokenCache,
            primaryColor = colorScheme.primary,
            chordColor = secondAccentColor,
            secondaryColor = colorScheme.onSurfaceVariant,
            outlineColor = colorScheme.outline,
        )
    }
    val bodyLarge = MaterialTheme.typography.bodyLarge
    val density = LocalDensity.current
    // The bottom of the padding this screen was handed is the navigation bar, or the keyboard wherever that reaches
    // higher, and the two are spent differently here. The keyboard covers the bottom of the field whatever it is
    // scrolled to, so what it covers beyond the bar is always taken off the field: the field then ends at the top of
    // the keyboard, and keeping the caret in view - which it does whenever its size changes - keeps it above it. The
    // bar and the room after the last line only mean anything once scrolled past, so they are spent at the end alone.
    val restingBottomInset = WindowInsets.contentEdges.asPaddingValues().calculateBottomPadding()
    val endPadding = restingBottomInset + 32.dp
    val endPaddingPx = with(density) { endPadding.roundToPx() }
    // Unlike SongPreview's bottom padding, which sits inside its own verticalScroll and is therefore only ever
    // spent once scrolled past the last line, this field's padding is outside the scrolling BasicTextField manages
    // internally - there is no way to hand it a padding that only counts once the content runs out. Applying it
    // unconditionally reserved that space at all times, edge-to-edge or not. Toggling it off is not simply a matter
    // of asking whether the field is scrolled to its end, either: the padding being decided is also what the field
    // measures its own scrollable height against, so removing it right at the end immediately makes the field think
    // there is further to scroll, which puts the padding straight back - the two states chase each other forever.
    // Requiring the field to have scrolled back up by at least the padding's own height before giving it up is what
    // breaks that loop.
    val respectsBottomInset = remember { mutableStateOf(false) }
    LaunchedEffect(scrollState, endPaddingPx) {
        snapshotFlow { scrollState.value to scrollState.maxValue }.collect { (value, maxValue) ->
            respectsBottomInset.value = if (respectsBottomInset.value) value >= maxValue - endPaddingPx else value >= maxValue
        }
    }
    BasicTextField(
        modifier = modifier
            // The field only ever scrolls along one axis of its own, the vertical one when it holds more than a line,
            // so the sideways scrolling is a container around it. That container measures the field against an
            // unbounded width, which is what keeps the text from wrapping, and it still passes the pane's width on as
            // the minimum, so the whole pane stays the field and a press anywhere in it places the caret. The field
            // asks its ancestors to bring the caret into view as it moves, so this follows the typing on its own.
            // The fade goes outside that container, on the pane itself, so it stays at the pane's top edge.
            .fadingTopEdge(scrollState, MaterialTheme.colorScheme.background)
            .horizontalScroll(horizontalScrollState)
            // The keyboard reaches the field only through the content padding this screen was handed, see CampfireApp,
            // and only the part of it that covers the field is applied, once: applying the whole inset a second time
            // shrinks the field to a couple of lines as soon as the keyboard comes up. There is no top padding for the
            // same reason the bottom one is split: it would sit outside the field's own scrolling, so the text would
            // scroll under a strip of nothing below the toolbar rather than up to its edge. The toolbar's own bottom
            // padding is the space between the two at rest.
            .padding(
                EditorFieldPadding(
                    contentPadding = contentPadding,
                    restingBottomInset = restingBottomInset,
                    endPadding = endPadding,
                    respectsBottomInset = respectsBottomInset,
                )
            ),
        state = textFieldState,
        textStyle = bodyLarge.copy(
            fontFamily = LocalMonospaceFontFamily.current,
            color = colorScheme.onSurface,
        ),
        // Autocorrect and automatic capitalization fight with a format whose words are "[Am]" and "{start_of_verse}".
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        lineLimits = TextFieldLineLimits.MultiLine(),
        outputTransformation = outputTransformation,
        cursorBrush = SolidColor(colorScheme.primary),
        // The field does its own scrolling when it is allowed more than one line; wrapping it in a scrollable
        // swallows the press that should have put the caret in it, and nothing can be typed at all.
        scrollState = scrollState,
    )
}

/**
 * The padding around the editor's field, every side of it asked for while the field is laid out rather than while it
 * is composed: the bottom follows the keyboard, whose inset changes on every frame it slides for, and so does whether
 * the room after the last line is spent, which the field's own scrolling decides. Read while composing, either would
 * compose the whole field again on each of those frames.
 *
 * Only the part of the handed padding that reaches higher than the resting inset is taken off the field, see
 * [ChordProTextField].
 */
@Stable
private class EditorFieldPadding(
    private val contentPadding: PaddingValues,
    private val restingBottomInset: Dp,
    private val endPadding: Dp,
    private val respectsBottomInset: State<Boolean>,
) : PaddingValues {

    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = contentPadding.calculateLeftPadding(layoutDirection) + 16.dp

    override fun calculateTopPadding() = 0.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection) = contentPadding.calculateRightPadding(layoutDirection) + 16.dp

    override fun calculateBottomPadding() = (contentPadding.calculateBottomPadding() - restingBottomInset).coerceAtLeast(0.dp) +
            if (respectsBottomInset.value) endPadding else 0.dp
}

/**
 * The rendered song, kept a beat behind the text so that typing does not re-parse on every keystroke, and parsed away
 * from the main thread.
 *
 * @param scrollState Where the preview is scrolled to, hoisted so that it survives the pane being composed again.
 * @param isSingleColumn Whether the song is stacked in one column (see [SongLyrics]): next to the field, where the
 * preview follows the text being typed. On its own it lays the song out the way the song details screen does.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Composable
private fun SongPreview(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    text: State<String>,
    scrollState: ScrollState,
    transposition: Int,
    fontScale: Float,
    chordSpelling: UserPreferences.ChordSpelling,
    contentPadding: PaddingValues,
    isSingleColumn: Boolean,
) {
    val labels = rememberDefaultSectionLabels()
    val latestTransposition by rememberUpdatedState(transposition)
    val latestChordSpelling by rememberUpdatedState(chordSpelling)
    // Lyrics only mode is about how a song is read, and this preview is here to show what is being written: chords
    // typed into the field opposite it have to appear, or the editor would answer an edit with nothing.
    fun inputsOf(text: String, transposition: Int, spelling: UserPreferences.ChordSpelling) = SongLyricsInputs(
        text = text,
        transposition = transposition,
        spelling = spelling,
        shouldShowChords = true,
        labels = labels,
    )
    fun prepare(inputs: SongLyricsInputs) = prepareSongLyrics(
        song = viewModel.renderSong(inputs.text, inputs.transposition, inputs.spelling),
        shouldShowChords = inputs.shouldShowChords,
        labels = inputs.labels,
    )
    // The first rendering is built right here, so the preview never opens on an empty frame. Every later one is built
    // away from the main thread once the typing pauses, which is exactly when the next key is likely to come, and
    // the one before it stays on screen until it is ready.
    var preview by remember(text) { mutableStateOf(inputsOf(text.value, transposition, chordSpelling).let { it to prepare(it) }) }
    LaunchedEffect(text, labels) {
        snapshotFlow { inputsOf(text.value, latestTransposition, latestChordSpelling) }
            .distinctUntilChanged()
            .debounce(PREVIEW_DELAY_MILLIS)
            .filter { it != preview.first }
            .mapLatest { inputs -> inputs to withContext(Dispatchers.Default) { prepare(inputs) } }
            .collect { preview = it }
    }
    val topPadding = 8.dp
    // The keyboard only ever covers the lower part of the preview, which the scroll room below the last line lets the
    // user scroll past, so it decides that room and not the columns: the height the sections are laid out against is
    // the pane's less the resting inset, and the preview keeps its columns while the keyboard comes and goes.
    val restingBottomPadding = WindowInsets.contentEdges.asPaddingValues().calculateBottomPadding() + 32.dp
    BoxWithConstraints(modifier = modifier) {
        SongLyrics(
            modifier = Modifier
                .fillMaxSize()
                .fadingTopEdge(scrollState, MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = topPadding)
                .padding(contentPadding.only(start = true, end = true, bottom = true, extraBottom = 32.dp)),
            model = preview.second,
            availableHeight = maxHeight - topPadding - restingBottomPadding,
            fontScale = fontScale,
            sectionMotion = SectionMotion.NONE,
            isSingleColumn = isSingleColumn,
        )
    }
}

/**
 * The one action of the editor that is neither writing the file nor undoing a keystroke, behind the same overflow
 * button the song details screen uses. It is a menu of one rather than a button of its own, however much room the bar
 * has ([ActionsMenuItem.isAlwaysInMenu]), because throwing away everything typed since the last save is not something
 * to end up in by mistapping the button next to Save.
 */
@Composable
private fun EditorMenu(
    modifier: Modifier = Modifier,
    canRevert: Boolean,
    onRevert: () -> Unit,
) = ActionsMenu(
    modifier = modifier,
    items = listOf(
        ActionsMenuItem(
            title = stringResource(Res.string.song_editor_revert),
            icon = painterResource(Res.drawable.ic_refresh),
            isEnabled = canRevert,
            isAlwaysInMenu = true,
            onClick = onRevert,
        ),
    ),
)

/**
 * Keeps the view model's copy of the text in step with the field, without writing any of it. It is what tells the
 * app there is something unsaved here, and what it saves if the user asks for it on the way out - by which time
 * this screen, and the field with it, may already be gone.
 */
@Composable
private fun ReportDraft(
    viewModel: CampfireViewModel,
    fileName: String,
    text: State<String>,
) {
    LaunchedEffect(text, fileName) {
        snapshotFlow { text.value }.collect { viewModel.onEditorTextChanged(fileName, it) }
    }
    // Whatever became of the text - saved, discarded, or the song deleted - there is no draft once the editor is gone.
    DisposableEffect(fileName) { onDispose { viewModel.onEditorClosed(fileName) } }
}

/**
 * Puts the file back into the field once the user has confirmed that is what they want. It goes through the field's
 * own editing rather than through a new [TextFieldState], so that a revert is one more step of the undo history and
 * not the end of it.
 */
@Composable
private fun RevertOnRequest(
    viewModel: CampfireViewModel,
    fileName: String,
    textFieldState: TextFieldState,
    summaryCache: ChordProSummaryCache,
) = LaunchedEffect(textFieldState, fileName) {
    viewModel.editorRevertRequests.collect {
        viewModel.songTexts.value[fileName]?.let { text ->
            summaryCache.clear()
            textFieldState.replaceAll(text)
        }
    }
}

/**
 * Keeps an editor that holds nothing of its own in step with its file. Once the file changes underneath it - a sync
 * run, a rescan, another program - a field still holding the text the file had would otherwise count as unsaved, and
 * saving it would write the old version back over the new one, which the next sync run then carries to every device.
 * So the field follows the file for as long as it holds exactly what the file held before the change: anything typed,
 * or a draft reopened on launch, never equals that and is left alone, and a file that has gone leaves the field as it
 * is, since then the field is the only copy. The update is one step of the undo history, like a revert.
 */
@Composable
private fun FollowFileWhileUntouched(
    viewModel: CampfireViewModel,
    fileName: String,
    textFieldState: TextFieldState,
    summaryCache: ChordProSummaryCache,
) = LaunchedEffect(textFieldState, fileName) {
    var previous = viewModel.songTexts.value[fileName]
    viewModel.songTexts.map { it[fileName] }.distinctUntilChanged().collect { current ->
        val base = previous
        previous = current
        if (current != null && base != null && current != base && textFieldState.text.contentEquals(base)) {
            summaryCache.clear()
            textFieldState.replaceAll(current)
        }
    }
}

/**
 * Replaces everything, for the rewrites that touch the whole document. The undo history records such an edit as
 * the whole text twice and keeps a hundred of them, which for a long document is more memory than a phone hands
 * out, so there the history starts over with the rewrite: it can still be undone, what came before it cannot.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun TextFieldState.replaceAll(text: String) {
    if (this.text.length > LONG_DOCUMENT_LENGTH) undoState.clearHistory()
    edit {
        val caret = selection.start.coerceAtMost(text.length)
        delete(0, length)
        insert(0, text)
        selection = TextRange(caret)
    }
}

/**
 * Replaces the document with its transposition, keeping the caret and the selection next to the text they were next
 * to (see [ChordProTransposer.transposedOffset]) rather than at the same character count. The history is treated the
 * way [replaceAll] treats it.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun TextFieldState.replaceWithTransposition(transposed: String) {
    val before = text.toString()
    if (before == transposed) return
    if (before.length > LONG_DOCUMENT_LENGTH) undoState.clearHistory()
    edit {
        val start = ChordProTransposer.transposedOffset(before, transposed, selection.start)
        val end = ChordProTransposer.transposedOffset(before, transposed, selection.end)
        replace(0, length, transposed)
        selection = TextRange(start, end)
    }
}

/**
 * The editor's field, and what became of it on the way back from a saved state.
 *
 * @param isDraftLost True where the field held unsaved text too long to be saved and the process that held it
 * is gone: the field starts from the file then, and the user is told so rather than left to find out.
 */
private class EditorField(
    val textFieldState: TextFieldState,
    val isDraftLost: Boolean = false,
)

/**
 * Saves the field as its text and its selection, and the text only while it is not long.
 *
 * What is saved here crosses to the system in one Binder transaction, together with everything else the Activity
 * saves and out of about a megabyte for the whole process; past that Android kills the app as it goes to the
 * background. The field's own saver also writes the undo history, in which every rewrite of the whole document (a
 * transposition, a revert) is the whole text twice, so a dozen taps on a long song add up to that megabyte.
 *
 * What this gives up is only ever wanted after a configuration change - a change of language or dark mode restores
 * through here as well - and the view model lives through those, so the field itself is handed to it ([retain]) and
 * taken back as it is, undo history included and however long. A new process gets the text and the caret, or for a
 * long document the file.
 */
private class EditorFieldSaver(
    private val retain: (TextFieldState) -> Unit,
    private val retained: () -> TextFieldState?,
    private val fileText: () -> String,
) : Saver<EditorField, Any> {

    override fun SaverScope.save(value: EditorField): Any {
        val textFieldState = value.textFieldState
        retain(textFieldState)
        val text = textFieldState.text.toString()
        return if (text.length <= LONG_DOCUMENT_LENGTH) {
            listOf(text, textFieldState.selection.start, textFieldState.selection.end)
        } else {
            // Whether there was anything to lose, so that an untouched long file does not come back with an apology.
            listOf(text != fileText())
        }
    }

    override fun restore(value: Any): EditorField {
        retained()?.let { return EditorField(it) }
        val saved = value as List<*>
        return if (saved.size == 1) {
            EditorField(textFieldState = TextFieldState(initialText = fileText()), isDraftLost = saved[0] as Boolean)
        } else {
            EditorField(
                TextFieldState(
                    initialText = saved[0] as String,
                    initialSelection = TextRange(start = saved[1] as Int, end = saved[2] as Int),
                )
            )
        }
    }
}

/**
 * The start of the line after the first `{start_of_…}`, which in a freshly created song is the blank line its
 * template leaves for the first verse.
 */
private fun String.caretInsideFirstSection(): Int {
    val sectionStart = indexOf(SECTION_START)
    if (sectionStart == -1) return length
    val lineBreak = indexOf('\n', sectionStart)
    return if (lineBreak == -1) length else lineBreak + 1
}

private val PANE_CHOICE_MAX_WIDTH = 400.dp
private val SMALL_SCREEN_SIZE = 600.dp
private const val SECTION_START = "{start_of_"
private const val PREVIEW_DELAY_MILLIS = 150L
private val COVER_ART_DELAY = 500.milliseconds

/**
 * What the editor takes for a long document: 100 KB as a saved state writes it, several times the longest song
 * and a tenth of what the transaction has room for.
 */
private const val LONG_DOCUMENT_LENGTH = 50_000
