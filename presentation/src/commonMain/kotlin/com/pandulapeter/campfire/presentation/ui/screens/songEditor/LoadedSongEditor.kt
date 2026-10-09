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
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
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
import com.pandulapeter.campfire.chordpro.model.ChordProSummary
import com.pandulapeter.campfire.chordpro.model.displayTitle
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.normalizedTags
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.ic_redo
import com.pandulapeter.campfire.presentation.resources.ic_save
import com.pandulapeter.campfire.presentation.resources.ic_undo
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_editor_edit
import com.pandulapeter.campfire.presentation.resources.song_editor_preview
import com.pandulapeter.campfire.presentation.resources.song_editor_redo
import com.pandulapeter.campfire.presentation.resources.song_editor_split
import com.pandulapeter.campfire.presentation.resources.song_editor_undo
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.presentation.ui.chords.toChordInstrument
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.CampfireTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.overlappingAction
import com.pandulapeter.campfire.presentation.ui.components.saveShortcut
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.components.WindowSize
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.TextTranspositionControls
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.coverArtAction
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.rememberSongInfoEditing
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.songInfoEditingActions
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.songPlayingAction
import com.pandulapeter.campfire.presentation.ui.platform.CompactKeyboardEffect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
                retain = { viewModel.retainEditorField(destination.fileName, it) },
                retained = { viewModel.retainedEditorField(destination.fileName) },
                recovered = { viewModel.recoveredEditorField(destination.fileName) },
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
    val songRenderer = viewModel.songRenderer
    val notation = viewModel.editorNotation
    // Compare with the same formatted draft the action applies, so the option follows typing, undo and revert. Only the
    // open menu reads it (ActionsMenuItem.isEnabledInMenu), so typing never prettifies the whole song.
    val prettifiedText = remember(textFieldState, viewModel) { derivedStateOf { songRenderer.prettifyText(text.value) } }
    ReportDraft(viewModel = viewModel, fileName = destination.fileName, text = text, textFieldState = textFieldState)

    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSavingSong.collectAsStateWithLifecycle()
    // The bar above the text follows the text as it is typed, so retitling a song shows up there right away. It
    // comes from the parser rather than from a regex of this screen's own, so that it is the same title, artist and
    // key the rest of the app will show once the file is written - the fallback to the file name included. One
    // summary rather than a parse per field: it also answers whether there is anything left to transpose.
    val summaryCache = remember(textFieldState) { songRenderer.editorSummaryCache(notation) }
    val summary by remember(textFieldState) { derivedStateOf { summaryCache.summaryOf(text.value) } }
    val hasUnsavedChanges by viewModel.hasUnsavedEditorChanges.collectAsStateWithLifecycle()
    RevertOnRequest(viewModel = viewModel, fileName = destination.fileName, textFieldState = textFieldState, summaryCache = summaryCache)
    EditOnRequest(viewModel = viewModel, fileName = destination.fileName, textFieldState = textFieldState, summaryCache = summaryCache)
    val editorSong = summary.toEditorSong(destination.fileName)
    val songInfoEditing = rememberSongInfoEditing(viewModel = viewModel, song = editorSong, target = SongEditTarget.EditorDraft(editorSong.fileName))
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
    val fontScale = userPreferences?.fontScale ?: UserPreferences.DEFAULT_FONT_SCALE
    val chordSpelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
    // The two rows of insertions are taller than what a phone has left for the text once the keyboard is up, so a
    // phone opens the editor with them folded away. The shorter side of the window is what decides that rather than
    // the width class: a phone turned sideways is wide enough to count as a large screen and is the one with the
    // least height of all.
    val windowContainerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val isSmallScreen = with(density) { minOf(windowContainerSize.width, windowContainerSize.height).toDp() } < SMALL_SCREEN_SIZE
    // Derived rather than read here, so that the keyboard sliding in recomposes the editor only as it crosses a line
    // these care about, not on every frame of its animation.
    val ime = WindowInsets.ime
    val windowHeight = with(density) { windowContainerSize.height.toDp() }
    val isKeyboardVisibleState = remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    val isTypingInShortWindowState = remember(ime, density, windowHeight) {
        derivedStateOf {
            val imeHeight = with(density) { ime.getBottom(this).toDp() }
            imeHeight > 0.dp && windowHeight - imeHeight < SHORT_WINDOW_HEIGHT
        }
    }
    // Above a phone's landscape keyboard the control row would leave the field a single line, so there it gives way
    // and only its Shortcuts chevron stays, in the title row; transposing and switching panes wait for the keyboard
    // to go.
    val isControlRowHiddenState = remember(ime, density, windowHeight) {
        derivedStateOf {
            val imeHeight = with(density) { ime.getBottom(this).toDp() }
            imeHeight > 0.dp && windowHeight - imeHeight < MIN_ROOM_FOR_CONTROL_ROW
        }
    }
    val isKeyboardVisible by isKeyboardVisibleState
    val isTypingInShortWindow by isTypingInShortWindowState
    val isControlRowHidden by isControlRowHiddenState
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
        if (!isSmallScreen) snapshotFlow { isTypingInShortWindowState.value }.first { it }
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
                AnimatedVisibility(
                    // Outside the animated width (see overlappingAction's KDoc), and at its end: it is the first action,
                    // and the undo button after it is drawn last, so a press on the overlap still reaches undo.
                    modifier = Modifier.overlappingAction(start = 0.dp, end = ACTION_BUTTON_OVERLAP),
                    visible = isControlRowHidden && panes != EditorPanes.PREVIEW,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut(),
                ) {
                    EditorToolbarToggle(
                        isExpanded = isToolbarExpanded,
                        isLabeled = false,
                        isEnabled = true,
                        onToggled = { isToolbarExpanded = !isToolbarExpanded },
                    )
                }
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
                    editingActions = songInfoEditingActions(songInfoEditing),
                    // The sheet edits what the two features show, so it goes once both are switched off.
                    songPlayingAction = if (userPreferences?.areChordsEnabled != false || userPreferences?.isMetronomeEnabled != false) {
                        songPlayingAction(viewModel = viewModel, song = editorSong, setlistFileName = null, target = SongEditTarget.EditorDraft(editorSong.fileName))
                    } else {
                        null
                    },
                    coverArtAction = if (userPreferences?.isCoverArtEnabled == true) {
                        coverArtAction(viewModel = viewModel, song = editorSong, target = SongEditTarget.EditorDraft(editorSong.fileName))
                    } else {
                        null
                    },
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
                // Transposing, switching pane and folding the insertions away are the things here that do not write at
                // the caret, so they are the ones that stay when the insertions leave.
                AnimatedVisibility(visible = !isControlRowHidden) {
                    Row(
                        modifier = Modifier.padding(
                            start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                            end = contentPadding.calculateEndPadding(layoutDirection) + 4.dp,
                            bottom = 8.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextTranspositionControls(
                            // The summary reads the key into the standard notation, and the field is in the editor's.
                            key = summary.metadata.key?.let { songRenderer.editorKeyOf(it, notation) },
                            // A key is enough on its own: it says what the song is in, and moving it is a transposition
                            // even before a chord has been written under it.
                            isEnabled = summary.hasChords || !summary.metadata.key.isNullOrBlank(),
                            onTransposed = { semitones ->
                                textFieldState.replaceWithTransposition(songRenderer.transposeText(textFieldState.text.toString(), semitones, chordSpelling.accidentals, notation))
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
                            shouldApplyPadding = false,
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
                }
                // Nothing below can act on a preview, so the insertions leave with the field they write into.
                AnimatedVisibility(visible = isToolbarExpanded && panes != EditorPanes.PREVIEW) {
                    EditorToolbar(
                        textFieldState = textFieldState,
                        text = text,
                        contentPadding = contentPadding,
                        chordShapes = remember(userPreferences) {
                            val instrument = (userPreferences?.chordInstrument ?: UserPreferences.ChordInstrument.GUITAR).toChordInstrument()
                            EditorChordShapes(
                                notation = notation.toChordNotation(),
                                instrument = instrument,
                                storedShapes = userPreferences?.chordVoicings?.get(instrument.id).orEmpty(),
                            )
                        },
                    )
                }
            },
        )
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
 * Whether the editor's field holds the focus, kept outside the snapshot system: it is only read at the moment the
 * panes change, and state read there would compose the whole screen again on every focus change of the field.
 */
private class FieldFocus {
    var hasFocus = false
}

private const val CHORDPRO_REFERENCE_URL = "https://www.chordpro.org/chordpro/chordpro-directives/"

/**
 * The song as the text being typed describes it, which is what the metadata dialogs of the preview's card and the cover
 * art sheet of the editor's menu are opened on: the library's entry describes the file, which the text may already
 * have moved away from.
 */
internal fun ChordProSummary.toEditorSong(fileName: String) = Song(
    fileName = fileName,
    title = metadata.displayTitle(fileName.removeSuffix(LibraryFiles.SONG_EXTENSION)),
    artist = metadata.artist?.takeIf { it.isNotBlank() }.orEmpty(),
    key = metadata.key?.takeIf { it.isNotBlank() },
    transpose = metadata.transpose,
    tags = normalizedTags(metadata.tags),
    languages = metadata.languages,
    coverArtUrl = metadata.coverArt,
    hasChords = hasChords,
    canUpdateFileName = false,
    lastModified = 0,
    size = 0,
)

/**
 * The start of the line after the first `{start_of_…}`, which in a freshly created song is the blank line its
 * template leaves for the first verse.
 */
internal fun String.caretInsideFirstSection(): Int {
    val sectionStart = indexOf(SECTION_START)
    if (sectionStart == -1) return length
    val lineBreak = indexOf('\n', sectionStart)
    return if (lineBreak == -1) length else lineBreak + 1
}

private val PANE_CHOICE_MAX_WIDTH = 400.dp
private val SMALL_SCREEN_SIZE = 600.dp

/**
 * The least the keyboard has to leave of the window for the editor's control row to stay over the field: the 48dp
 * compact title row, the 56dp control row and three 18sp lines with the field's padding. The smallest phone keeps
 * about 340dp upright and so keeps the row, and about 112dp on its side and so loses it.
 */
private val MIN_ROOM_FOR_CONTROL_ROW = 240.dp
private const val SECTION_START = "{start_of_"
