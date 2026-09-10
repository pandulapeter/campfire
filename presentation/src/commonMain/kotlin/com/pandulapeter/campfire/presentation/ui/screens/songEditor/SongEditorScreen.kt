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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.back
import com.pandulapeter.campfire.presentation.resources.delete
import com.pandulapeter.campfire.presentation.resources.edit
import com.pandulapeter.campfire.presentation.resources.export
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_back
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.ic_export
import com.pandulapeter.campfire.presentation.resources.ic_more
import com.pandulapeter.campfire.presentation.resources.ic_redo
import com.pandulapeter.campfire.presentation.resources.ic_save
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.ic_undo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_chord
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_comment
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_section
import com.pandulapeter.campfire.presentation.resources.song_editor_preview
import com.pandulapeter.campfire.presentation.resources.song_editor_redo
import com.pandulapeter.campfire.presentation.resources.song_editor_save
import com.pandulapeter.campfire.presentation.resources.song_editor_saved
import com.pandulapeter.campfire.presentation.resources.song_editor_saving
import com.pandulapeter.campfire.presentation.resources.song_editor_section_bridge
import com.pandulapeter.campfire.presentation.resources.song_editor_section_chorus
import com.pandulapeter.campfire.presentation.resources.song_editor_section_grid
import com.pandulapeter.campfire.presentation.resources.song_editor_section_tab
import com.pandulapeter.campfire.presentation.resources.song_editor_section_verse
import com.pandulapeter.campfire.presentation.resources.song_editor_transpose_text_down
import com.pandulapeter.campfire.presentation.resources.song_editor_transpose_text_up
import com.pandulapeter.campfire.presentation.resources.song_editor_undo
import com.pandulapeter.campfire.presentation.resources.song_editor_unsaved
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.CampfireTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.components.WindowSize
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongLyrics
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
    onBack: () -> Unit
) {
    val songTexts by viewModel.songTexts.collectAsStateWithLifecycle()
    LaunchedEffect(destination.fileName) { viewModel.loadSongContent(destination.fileName) }
    AnimatedContent(
        modifier = modifier.fillMaxSize(),
        targetState = songTexts[destination.fileName],
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it != null }
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
                onBack = onBack
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingPane(contentPadding: PaddingValues) = Box(
    modifier = Modifier.fillMaxSize().padding(contentPadding),
    contentAlignment = Alignment.Center
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
    onBack: () -> Unit
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
            }
        )
    }
    ReportDraft(viewModel = viewModel, fileName = destination.fileName, textFieldState = textFieldState)

    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSavingSong.collectAsStateWithLifecycle()
    val filePicker = LocalFilePicker.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // Both follow the text as it is typed, so retitling a song shows in the bar above it right away. The title
    // comes from the parser rather than from a regex of this screen's own, so that it is the same title the song
    // list will show once the file is written - fallback to the file name included.
    val title by remember(textFieldState, destination.fileName) {
        derivedStateOf {
            ChordProParser.parseMetadata(textFieldState.text.toString()).title?.takeIf { it.isNotBlank() }
                ?: destination.fileName.removeSuffix(LibraryFiles.SONG_EXTENSION)
        }
    }
    val hasUnsavedChanges by viewModel.hasUnsavedEditorChanges.collectAsStateWithLifecycle()
    // The one way the file is ever written, reached from the app bar's button and from Ctrl / Cmd + S alike.
    val onSaveRequested = {
        if (hasUnsavedChanges) {
            viewModel.saveSongContent(destination.fileName, textFieldState.text.toString())
        }
        Unit
    }
    var isPreviewVisible by rememberSaveable { mutableStateOf(false) }
    val hasSideBySidePreview = windowSize == WindowSize.EXPANDED
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
                        painter = painterResource(Res.drawable.ic_back),
                        contentDescription = stringResource(Res.string.back)
                    )
                }
            },
            title = {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    SaveLabel(isSaving = isSaving, hasUnsavedChanges = hasUnsavedChanges)
                }
            },
            actions = {
                IconButton(
                    enabled = textFieldState.undoState.canUndo,
                    onClick = { textFieldState.undoState.undo() }
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_undo),
                        contentDescription = stringResource(Res.string.song_editor_undo)
                    )
                }
                IconButton(
                    enabled = textFieldState.undoState.canRedo,
                    onClick = { textFieldState.undoState.redo() }
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_redo),
                        contentDescription = stringResource(Res.string.song_editor_redo)
                    )
                }
                IconButton(
                    enabled = hasUnsavedChanges && !isSaving,
                    onClick = onSaveRequested
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_save),
                        contentDescription = stringResource(Res.string.song_editor_save)
                    )
                }
                EditorMenu(
                    viewModel = viewModel,
                    fileName = destination.fileName,
                    textFieldState = textFieldState,
                    accidentals = chordSpelling.accidentals,
                    onExport = { viewModel.exportSong(filePicker, destination.fileName) }
                )
            }
        )
        if (!hasSideBySidePreview) {
            SegmentedChoice(
                modifier = Modifier.padding(bottom = 8.dp),
                options = listOf(
                    false to stringResource(Res.string.edit),
                    true to stringResource(Res.string.song_editor_preview)
                ),
                selected = isPreviewVisible,
                onSelected = { isPreviewVisible = it }
            )
        }
        val layoutDirection = LocalLayoutDirection.current
        val editor: @Composable (Modifier) -> Unit = { paneModifier ->
            ChordProTextField(
                modifier = paneModifier,
                textFieldState = textFieldState,
                fontScale = fontScale,
                onSaveRequested = onSaveRequested,
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    // Next to the preview the divider is the end of this pane, not the window.
                    end = if (hasSideBySidePreview) 0.dp else contentPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding()
                )
            )
        }
        val preview: @Composable (Modifier) -> Unit = { paneModifier ->
            SongPreview(
                modifier = paneModifier,
                viewModel = viewModel,
                textFieldState = textFieldState,
                transposition = transpositions[destination.fileName, null],
                shouldShowChords = userPreferences?.isLyricsOnlyModeEnabled != true,
                fontScale = fontScale,
                isHorizontalFlow = userPreferences?.isHorizontalSectionFlowEnabled == true,
                chordSpelling = chordSpelling,
                contentPadding = PaddingValues(
                    start = if (hasSideBySidePreview) 0.dp else contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding()
                )
            )
        }
        if (hasSideBySidePreview) {
            Row(modifier = Modifier.fillMaxSize()) {
                editor(Modifier.weight(1f).fillMaxSize())
                VerticalDivider()
                preview(Modifier.weight(1f).fillMaxSize())
            }
        } else {
            AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                targetState = isPreviewVisible,
                transitionSpec = { fadeIn() togetherWith fadeOut() }
            ) { showPreview ->
                if (showPreview) preview(Modifier.fillMaxSize()) else editor(Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * The text itself. Monospaced, because a tab or a grid only lines up in one and because a ChordPro document is
 * source rather than prose.
 */
@Composable
private fun ChordProTextField(
    modifier: Modifier = Modifier,
    textFieldState: TextFieldState,
    fontScale: Float,
    onSaveRequested: () -> Unit,
    contentPadding: PaddingValues
) {
    val colorScheme = MaterialTheme.colorScheme
    val outputTransformation = remember(colorScheme) {
        ChordProOutputTransformation.of(
            primaryColor = colorScheme.primary,
            secondaryColor = colorScheme.onSurfaceVariant,
            outlineColor = colorScheme.outline
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
            .imePadding()
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 32.dp
            ),
        state = textFieldState,
        textStyle = bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = bodyLarge.fontSize * fontScale,
            lineHeight = bodyLarge.lineHeight * fontScale,
            color = colorScheme.onSurface
        ),
        // Autocorrect and automatic capitalization fight with a format whose words are "[Am]" and "{start_of_verse}".
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        lineLimits = TextFieldLineLimits.MultiLine(),
        outputTransformation = outputTransformation,
        cursorBrush = SolidColor(colorScheme.primary),
        // The field does its own scrolling when it is allowed more than one line; wrapping it in a scrollable
        // swallows the press that should have put the caret in it, and nothing can be typed at all.
        scrollState = rememberScrollState()
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
    shouldShowChords: Boolean,
    fontScale: Float,
    isHorizontalFlow: Boolean,
    chordSpelling: UserPreferences.ChordSpelling,
    contentPadding: PaddingValues
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
                    bottom = bottomPadding
                ),
            song = song,
            availableHeight = maxHeight - topPadding - bottomPadding,
            shouldShowChords = shouldShowChords,
            fontScale = fontScale,
            isHorizontalFlow = isHorizontalFlow,
            scrollState = scrollState
        )
    }
}

/**
 * Everything that rewrites the text or acts on the song, behind one overflow button.
 *
 * @param accidentals The spelling the transposition writes. Only the accidentals, and not the whole
 *   [UserPreferences.ChordSpelling]: this one rewrites the file, and a file is always written in the app's own
 *   notation, whatever the viewer prefers to read.
 */
@Composable
private fun EditorMenu(
    viewModel: CampfireViewModel,
    fileName: String,
    textFieldState: TextFieldState,
    accidentals: UserPreferences.Accidentals,
    onExport: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isSectionMenuExpanded by remember { mutableStateOf(false) }
    val sections = listOf(
        "verse" to stringResource(Res.string.song_editor_section_verse),
        "chorus" to stringResource(Res.string.song_editor_section_chorus),
        "bridge" to stringResource(Res.string.song_editor_section_bridge),
        "tab" to stringResource(Res.string.song_editor_section_tab),
        "grid" to stringResource(Res.string.song_editor_section_grid)
    )
    Box {
        IconButton(onClick = { isExpanded = true }) {
            Icon(
                painter = painterResource(Res.drawable.ic_more),
                contentDescription = stringResource(Res.string.songs_actions)
            )
        }
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }
        ) {
            EditorMenuItem(
                title = stringResource(Res.string.song_editor_insert_chord),
                icon = Res.drawable.ic_add
            ) {
                isExpanded = false
                textFieldState.wrapSelection(prefix = "[", suffix = "]")
            }
            EditorMenuItem(
                title = stringResource(Res.string.song_editor_insert_comment),
                icon = Res.drawable.ic_add
            ) {
                isExpanded = false
                textFieldState.wrapSelection(prefix = "{comment: ", suffix = "}")
            }
            EditorMenuItem(
                title = stringResource(Res.string.song_editor_insert_section),
                icon = Res.drawable.ic_add
            ) {
                isSectionMenuExpanded = true
            }
            EditorMenuItem(
                title = stringResource(Res.string.song_editor_transpose_text_up),
                icon = Res.drawable.ic_add
            ) {
                isExpanded = false
                textFieldState.replaceAll(viewModel.transposeText(textFieldState.text.toString(), 1, accidentals))
            }
            EditorMenuItem(
                title = stringResource(Res.string.song_editor_transpose_text_down),
                icon = Res.drawable.ic_subtract
            ) {
                isExpanded = false
                textFieldState.replaceAll(viewModel.transposeText(textFieldState.text.toString(), -1, accidentals))
            }
            EditorMenuItem(
                title = stringResource(Res.string.export),
                icon = Res.drawable.ic_export
            ) {
                isExpanded = false
                onExport()
            }
            EditorMenuItem(
                title = stringResource(Res.string.delete),
                icon = Res.drawable.ic_delete
            ) {
                isExpanded = false
                viewModel.allSongs.value.firstOrNull { it.fileName == fileName }
                    ?.let { viewModel.showDialog(CampfireViewModel.DialogType.DeleteSong(it)) }
            }
        }
        DropdownMenu(
            expanded = isSectionMenuExpanded,
            onDismissRequest = { isSectionMenuExpanded = false }
        ) {
            sections.forEach { (name, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        isSectionMenuExpanded = false
                        isExpanded = false
                        textFieldState.wrapSelection(
                            prefix = "{start_of_$name}\n",
                            suffix = "\n{end_of_$name}",
                            shouldStartOnItsOwnLine = true
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun EditorMenuItem(
    title: String,
    icon: org.jetbrains.compose.resources.DrawableResource,
    onClick: () -> Unit
) = DropdownMenuItem(
    text = { Text(title) },
    leadingIcon = { Icon(painter = painterResource(icon), contentDescription = null) },
    onClick = onClick
)

/** "Saved" only once the text on screen is the text on disk, "Saving…" only while it is actually being written. */
@Composable
private fun SaveLabel(
    isSaving: Boolean,
    hasUnsavedChanges: Boolean
) {
    val saving = stringResource(Res.string.song_editor_saving)
    val saved = stringResource(Res.string.song_editor_saved)
    // Its own short label rather than the dialog's "Unsaved changes": there is very little room next to a song title.
    val unsaved = stringResource(Res.string.song_editor_unsaved)
    AnimatedContent(
        targetState = when {
            isSaving -> saving
            hasUnsavedChanges -> unsaved
            else -> saved
        },
        transitionSpec = { fadeIn() togetherWith fadeOut() }
    ) { label ->
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
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
    textFieldState: TextFieldState
) {
    LaunchedEffect(textFieldState, fileName) {
        snapshotFlow { textFieldState.text.toString() }.collect { viewModel.onEditorTextChanged(fileName, it) }
    }
    // Whatever became of the text - saved, discarded, or the song deleted - there is no draft once the editor is gone.
    DisposableEffect(fileName) { onDispose { viewModel.onEditorClosed() } }
}

/**
 * Puts [prefix] and [suffix] around the selection, or around the caret when there is none.
 *
 * @param shouldStartOnItsOwnLine For the block directives, which are only directives when they are alone on a line.
 */
private fun TextFieldState.wrapSelection(
    prefix: String,
    suffix: String,
    shouldStartOnItsOwnLine: Boolean = false
) = edit {
    val start = minOf(selection.start, selection.end)
    val end = maxOf(selection.start, selection.end)
    val selected = originalText.substring(start, end)
    val opening = if (shouldStartOnItsOwnLine && start > 0 && originalText[start - 1] != '\n') "\n$prefix" else prefix
    val closing = if (shouldStartOnItsOwnLine && end < originalText.length && originalText[end] != '\n') "$suffix\n" else suffix
    delete(start, end)
    insert(start, opening + selected + closing)
    // With nothing selected the caret lands between the two halves, which is where the next thing typed belongs.
    selection = if (selected.isEmpty()) {
        TextRange(start + opening.length)
    } else {
        TextRange(start + opening.length, start + opening.length + selected.length)
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

private const val SECTION_START = "{start_of_"
private const val PREVIEW_DELAY_MILLIS = 150L
