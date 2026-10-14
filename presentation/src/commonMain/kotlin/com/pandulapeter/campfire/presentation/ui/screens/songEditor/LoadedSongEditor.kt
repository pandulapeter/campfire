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

import com.pandulapeter.campfire.presentation.ui.screens.songEditor.toolbar.EditorChordShapes
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.toolbar.EditorControlRow
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.toolbar.EditorToolbar
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.toolbar.isEnabled
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.search.EditorSearchMode
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.search.EditorSearchSheet
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.search.editorSearchShortcuts
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.search.rememberEditorSearch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.model.displayTitle
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.presentation.ui.instrument.chordTuning
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.appBar.CampfireTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.saveShortcut
import com.pandulapeter.campfire.presentation.ui.components.WindowSize
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentTuning
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.songInfo.rememberSongInfoEditing
import com.pandulapeter.campfire.presentation.ui.platform.CompactKeyboardEffect
import com.pandulapeter.campfire.presentation.ui.tabs.emptyTabStaffFor
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun LoadedSongEditor(
    viewModel: CampfireViewModel,
    destination: CampfireDestination.SongEditor,
    initialText: String,
    hasSavedText: Boolean,
    windowSize: WindowSize,
    contentPadding: PaddingValues,
    urlOpener: (String) -> Unit,
    onBack: () -> Unit,
) {
    // Keyed on the file name so that opening another song starts a new field with its own undo history, and saved
    // so that a rotation or a trip through process death does not lose what has been typed, see EditorFieldSaver.
    val fileText by rememberUpdatedState(initialText)
    val editorField = rememberSaveable(
        destination.fileName,
        saver = remember(viewModel, destination.fileName) {
            EditorFieldSaver(
                retain = { viewModel.editorSession.retainEditorField(destination.fileName, it) },
                retained = { viewModel.editorSession.retainedEditorField(destination.fileName) },
                recovered = { viewModel.editorSession.recoveredEditorField(destination.fileName) },
                fileText = { fileText },
            )
        },
    ) {
        // A field the view model already holds for this file is taken as it is: the draft a previous run left, reopened
        // on launch. Otherwise a new one over the file's text.
        EditorField(
            viewModel.editorSession.retainedEditorField(destination.fileName) ?: TextFieldState(
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
        if (editorField.isDraftLost) viewModel.editorSession.onEditorDraftLost()
    }
    // The text as one string, copied once per edit and shared by everything that follows it: the draft, the summary
    // in the bar, the toolbar and the preview would otherwise each copy and scan the whole song on every keystroke.
    val text = remember(textFieldState) { derivedStateOf { textFieldState.text.toString() } }
    val songRenderer = viewModel.songRenderer
    val notation = viewModel.editorSession.editorNotation
    // Compare with the same formatted draft the action applies, so the option follows typing, undo and revert. Only the
    // open menu reads it (ActionsMenuItem.isEnabledInMenu), so typing never prettifies the whole song.
    val prettifiedText = remember(textFieldState, viewModel) { derivedStateOf { songRenderer.prettifyText(text.value) } }
    val search = rememberEditorSearch(textFieldState = textFieldState, text = text)
    ReportDraft(viewModel = viewModel, fileName = destination.fileName, text = text, textFieldState = textFieldState)

    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val isSaving by viewModel.editorSession.isSavingSong.collectAsStateWithLifecycle()
    // The bar above the text follows the text as it is typed, so retitling a song shows up there right away. It
    // comes from the parser rather than from a regex of this screen's own, so that it is the same title, artist and
    // key the rest of the app will show once the file is written - the fallback to the file name included. One
    // summary rather than a parse per field: it also answers whether there is anything left to transpose.
    val summaryCache = remember(textFieldState) { songRenderer.editorSummaryCache(notation) }
    val summary by remember(textFieldState) { derivedStateOf { summaryCache.summaryOf(text.value) } }
    val hasUnsavedChanges by viewModel.editorSession.hasUnsavedEditorChanges.collectAsStateWithLifecycle()
    RevertOnRequest(viewModel = viewModel, fileName = destination.fileName, textFieldState = textFieldState, summaryCache = summaryCache)
    EditOnRequest(viewModel = viewModel, fileName = destination.fileName, textFieldState = textFieldState, summaryCache = summaryCache)
    val editorSong = summary.toEditorSong(destination.fileName)
    val songInfoEditing = rememberSongInfoEditing(viewModel = viewModel, song = editorSong, setlistFileName = null, target = SongEditTarget.EditorDraft(editorSong.fileName))
    val latestEditorSong by rememberUpdatedState(editorSong)
    val onOpenSongInfo = remember(viewModel) {
        { viewModel.showDialog(DialogType.SongInfo(song = latestEditorSong, target = SongEditTarget.EditorDraft(latestEditorSong.fileName))) }
    }
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
    val panes = shownPanes(selected = selectedPanes, hasRoomForSplitPanes = hasRoomForSplitPanes)
    val hasSideBySidePreview = panes == EditorPanes.SPLIT
    // The matches are found in the field and shown there, so a search started over the preview brings the field back.
    val onOpenSearch = { mode: EditorSearchMode ->
        if (panes == EditorPanes.PREVIEW) selectedPanes = EditorPanes.EDIT
        search.open(mode)
    }
    val fontScale = userPreferences?.fontScale ?: UserPreferences.DEFAULT_FONT_SCALE
    val chordSpelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
    val windowContainerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val keyboard = rememberEditorKeyboard()
    val isSmallScreen = keyboard.isSmallScreen
    val isKeyboardVisible by keyboard.isKeyboardVisibleState
    val isTypingInShortWindow by keyboard.isTypingInShortWindowState
    val isControlRowHidden by keyboard.isControlRowHiddenState
    CompactKeyboardEffect(isEnabled = isTypingInShortWindow && LocalWindowInfo.current.containerDpSize.height < SHORT_WINDOW_HEIGHT)
    var isToolbarExpanded by rememberSaveable {
        mutableStateOf(!isSmallScreen)
    }
    // Collapse once when the keyboard appears. The user can reopen the shortcuts while typing, and closing
    // the keyboard leaves their current choice intact.
    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible) return@LaunchedEffect
        // The keyboard's inset is animated, so its first frame is only a few pixels and a large window is not short
        // yet: the rows are folded once the keyboard has grown far enough to make it so, if it ever does.
        if (!isSmallScreen) snapshotFlow { keyboard.isTypingInShortWindowState.value }.first { it }
        isToolbarExpanded = false
    }
    // Key events only travel along the focus path, and nothing in the editor is focused until the text is clicked,
    // nor at all while the preview is the only pane - or after the panes change places, which composes the field
    // again. So the editor takes the focus itself as it opens and whenever the panes change, and the save shortcut
    // sits in its preview pass, where it hears the key wherever inside the editor the focus has gone since: the field
    // once it is clicked, the preview, a button of the bar. A field that was being typed in when the panes changed
    // without the user asking - a rotation or a resize across the width Split needs - gets the focus back instead,
    // so the caret stays where it was and the keyboard stays up. Whether it had it is read in the composition that
    // changes the panes, before the old field is disposed and reports losing it.
    val focusRequester = remember { FocusRequester() }
    val fieldFocusRequester = remember { FocusRequester() }
    val fieldFocus = remember { FieldFocus() }
    val wasFieldFocused = remember(panes) { fieldFocus.hasFocus }
    LaunchedEffect(panes) {
        if (wasFieldFocused && panes != EditorPanes.PREVIEW) fieldFocusRequester.requestFocus() else focusRequester.requestFocus()
    }

    val layoutDirection = LocalLayoutDirection.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .saveShortcut(onSaveRequested)
            .editorSearchShortcuts(
                onFind = { onOpenSearch(EditorSearchMode.FIND) },
                onReplace = { onOpenSearch(EditorSearchMode.REPLACE) },
            )
    ) {
        CampfireTopAppBar(
            isCompact = isTypingInShortWindow,
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
                EditorAppBarActions(
                    textFieldState = textFieldState,
                    isToolbarToggleShown = isControlRowHidden && panes != EditorPanes.PREVIEW,
                    isToolbarExpanded = isToolbarExpanded,
                    onToolbarToggled = { isToolbarExpanded = !isToolbarExpanded },
                    canSave = hasUnsavedChanges && !isSaving,
                    onSave = onSaveRequested,
                    menuMaxWidth = editorMenuMaxWidth(
                        appBarWidth = with(density) { windowContainerSize.width.toDp() } -
                            contentPadding.calculateStartPadding(layoutDirection) - contentPadding.calculateEndPadding(layoutDirection),
                        isToolbarToggleShown = isControlRowHidden && panes != EditorPanes.PREVIEW,
                        hasCover = userPreferences?.isCoverArtEnabled == true,
                    ),
                    onFind = { onOpenSearch(EditorSearchMode.FIND) },
                    onFindAndReplace = { onOpenSearch(EditorSearchMode.REPLACE) },
                    onOpenSongInfo = onOpenSongInfo,
                    canPrettify = { !isSaving && text.value.isNotBlank() && prettifiedText.value != text.value },
                    onPrettify = {
                        val prettified = prettifiedText.value
                        if (prettified != text.value) textFieldState.replaceWithPrettification(prettified)
                    },
                    canRevert = hasUnsavedChanges && hasSavedText && !isSaving,
                    onRevert = { viewModel.showDialog(DialogType.RevertChanges) },
                    onOpenChordProReference = { urlOpener(CHORDPRO_REFERENCE_URL) },
                )
            },
            bottomContent = {
                AnimatedVisibility(visible = !isControlRowHidden) {
                    EditorControlRow(
                        contentPadding = contentPadding,
                        // The summary reads the key into the standard notation, and the field is in the editor's.
                        key = summary.metadata.key?.let { songRenderer.editorKeyOf(it, notation) },
                        // A key is enough on its own: it says what the song is in, and moving it is a transposition
                        // even before a chord has been written under it.
                        isTranspositionEnabled = summary.hasChords || !summary.metadata.key.isNullOrBlank(),
                        onTransposed = { semitones ->
                            textFieldState.replaceWithTransposition(songRenderer.transposeText(textFieldState.text.toString(), semitones, chordSpelling.accidentals, notation))
                        },
                        panes = panes,
                        hasRoomForSplitPanes = hasRoomForSplitPanes,
                        onPanesSelected = { selectedPanes = it },
                        isToolbarExpanded = isToolbarExpanded,
                        isToolbarToggleLabeled = windowSize != WindowSize.COMPACT,
                        onToolbarToggled = { isToolbarExpanded = !isToolbarExpanded },
                    )
                }
                // Nothing below can act on a preview, so the insertions leave with the field they write into.
                AnimatedVisibility(visible = isToolbarExpanded && panes != EditorPanes.PREVIEW) {
                    EditorToolbar(
                        textFieldState = textFieldState,
                        text = text,
                        contentPadding = contentPadding,
                        chordShapes = remember(userPreferences) {
                            // Other draws no diagrams, but a definition is still worth writing for whoever reads the
                            // file on a guitar, the instrument ChordPro takes a `{define}` without one to be for.
                            val tuning = userPreferences?.chordTuning() ?: ChordTuning.GUITAR
                            EditorChordShapes(
                                notation = notation.toChordNotation(),
                                tuning = tuning,
                                storedShapes = userPreferences?.chordVoicings?.get(tuning.instrument.id).orEmpty(),
                                onWrittenForStandardTuning = viewModel::onChordShapeWrittenForStandardTuning,
                            )
                        },
                        tabStaff = remember(userPreferences) {
                            val instrument = userPreferences?.instrument ?: UserPreferences.Instrument.GUITAR
                            val tuning = userPreferences?.instrumentTuning
                            { tabInstrument -> emptyTabStaffFor(tabInstrument, instrument, tuning, notation.toChordNotation()) }
                        },
                    )
                }
            },
        )
        val searchHighlights = remember(search) { { search.highlights } }
        val searchSheetTop = remember(search) { { search.sheetTop } }
        val editor: @Composable (Modifier) -> Unit = { paneModifier ->
            ChordProTextField(
                modifier = paneModifier,
                fieldModifier = Modifier
                    .focusRequester(fieldFocusRequester)
                    .onFocusChanged { fieldFocus.hasFocus = it.hasFocus },
                textFieldState = textFieldState,
                isCompactTyping = isTypingInShortWindow,
                scrollState = fieldScrollState,
                horizontalScrollState = fieldHorizontalScrollState,
                // Next to the preview the divider is the end of this pane, not the window.
                contentPadding = contentPadding.only(start = true, end = !hasSideBySidePreview, bottom = true),
                searchHighlights = searchHighlights,
                searchSheetTop = searchSheetTop,
            )
        }
        val preview: @Composable (Modifier) -> Unit = { paneModifier ->
            SongPreview(
                modifier = paneModifier,
                viewModel = viewModel,
                text = text,
                scrollState = previewScrollState,
                fontScale = fontScale,
                chordSpelling = chordSpelling,
                contentPadding = contentPadding.only(start = !hasSideBySidePreview, end = true, bottom = true),
                isSingleColumn = hasSideBySidePreview,
                songInfoEditing = songInfoEditing,
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
    if (search.mode != null) {
        EditorSearchSheet(
            search = search,
            // The match the search was at is selected in the field by now, and the caret goes back to it, so that what
            // was looked for is what is typed over next.
            onDismiss = {
                search.close()
                fieldFocusRequester.requestFocus()
            },
        )
    }
}

/**
 * Whether the editor's field holds the focus, kept outside the snapshot system: it is only read at the moment the
 * panes change, and state read there would compose the whole screen again on every focus change of the field.
 */
private class FieldFocus {
    var hasFocus = false
}

private const val CHORDPRO_REFERENCE_URL = "https://www.chordpro.org/chordpro/chordpro-directives/"
