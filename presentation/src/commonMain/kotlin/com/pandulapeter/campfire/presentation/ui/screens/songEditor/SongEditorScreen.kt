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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.displayTitle
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.edit
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.ic_redo
import com.pandulapeter.campfire.presentation.resources.ic_refresh
import com.pandulapeter.campfire.presentation.resources.ic_save
import com.pandulapeter.campfire.presentation.resources.ic_undo
import com.pandulapeter.campfire.presentation.resources.song_editor_preview
import com.pandulapeter.campfire.presentation.resources.song_editor_redo
import com.pandulapeter.campfire.presentation.resources.song_editor_revert
import com.pandulapeter.campfire.presentation.resources.song_editor_save
import com.pandulapeter.campfire.presentation.resources.song_editor_split
import com.pandulapeter.campfire.presentation.resources.song_editor_undo
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.components.CampfireTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.components.WindowSize
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongLyrics
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.TextTranspositionControls
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.painterResource

/**
 * The raw ChordPro text of one song, with what it will look like next to it. Wide windows show both at once, narrow
 * ones one at a time.
 *
 * The text lives in a [TextFieldState] here rather than in the view model: it is the field's own state, undo history
 * included, and it is saved across configuration changes and process death with the field's own saver. Nothing is
 * ever written on its own: the file changes when the user saves it, and leaving with something unsaved asks first
 * (see [CampfireViewModel.navigateBack]). What the screen does report as it is typed is the text itself, which is
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
    LaunchedEffect(destination.fileName) { viewModel.loadSongContent(destination.fileName) }
    AnimatedContent(
        modifier = modifier.fillMaxSize(),
        targetState = songTexts[destination.fileName],
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it != null },
    ) { initialText ->
        if (initialText == null) {
            LoadingPane(contentPadding = contentPadding)
        } else {
            LoadedSongEditor(
                viewModel = viewModel,
                destination = destination,
                initialText = initialText,
                windowSize = windowSize,
                contentPadding = contentPadding,
                onBack = onBack,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingPane(contentPadding: PaddingValues) = Box(
    modifier = Modifier.fillMaxSize().padding(contentPadding),
    contentAlignment = Alignment.Center,
) {
    ContainedLoadingIndicator()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun LoadedSongEditor(
    viewModel: CampfireViewModel,
    destination: CampfireDestination.SongEditor,
    initialText: String,
    windowSize: WindowSize,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    // Keyed on the file name so that opening another song starts a new field with its own undo history, and saved
    // so that a rotation or a trip through process death does not lose what has been typed.
    val textFieldState = rememberSaveable(destination.fileName, saver = TextFieldState.Saver) {
        TextFieldState(
            initialText = initialText,
            initialSelection = if (destination.shouldStartInsideFirstSection) {
                TextRange(initialText.caretInsideFirstSection())
            } else {
                TextRange.Zero
            },
        )
    }
    ReportDraft(viewModel = viewModel, fileName = destination.fileName, textFieldState = textFieldState)

    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSavingSong.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // The bar above the text follows the text as it is typed, so retitling a song shows up there right away. It
    // comes from the parser rather than from a regex of this screen's own, so that it is the same title, artist and
    // key the rest of the app will show once the file is written - the fallback to the file name included. One
    // summary rather than a parse per field: it also answers whether there is anything left to transpose.
    val summary by remember(textFieldState) {
        derivedStateOf { ChordProParser.summarize(textFieldState.text.toString()) }
    }
    val hasUnsavedChanges by viewModel.hasUnsavedEditorChanges.collectAsStateWithLifecycle()
    RevertOnRequest(viewModel = viewModel, fileName = destination.fileName, textFieldState = textFieldState)
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

    Column(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        CampfireTopAppBar(
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_clear),
                        contentDescription = stringResource(Res.string.close),
                    )
                }
            },
            title = {
                Column {
                    Text(
                        text = summary.metadata.displayTitle(destination.fileName.removeSuffix(LibraryFiles.SONG_EXTENSION)),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = summary.metadata.artist.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                    enabled = textFieldState.undoState.canRedo,
                    onClick = { textFieldState.undoState.redo() },
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_redo),
                        contentDescription = stringResource(Res.string.song_editor_redo),
                    )
                }
                IconButton(
                    enabled = hasUnsavedChanges && !isSaving,
                    onClick = onSaveRequested,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_save),
                        contentDescription = stringResource(Res.string.song_editor_save),
                    )
                }
                EditorMenu(
                    canRevert = hasUnsavedChanges && !isSaving,
                    onRevert = { viewModel.showDialog(CampfireViewModel.DialogType.RevertChanges) },
                )
            },
            bottomContent = {
                // Transposing and switching pane are the two things here that do not write at the caret, so they
                // are the two that stay when the insertions leave.
                Row(
                    modifier = Modifier.padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextTranspositionControls(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        key = summary.metadata.key,
                        // A key is enough on its own: it says what the song is in, and moving it is a transposition
                        // even before a chord has been written under it.
                        isEnabled = summary.hasChords || !summary.metadata.key.isNullOrBlank(),
                        onTransposed = { semitones ->
                            textFieldState.replaceAll(viewModel.transposeText(textFieldState.text.toString(), semitones, chordSpelling.accidentals))
                        },
                    )
                    SegmentedChoice(
                        // Not the whole of a desktop window's width: the row is a pair of controls rather than a
                        // tab bar, and the stepper next to it would be lost at the end of a metre of segments.
                        modifier = Modifier.weight(1f, fill = false).widthIn(max = PANE_CHOICE_MAX_WIDTH),
                        options = listOfNotNull(
                            EditorPanes.EDIT to stringResource(Res.string.edit),
                            EditorPanes.PREVIEW to stringResource(Res.string.song_editor_preview),
                            if (hasRoomForSplitPanes) EditorPanes.SPLIT to stringResource(Res.string.song_editor_split) else null,
                        ),
                        selected = panes,
                        onSelected = { selectedPanes = it },
                    )
                }
                // Nothing below can act on a preview, so the insertions leave with the field they write into.
                AnimatedVisibility(visible = panes != EditorPanes.PREVIEW) {
                    EditorToolbar(
                        textFieldState = textFieldState,
                        contentPadding = contentPadding,
                    )
                }
            },
        )
        val layoutDirection = LocalLayoutDirection.current
        val editor: @Composable (Modifier) -> Unit = { paneModifier ->
            ChordProTextField(
                modifier = paneModifier,
                textFieldState = textFieldState,
                onSaveRequested = onSaveRequested,
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    // Next to the preview the divider is the end of this pane, not the window.
                    end = if (hasSideBySidePreview) 0.dp else contentPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
            )
        }
        val preview: @Composable (Modifier) -> Unit = { paneModifier ->
            SongPreview(
                modifier = paneModifier,
                viewModel = viewModel,
                textFieldState = textFieldState,
                transposition = transpositions[destination.fileName, null],
                fontScale = fontScale,
                isHorizontalFlow = userPreferences?.isHorizontalSectionFlowEnabled == true,
                chordSpelling = chordSpelling,
                contentPadding = PaddingValues(
                    start = if (hasSideBySidePreview) 0.dp else contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
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
 * The text itself. Monospaced, because a tab or a grid only lines up in one and because a ChordPro document is
 * source rather than prose.
 *
 * The text size preference deliberately does not reach here: it is how large the lyrics are read from across a
 * room, and this is the source of the file rather than the song. Scaling it would also move the columns of a tab
 * away from the width the monospaced font is keeping them at.
 */
@Composable
private fun ChordProTextField(
    modifier: Modifier = Modifier,
    textFieldState: TextFieldState,
    onSaveRequested: () -> Unit,
    contentPadding: PaddingValues,
) {
    val colorScheme = MaterialTheme.colorScheme
    val outputTransformation = remember(colorScheme) {
        ChordProOutputTransformation.of(
            primaryColor = colorScheme.primary,
            secondaryColor = colorScheme.onSurfaceVariant,
            outlineColor = colorScheme.outline,
        )
    }
    val bodyLarge = MaterialTheme.typography.bodyLarge
    val layoutDirection = LocalLayoutDirection.current
    BasicTextField(
        modifier = modifier
            // The same save as the app bar's button, for the hand that reaches for the keyboard instead. It lives on
            // the field rather than in the window's key handler, which has no way to reach this text.
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.S && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)) {
                    onSaveRequested()
                    true
                } else {
                    false
                }
            }
            // The keyboard is already in the content padding this screen was handed, see CampfireApp; applying the
            // inset a second time here shrank the field to a couple of lines as soon as the keyboard came up.
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 32.dp,
            ),
        state = textFieldState,
        textStyle = bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            color = colorScheme.onSurface,
        ),
        // Autocorrect and automatic capitalization fight with a format whose words are "[Am]" and "{start_of_verse}".
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        lineLimits = TextFieldLineLimits.MultiLine(),
        outputTransformation = outputTransformation,
        cursorBrush = SolidColor(colorScheme.primary),
        // The field does its own scrolling when it is allowed more than one line; wrapping it in a scrollable
        // swallows the press that should have put the caret in it, and nothing can be typed at all.
        scrollState = rememberScrollState(),
    )
}

/** The rendered song, kept a beat behind the text so that typing does not re-parse on every keystroke. */
@OptIn(FlowPreview::class)
@Composable
private fun SongPreview(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    textFieldState: TextFieldState,
    transposition: Int,
    fontScale: Float,
    isHorizontalFlow: Boolean,
    chordSpelling: UserPreferences.ChordSpelling,
    contentPadding: PaddingValues,
) {
    var previewedText by remember(textFieldState) { mutableStateOf(textFieldState.text.toString()) }
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .debounce(PREVIEW_DELAY_MILLIS)
            .collect { previewedText = it }
    }
    val song = remember(previewedText, transposition, chordSpelling) { viewModel.renderSong(previewedText, transposition, chordSpelling) }
    val layoutDirection = LocalLayoutDirection.current
    val scrollState = rememberScrollState()
    val topPadding = 8.dp
    val bottomPadding = contentPadding.calculateBottomPadding() + 32.dp
    BoxWithConstraints(modifier = modifier) {
        SongLyrics(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    top = topPadding,
                    bottom = bottomPadding,
                ),
            song = song,
            availableHeight = maxHeight - topPadding - bottomPadding,
            // Lyrics only mode is about how a song is read, and this preview is here to show what is being written:
            // chords typed into the field opposite it have to appear, or the editor would answer an edit with nothing.
            shouldShowChords = true,
            fontScale = fontScale,
            isHorizontalFlow = isHorizontalFlow,
            scrollState = scrollState,
        )
    }
}

/**
 * The one action of the editor that is neither writing the file nor undoing a keystroke, behind the same overflow
 * button the song details screen uses. It is a menu of one rather than a button of its own, because throwing away
 * everything typed since the last save is not something to end up in by mistapping the button next to Save.
 */
@Composable
private fun EditorMenu(
    canRevert: Boolean,
    onRevert: () -> Unit,
) = ActionsMenu { dismiss ->
    ActionsMenuItem(
        title = stringResource(Res.string.song_editor_revert),
        icon = painterResource(Res.drawable.ic_refresh),
        isEnabled = canRevert,
        onClick = {
            dismiss()
            onRevert()
        },
    )
}

/**
 * Keeps the view model's copy of the text in step with the field, without writing any of it. It is what tells the
 * app there is something unsaved here, and what it saves if the user asks for it on the way out - by which time
 * this screen, and the field with it, may already be gone.
 */
@Composable
private fun ReportDraft(
    viewModel: CampfireViewModel,
    fileName: String,
    textFieldState: TextFieldState,
) {
    LaunchedEffect(textFieldState, fileName) {
        snapshotFlow { textFieldState.text.toString() }.collect { viewModel.onEditorTextChanged(fileName, it) }
    }
    // Whatever became of the text - saved, discarded, or the song deleted - there is no draft once the editor is gone.
    DisposableEffect(fileName) { onDispose { viewModel.onEditorClosed() } }
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
) = LaunchedEffect(textFieldState, fileName) {
    viewModel.editorRevertRequests.collect {
        viewModel.songTexts.value[fileName]?.let(textFieldState::replaceAll)
    }
}

/** Replaces everything, for the rewrites that touch the whole document. */
private fun TextFieldState.replaceAll(text: String) = edit {
    val caret = selection.start.coerceAtMost(text.length)
    delete(0, length)
    insert(0, text)
    selection = TextRange(caret)
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
private const val SECTION_START = "{start_of_"
private const val PREVIEW_DELAY_MILLIS = 150L
