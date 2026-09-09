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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
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
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.ic_undo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_chord
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_comment
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_section
import com.pandulapeter.campfire.presentation.resources.song_editor_preview
import com.pandulapeter.campfire.presentation.resources.song_editor_redo
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
 * included, and it is saved across configuration changes and process death with the field's own saver. The view
 * model only ever receives finished text to write.
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
    // What was last handed over to be written, which is what "Saved" is measured against.
    val savedText = remember(destination.fileName) { mutableStateOf(initialText) }
    AutoSave(viewModel = viewModel, fileName = destination.fileName, textFieldState = textFieldState, savedText = savedText)

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
    val hasUnsavedChanges by remember(textFieldState, savedText) {
        derivedStateOf { textFieldState.text.toString() != savedText.value }
    }
    var isPreviewVisible by rememberSaveable { mutableStateOf(false) }
    val hasSideBySidePreview = windowSize == WindowSize.EXPANDED
    val fontScale = userPreferences?.fontScale ?: CampfireViewModel.DEFAULT_FONT_SCALE

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
                EditorMenu(
                    viewModel = viewModel,
                    fileName = destination.fileName,
                    textFieldState = textFieldState,
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
                onSaveRequested = { viewModel.saveSongContent(destination.fileName, textFieldState.text.toString()) },
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
            // The autosave writes a second after the typing stops; this is for the hand that reaches for it anyway.
            // It lives on the field rather than in the window's key handler, which has no way to reach this text.
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
    contentPadding: PaddingValues
) {
    var previewedText by remember(textFieldState) { mutableStateOf(textFieldState.text.toString()) }
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .debounce(PREVIEW_DELAY_MILLIS)
            .collect { previewedText = it }
    }
    val song = remember(previewedText, transposition) { viewModel.renderSong(previewedText, transposition) }
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
            scrollState = scrollState,
            topInset = topPadding
        )
    }
}

/** Everything that rewrites the text or acts on the song, behind one overflow button. */
@Composable
private fun EditorMenu(
    viewModel: CampfireViewModel,
    fileName: String,
    textFieldState: TextFieldState,
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
                textFieldState.replaceAll(viewModel.transposeText(textFieldState.text.toString(), 1))
            }
            EditorMenuItem(
                title = stringResource(Res.string.song_editor_transpose_text_down),
                icon = Res.drawable.ic_subtract
            ) {
                isExpanded = false
                textFieldState.replaceAll(viewModel.transposeText(textFieldState.text.toString(), -1))
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
    AnimatedContent(
        targetState = when {
            isSaving -> saving
            hasUnsavedChanges -> ""
            else -> saved
        },
        transitionSpec = { fadeIn() togetherWith fadeOut() }
    ) { label ->
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/**
 * Writes the text a second after the typing stops, and once more on the way out. A file the user can also open in a
 * text editor is not the place for a save button.
 */
@OptIn(FlowPreview::class)
@Composable
private fun AutoSave(
    viewModel: CampfireViewModel,
    fileName: String,
    textFieldState: TextFieldState,
    savedText: MutableState<String>
) {
    fun saveIfChanged() {
        val text = textFieldState.text.toString()
        if (text != savedText.value) {
            savedText.value = text
            viewModel.saveSongContent(fileName, text)
        }
    }

    LaunchedEffect(textFieldState, fileName) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .debounce(AUTOSAVE_DELAY_MILLIS)
            .collect { saveIfChanged() }
    }
    // The screen can go away between two keystrokes, and the last second of typing must not go with it. Both of
    // these write on the view model's scope, which outlives the screen, see CampfireViewModel.saveSongContent.
    val currentSave by rememberUpdatedState(::saveIfChanged)
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { currentSave() }
    DisposableEffect(fileName) { onDispose { currentSave() } }
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
private const val AUTOSAVE_DELAY_MILLIS = 1_000L
