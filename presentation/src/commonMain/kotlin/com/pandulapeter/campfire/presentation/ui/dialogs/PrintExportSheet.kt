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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.pandulapeter.campfire.presentation.ui.platform.isDesktopPlatform
import com.pandulapeter.campfire.presentation.ui.platform.isLaunchScreenWholeStartup
import com.pandulapeter.campfire.presentation.ui.platform.verticalWheelNotches
import kotlinx.coroutines.launch
import kotlin.math.pow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.PrintSettings
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.*
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.DelayedLoadingIndicator
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.components.SettingsSectionTitle
import com.pandulapeter.campfire.presentation.ui.components.fadingVerticalEdges
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.print.*
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.Stepper
import com.pandulapeter.campfire.presentation.ui.theme.LocalMonospaceFontFamily
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun PrintExportSheet(viewModel: CampfireViewModel, dialog: CampfireViewModel.DialogType.PrintExport) {
    val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val initialSettings = preferences?.printSettings ?: return
    var settings by remember(dialog) { mutableStateOf(viewModel.pendingPrintSettings.value ?: initialSettings) }
    var source by remember(dialog) { mutableStateOf<PrintSource?>(null) }
    var failed by remember(dialog) { mutableStateOf(false) }
    var attempt by remember(dialog) { mutableIntStateOf(0) }
    // Saved, or a rotation would put back every song somebody had unticked; null until the source has been read.
    var selected by rememberSaveable(dialog, stateSaver = SELECTION_SAVER) { mutableStateOf<Set<Int>?>(null) }
    val exportProgress by viewModel.pdfExportProgress.collectAsStateWithLifecycle()
    val isFileTransferActive by viewModel.isFileTransferActive.collectAsStateWithLifecycle()
    var page by rememberSaveable(dialog) { mutableIntStateOf(0) }
    val filePicker = LocalFilePicker.current
    val fontResolver = LocalFontFamilyResolver.current
    val fontFamily = LocalMonospaceFontFamily.current
    // The face the viewer sets lyrics in, which on the web is the preloaded Inter rather than FontFamily.Default.
    val textFontFamily = MaterialTheme.typography.bodyLarge.fontFamily ?: FontFamily.Default
    fun newRenderer() = PrintRenderer(TextMeasurer(fontResolver, Density(1f), LayoutDirection.Ltr, cacheSize = 256), fontFamily, textFontFamily)
    val renderer = remember(fontResolver, fontFamily, textFontFamily) { newRenderer() }
    val labels = PrintLabels(stringResource(Res.string.print_key), stringResource(Res.string.print_capo),
        stringResource(Res.string.print_tempo), stringResource(Res.string.print_time), stringResource(Res.string.print_missing),
        stringResource(Res.string.print_chorus), stringResource(Res.string.print_bridge))
    LaunchedEffect(dialog, attempt) {
        failed = false
        try {
            source = viewModel.preparePrintSource(dialog)
            if (selected == null) selected = source!!.songs.indices.toSet()
        } catch (exception: CancellationException) { throw exception
        } catch (exception: Exception) { failed = true }
    }
    val chosenSource = remember(source, selected) { source?.let { it.copy(songs = it.songs.filterIndexed { index, _ -> index in selected.orEmpty() }) } }
    var layoutFailed by remember { mutableStateOf(false) }
    // The last document stays on screen while the next one is laid out, so that a step of an option fades from one
    // page to the next instead of blanking the preview to a spinner each time.
    // Retry is keyed too: the source it reads again equals the one that failed to lay out, so without it nothing would.
    val laidOut by produceState<LaidOutDocument?>(null, chosenSource, settings, labels, renderer, attempt) {
        layoutFailed = false
        val input = chosenSource
        val inputSettings = settings
        if (input == null || input.songs.isEmpty()) {
            value = null
            return@produceState
        }
        // A burst of steps is one layout, but the first one, with nothing on screen yet, starts at once.
        if (value != null) delay(LAYOUT_DEBOUNCE)
        try {
            val document = withContext(Dispatchers.Default) {
                val measurements = newRenderer()
                layoutPrintDocument(input, settings, labels, measurements::width)
            }
            value = LaidOutDocument(document, input, inputSettings, labels, generation = (value?.generation ?: 0) + 1)
        } catch (exception: CancellationException) { throw exception
        } catch (exception: Exception) { layoutFailed = true }
    }
    // Only a document laid out from what the sheet shows now may be saved: the producer restarts a frame after an option
    // changes, and a Save in that frame would otherwise export the old options. The source is compared by identity,
    // since it is remembered and an equality check would walk every song in it.
    val isCurrent = laidOut.let { it != null && it.source === chosenSource && it.settings == settings && it.labels == labels }
    val update: (PrintSettings) -> Unit = {
        val normalized = it.normalized()
        if (normalized != settings) { settings = normalized; viewModel.setPrintSettings(normalized) }
    }
    CampfireBottomSheet(
        title = stringResource(Res.string.print_export),
        subtitle = dialog.setlist?.title ?: dialog.song?.title.orEmpty(),
        sheetMaxWidth = 1100.dp,
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        val sheetUncoveredTopInset = uncoveredTopInset
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            // The height of the sheet at its tallest: the one its content is offered changes while it slides up and is
            // dragged, and a layout chosen by that would switch, and resize the preview, during the opening animation.
            val stableHeight = maxHeight - sheetUncoveredTopInset()
            val state = when {
                failed || layoutFailed -> PrintSheetContent.FAILED
                source == null -> PrintSheetContent.LOADING
                else -> PrintSheetContent.LOADED
            }
            AnimatedContent(state, Modifier.fillMaxSize(), transitionSpec = { fadeIn() togetherWith fadeOut() }) { shown ->
                when (shown) {
                    PrintSheetContent.FAILED -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(Res.string.print_load_failed))
                            TextButton(onClick = { attempt++ }) { Text(stringResource(Res.string.retry)) }
                        }
                    }
                    PrintSheetContent.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { DelayedLoadingIndicator() }
                    PrintSheetContent.LOADED -> if (source != null) {
                        val options: @Composable (Modifier) -> Unit = { modifier ->
                            PrintOptions(modifier, source!!, settings, selected.orEmpty(), onSelected = { selected = it }, onSettings = update)
                        }
                        val preview: @Composable (Modifier) -> Unit = { modifier ->
                            val emptyMessage = when {
                                source!!.songs.isEmpty() -> stringResource(Res.string.print_setlist_empty)
                                selected.orEmpty().isEmpty() -> stringResource(Res.string.print_no_songs)
                                else -> null
                            }
                            PrintPreview(modifier, laidOut, isCurrent, renderer, emptyMessage, page = page, onPageSettled = { page = it })
                        }
                        when {
                            maxWidth >= 760.dp && stableHeight >= 480.dp -> Row(Modifier.fillMaxSize()) {
                                options(Modifier.width(330.dp).fillMaxHeight())
                                preview(Modifier.weight(1f).fillMaxHeight())
                            }
                            // A phone on its side: too short to stack the two, wide enough to put them side by side.
                            maxWidth >= 600.dp -> Row(Modifier.fillMaxSize()) {
                                options(Modifier.width(300.dp).fillMaxHeight())
                                preview(Modifier.weight(1f).fillMaxHeight())
                            }
                            else -> Column(Modifier.fillMaxSize()) {
                                preview(Modifier.fillMaxWidth().height(stableHeight * PHONE_PREVIEW_HEIGHT_FRACTION))
                                Spacer(Modifier.height(Dp.Hairline))
                                options(Modifier.fillMaxWidth().weight(1f))
                            }
                        }
                    }
                }
            }
        }
        // Here rather than outside the sheet, since closing it the way its close button does is only possible from here.
        LaunchedEffect(dialog) { viewModel.printExportSaved.collect { if (it == dialog) close() } }
        PrintActions(
            contentPadding = contentPadding,
            progress = exportProgress,
            // Any import or export running would make the view model ignore the tap, so the button does not take it.
            canSave = isCurrent && laidOut?.document?.pages?.isNotEmpty() == true && !isFileTransferActive,
            canShare = filePicker.canShare,
            onExport = { isShare ->
                laidOut?.document?.let { snapshot ->
                    // From the sheet's own settings, which the saved preferences may not have caught up with yet.
                    viewModel.exportPdf(filePicker, pdfFileName(source!!, settings), dialog, snapshot.pages.size, isShare) { onPage ->
                        newRenderer().pdf(snapshot, source!!.title, onPage = onPage)
                    }
                }
            },
            onCancel = viewModel::cancelPdfExport,
        )
    }
}

/**
 * Below the scrolling area rather than in the header, and given the bottom inset itself, as the cover search's are: the
 * list above it has no bar to scroll under, so the inset is the row's alone. While the pages are drawn the progress
 * shows above the button and the button cancels; once the picker is up there is nothing to cancel, and Save stays
 * disabled by [canSave] until it has answered.
 */
@Composable
private fun PrintActions(
    contentPadding: PaddingValues,
    progress: CampfireViewModel.PdfExportProgress?,
    canSave: Boolean,
    canShare: Boolean,
    onExport: (isShare: Boolean) -> Unit,
    onCancel: () -> Unit,
) = Column(
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).padding(contentPadding),
) {
    // Keyed by whether there is any, so that the bar fades out showing the last count rather than an empty one.
    AnimatedContent(progress, transitionSpec = { fadeIn() togetherWith fadeOut() }, contentKey = { it != null }) { shown ->
        if (shown != null) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text(
                    text = stringResource(Res.string.print_page, (shown.done + 1).coerceAtMost(shown.total), shown.total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { if (shown.total == 0) 0f else shown.done.toFloat() / shown.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        // One Cancel stands for both while the pages are drawn, whichever of the two started it.
        AnimatedVisibility(canShare && progress == null, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
            TextButton(enabled = canSave, onClick = { onExport(true) }, modifier = Modifier.padding(end = 8.dp)) {
                Icon(painterResource(Res.drawable.ic_share), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(Res.string.print_share))
            }
        }
        Button(enabled = progress != null || canSave, onClick = if (progress != null) onCancel else ({ onExport(false) })) {
            CrossfadedLabel(stringResource(Res.string.print_save), stringResource(Res.string.cancel), isSecondShown = progress != null)
        }
    }
}

/** What the sheet's area shows, which it fades between; a new document is not one of these, see [PrintPreview]. */
private enum class PrintSheetContent { FAILED, LOADING, LOADED }

/**
 * A laid out document together with what it was laid out from, which is what tells a document that stands in for the
 * next one while that is laid out from one that may be exported. [generation] tells one document from the next without
 * comparing them, which would walk every text on every page.
 */
private class LaidOutDocument(val document: PrintDocument, val source: PrintSource, val settings: PrintSettings, val labels: PrintLabels, val generation: Int)

/** Two labels of one button, faded between, the button keeping the size of the wider so that it does not jump. */
@Composable
private fun CrossfadedLabel(first: String, second: String, isSecondShown: Boolean) = Box(contentAlignment = Alignment.Center) {
    val secondAlpha by animateFloatAsState(if (isSecondShown) 1f else 0f)
    Text(first, Modifier.alpha(1f - secondAlpha).semantics { if (isSecondShown) invisibleToUser() })
    Text(second, Modifier.alpha(secondAlpha).semantics { if (!isSecondShown) invisibleToUser() })
}

@Composable
private fun PrintOptions(modifier: Modifier, source: PrintSource, settings: PrintSettings, selected: Set<Int>, onSelected: (Set<Int>) -> Unit, onSettings: (PrintSettings) -> Unit) {
    val state = rememberLazyListState()
    LazyColumn(modifier.fadingVerticalEdges(state), state = state, contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            SettingsSectionTitle(text = stringResource(Res.string.print_paper))
            SegmentedChoice(
                options = PrintSettings.Paper.entries.map { paper ->
                    paper to stringResource(if (paper == PrintSettings.Paper.A4) Res.string.print_a4 else Res.string.print_letter)
                },
                selected = settings.paper,
                onSelected = { onSettings(settings.copy(paper = it)) },
            )
            Spacer(Modifier.height(8.dp))
            SegmentedChoice(
                options = listOf(false to stringResource(Res.string.print_portrait), true to stringResource(Res.string.print_landscape)),
                selected = settings.isLandscape,
                onSelected = { onSettings(settings.copy(isLandscape = it)) },
            )
            Spacer(Modifier.height(8.dp))
            PrintStepperRow(stringResource(Res.string.song_details_text_size)) {
                Stepper(
                    value = stringResource(Res.string.print_font_size, settings.fontSize),
                    isDefault = true,
                    decreaseIcon = painterResource(Res.drawable.ic_text_decrease),
                    decreaseLabel = stringResource(Res.string.song_details_text_size_decrease),
                    canDecrease = settings.fontSize > MIN_FONT_SIZE,
                    onDecrease = { onSettings(settings.copy(fontSize = settings.fontSize - 1)) },
                    increaseIcon = painterResource(Res.drawable.ic_text_increase),
                    increaseLabel = stringResource(Res.string.song_details_text_size_increase),
                    canIncrease = settings.fontSize < MAX_FONT_SIZE,
                    onIncrease = { onSettings(settings.copy(fontSize = settings.fontSize + 1)) },
                    resetLabel = null,
                    onReset = null,
                )
            }
            PrintStepperRow(stringResource(Res.string.print_margins)) {
                Stepper(
                    value = stringResource(Res.string.print_margin, settings.marginMm),
                    isDefault = true,
                    decreaseIcon = painterResource(Res.drawable.ic_subtract),
                    decreaseLabel = stringResource(Res.string.print_margin_decrease),
                    canDecrease = settings.marginMm > MIN_MARGIN_MM,
                    // To the next multiple of the step either way, so that a stored value off the steps joins them
                    // rather than keeping its offset (12 goes to 10 or 15, never to 7 or 17); normalized() clamps it.
                    onDecrease = { onSettings(settings.copy(marginMm = (settings.marginMm - 1) / MARGIN_STEP_MM * MARGIN_STEP_MM)) },
                    increaseIcon = painterResource(Res.drawable.ic_add),
                    increaseLabel = stringResource(Res.string.print_margin_increase),
                    canIncrease = settings.marginMm < MAX_MARGIN_MM,
                    onIncrease = { onSettings(settings.copy(marginMm = (settings.marginMm / MARGIN_STEP_MM + 1) * MARGIN_STEP_MM)) },
                    resetLabel = null,
                    onReset = null,
                )
            }
            SettingsSectionTitle(text = stringResource(Res.string.print_columns))
            SegmentedChoice(
                options = (1..2).map { it to it.toString() },
                selected = settings.columns,
                onSelected = { onSettings(settings.copy(columns = it)) },
            )
            Spacer(Modifier.height(8.dp))
            CheckboxListItem(title = stringResource(Res.string.print_chords), isChecked = settings.showChords, onCheckedChange = { onSettings(settings.copy(showChords = it)) })
            CheckboxListItem(title = stringResource(Res.string.print_comments), isChecked = settings.showComments, onCheckedChange = { onSettings(settings.copy(showComments = it)) })
            CheckboxListItem(title = stringResource(Res.string.print_metadata), isChecked = settings.showMetadata, onCheckedChange = { onSettings(settings.copy(showMetadata = it)) })
            CheckboxListItem(title = stringResource(Res.string.print_page_numbers), isChecked = settings.showPageNumbers, onCheckedChange = { onSettings(settings.copy(showPageNumbers = it)) })
            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(Res.string.print_key_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (source.isSetlist) {
            item {
                SettingsSectionTitle(text = stringResource(Res.string.print_setlist_content))
                SegmentedChoice(
                    options = PrintSettings.SetlistMode.entries.map { mode ->
                        mode to stringResource(if (mode == PrintSettings.SetlistMode.SONG_SHEETS) Res.string.print_song_sheets else Res.string.print_running_order)
                    },
                    selected = settings.setlistMode,
                    onSelected = { onSettings(settings.copy(setlistMode = it)) },
                )
                if (settings.setlistMode == PrintSettings.SetlistMode.SONG_SHEETS) {
                    Spacer(Modifier.height(8.dp))
                    CheckboxListItem(title = stringResource(Res.string.print_overview), isChecked = settings.includeSetlistOverview, onCheckedChange = { onSettings(settings.copy(includeSetlistOverview = it)) })
                    CheckboxListItem(title = stringResource(Res.string.print_new_page), isChecked = settings.startSongsOnNewPage, onCheckedChange = { onSettings(settings.copy(startSongsOnNewPage = it)) })
                }
                if (source.songs.isNotEmpty()) {
                    SettingsSectionTitle(text = stringResource(Res.string.print_songs))
                    Row(Modifier.padding(horizontal = 4.dp)) {
                        TextButton(onClick = { onSelected(source.songs.indices.toSet()) }) { Text(stringResource(Res.string.print_select_all)) }
                        TextButton(onClick = { onSelected(emptySet()) }) { Text(stringResource(Res.string.print_select_none)) }
                    }
                }
            }
            itemsIndexed(source.songs) { index, entry ->
                CheckboxListItem(title = "${entry.index}. ${entry.title}", description = if (entry.song == null) stringResource(Res.string.print_missing) else entry.artist,
                    isChecked = index in selected, onCheckedChange = { onSelected(if (it) selected + index else selected - index) })
            }
        } else if (source.songs.any { it.song == null }) {
            item { Text(stringResource(Res.string.print_missing), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** A label and a stepper on one line, built like the overflow menus' `MenuStepperRow`, at the sheet's own keyline. */
@Composable
private fun PrintStepperRow(label: String, stepper: @Composable () -> Unit) = Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(label, Modifier.weight(1f).padding(end = 16.dp), style = MaterialTheme.typography.bodyLarge)
    stepper()
}

/** What the preview shows, which it fades between, keyed by its kind so that a new document does not count as a change. */
private sealed interface PreviewContent {
    data class Empty(val message: String) : PreviewContent
    data object Loading : PreviewContent
    data class Pages(val laidOut: LaidOutDocument) : PreviewContent
}

/**
 * @param emptyMessage What to say instead of the pages where there are none to show: a setlist with no songs, or none
 *   of them chosen.
 * @param page The page asked for, which the sheet keeps rather than the pager: after a rotation the layout starts over and
 *   there are no pages for a while, and a pager state restored on its own would clamp the saved page to the first one.
 */
@Composable
private fun PrintPreview(
    modifier: Modifier,
    laidOut: LaidOutDocument?,
    isCurrent: Boolean,
    renderer: PrintRenderer,
    emptyMessage: String?,
    page: Int,
    onPageSettled: (Int) -> Unit,
) {
    val content = when {
        emptyMessage != null -> PreviewContent.Empty(emptyMessage)
        laidOut == null || laidOut.document.pages.isEmpty() -> PreviewContent.Loading
        else -> PreviewContent.Pages(laidOut)
    }
    AnimatedContent(content, modifier.padding(16.dp), transitionSpec = { fadeIn() togetherWith fadeOut() }, contentKey = { it::class }) { shown ->
        when (shown) {
            is PreviewContent.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(shown.message) }
            PreviewContent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { DelayedLoadingIndicator() }
            is PreviewContent.Pages -> PrintPages(shown.laidOut, isCurrent, renderer, page, onPageSettled)
        }
    }
}

/**
 * The pages side by side in a pager, turned by a swipe, by the buttons under them or, once the pane has the focus, by
 * the arrow, Page Up / Page Down, Home and End keys, and zoomed by a pinch, a double tap, the zoom button or, on the
 * desktop, Ctrl / Cmd and the scroll wheel. A zoomed page is drawn again at its new size rather than scaled up, so it
 * stays sharp, and the pager does not take a swipe while it is zoomed, since the swipe is the pan.
 */
@Composable
private fun PrintPages(laidOut: LaidOutDocument, isCurrent: Boolean, renderer: PrintRenderer, page: Int, onPageSettled: (Int) -> Unit) {
    val pageCount = laidOut.document.pages.size
    val pagerState = rememberPagerState(initialPage = page.coerceIn(0, pageCount - 1)) { pageCount }
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    // A new document keeps the page that was open, clamped to the pages it has.
    LaunchedEffect(pageCount) { if (pageCount > 0) pagerState.scrollToPage(page.coerceIn(0, pageCount - 1)) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (pagerState.pageCount > 0) onPageSettled(settled)
            zoom = 1f
            pan = Offset.Zero
        }
    }
    // Only where a keyboard is the way the app is driven: on a touch screen it would bring the keyboard's focus ring up.
    LaunchedEffect(Unit) { if (isDesktopPlatform) focusRequester.requestFocus() }
    fun zoomTo(target: Float, pivot: Offset = Offset(pageSize.width / 2f, pageSize.height / 2f)) {
        val clampedZoom = target.coerceIn(1f, MAX_ZOOM)
        // The point of the page under the pivot stays under it.
        pan = clampPan(pivot - (pivot - pan) * (clampedZoom / zoom), clampedZoom, pageSize)
        zoom = clampedZoom
    }
    fun turnTo(target: Int) {
        coroutineScope.launch { pagerState.animateScrollToPage(target.coerceIn(0, pageCount - 1)) }
    }
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        zoomTo(zoom * zoomChange)
        pan = clampPan(pan + panChange, zoom, pageSize)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                // Alt + Left is the browser's Back, which on the web closes this sheet, and Cmd + Left and Right are Back
                // and Forward on a Mac, so a press with any of those down is left to whoever sent it.
                if (event.type != KeyEventType.KeyDown || event.isAltPressed || event.isCtrlPressed || event.isMetaPressed) return@onKeyEvent false
                val target = when (event.key) {
                    Key.DirectionLeft, Key.PageUp -> pagerState.currentPage - 1
                    Key.DirectionRight, Key.PageDown -> pagerState.currentPage + 1
                    Key.MoveHome -> 0
                    Key.MoveEnd -> pageCount - 1
                    else -> return@onKeyEvent false
                }
                turnTo(target)
                true
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp,
                userScrollEnabled = zoom == 1f,
            ) { index ->
                val isShownPage = index == pagerState.currentPage
                val pageLabel = stringResource(Res.string.print_page, index + 1, pageCount)
                // Keyed by the generation, not by the document, whose equality would compare every text on every page.
                AnimatedContent(laidOut, Modifier.fillMaxSize(), transitionSpec = { fadeIn() togetherWith fadeOut() }, contentKey = { it.generation }) { faded ->
                    val document = faded.document
                    val shownPage = document.pages.getOrNull(index) ?: return@AnimatedContent
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val pageWidth = minOf(maxWidth, maxHeight * (document.width / document.height))
                        PrintPageCanvas(
                            modifier = Modifier
                                .width(pageWidth)
                                .aspectRatio(document.width / document.height)
                                .then(
                                    if (isShownPage) {
                                        Modifier
                                            .onSizeChanged { pageSize = it }
                                            .transformable(transformableState, canPan = { zoom > 1f })
                                            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoomTo(if (zoom > 1f) 1f else DOUBLE_TAP_ZOOM, it) }) }
                                            .wheelZoom { notches, position -> zoomTo(zoom * WHEEL_ZOOM_BASE.pow(-notches), position) }
                                    } else {
                                        Modifier
                                    },
                                ),
                            document = document,
                            page = shownPage,
                            renderer = renderer,
                            zoom = if (isShownPage) zoom else 1f,
                            pan = if (isShownPage) pan else Offset.Zero,
                            description = pageLabel + "\n" + shownPage.texts.joinToString("\n") { it.text },
                        )
                    }
                }
            }
            LayoutIndicator(isVisible = !isCurrent)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(enabled = pagerState.currentPage > 0, onClick = { turnTo(pagerState.currentPage - 1) }) {
                Icon(painterResource(Res.drawable.ic_previous), contentDescription = stringResource(Res.string.print_previous))
            }
            Text(stringResource(Res.string.print_page, pagerState.currentPage + 1, pageCount), style = MaterialTheme.typography.bodySmall)
            IconButton(enabled = pagerState.currentPage + 1 < pageCount, onClick = { turnTo(pagerState.currentPage + 1) }) {
                Icon(painterResource(Res.drawable.ic_next), contentDescription = stringResource(Res.string.print_next))
            }
            IconButton(onClick = { zoomTo(if (zoom > 1f) 1f else DOUBLE_TAP_ZOOM) }) {
                Icon(
                    painter = painterResource(if (zoom > 1f) Res.drawable.ic_subtract else Res.drawable.ic_add),
                    contentDescription = stringResource(if (zoom > 1f) Res.string.print_zoom_out else Res.string.print_zoom_in),
                )
            }
        }
    }
}

/** One page, white under a hairline border, drawn at [zoom] times the size that fits and moved by [pan] inside its bounds. */
@Composable
private fun PrintPageCanvas(
    modifier: Modifier,
    document: PrintDocument,
    page: PrintPage,
    renderer: PrintRenderer,
    zoom: Float,
    pan: Offset,
    description: String,
) = Canvas(
    modifier
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
        .clipToBounds()
        .semantics { contentDescription = description },
) {
    drawRect(Color.White)
    translate(pan.x, pan.y) { renderer.draw(this, page, size.width * zoom / document.width) }
}

/** Keeps a page zoomed by [zoom] covering its whole box, so that no pan shows anything beyond its edges. */
private fun clampPan(pan: Offset, zoom: Float, size: IntSize) = Offset(
    x = pan.x.coerceIn(-(zoom - 1f) * size.width, 0f),
    y = pan.y.coerceIn(-(zoom - 1f) * size.height, 0f),
)

/**
 * Ctrl or Cmd and the scroll wheel, in the desktop application only: in a browser that chord is the page's own zoom,
 * which the app leaves alone. [isLaunchScreenWholeStartup] is the one platform flag that is true there and nowhere else.
 */
private fun Modifier.wheelZoom(onZoom: (notches: Float, position: Offset) -> Unit) = if (!isLaunchScreenWholeStartup) this else pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Scroll && (event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed)) {
                onZoom(event.verticalWheelNotches(), event.changes.first().position)
                event.changes.forEach { it.consume() }
            }
        }
    }
}

/** Shown over a page that stands in for the next one, once the layout of that has taken a moment (it fades in on its own). */
@Composable
private fun LayoutIndicator(isVisible: Boolean) = AnimatedVisibility(isVisible, enter = EnterTransition.None, exit = fadeOut()) {
    DelayedLoadingIndicator()
}

/** The selected songs by their place in the source, with null (nothing read yet) saved as nothing at all. */
private val SELECTION_SAVER = Saver<Set<Int>?, List<Int>>(save = { it?.toList() }, restore = { it.toSet() })

private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val WHEEL_ZOOM_BASE = 1.15f // The zoom of one notch of the scroll wheel, towards the user zooming out.
private const val PHONE_PREVIEW_HEIGHT_FRACTION = 0.42f
private val LAYOUT_DEBOUNCE = 120.milliseconds
private const val MIN_FONT_SIZE = 8
private const val MAX_FONT_SIZE = 20
private const val MIN_MARGIN_MM = 10
private const val MAX_MARGIN_MM = 25
private const val MARGIN_STEP_MM = 5
