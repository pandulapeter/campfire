/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.print_load_failed
import com.pandulapeter.campfire.presentation.resources.print_share
import com.pandulapeter.campfire.presentation.resources.retry
import com.pandulapeter.campfire.presentation.resources.share_send
import com.pandulapeter.campfire.presentation.resources.songs_selection_count
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.DelayedLoadingIndicator
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.saveShortcut
import com.pandulapeter.campfire.presentation.ui.chrome.contentEdges
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.songLabel
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.calendarLocale
import com.pandulapeter.campfire.presentation.ui.print.PrintSource
import com.pandulapeter.campfire.presentation.ui.print.PrintRenderer
import com.pandulapeter.campfire.presentation.ui.print.pdfFileName
import com.pandulapeter.campfire.presentation.ui.print.withinFeatures
import com.pandulapeter.campfire.presentation.ui.share.StopSharingConfirmation
import com.pandulapeter.campfire.presentation.ui.tabs.rememberTabNoticeLabels
import com.pandulapeter.campfire.presentation.ui.theme.LocalMonospaceFontFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/**
 * The Share screen of a song or a setlist: the options on one side, the preview of what they make on the other, and Save
 * as its floating action button (Share, where the platform has it, in the app bar). The first option is the format: a
 * PDF, whose preview and file are drawn from one layout by one [PrintRenderer], so what is saved is the preview at print
 * resolution, or the library's own files - a song's ChordPro file, or a setlist's zip of its manifest and its songs as
 * ChordPro files - which take no option but the songs a setlist's zip holds, and are previewed as what is written. That
 * one choice is what the menus offer as their single export entry, rather than one entry for every format and every way
 * out of the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportScreen(
    viewModel: CampfireViewModel,
    dialog: DialogType.Export,
) {
    val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val initialSettings = preferences?.printSettings ?: return
    val formats = exportFormatsOf(dialog, isSyncAvailable = viewModel.syncController.syncProviders.isNotEmpty())
    val state = rememberExportState(dialog) { pickedFormat ->
        openingSettingsOf(
            stored = viewModel.exportController.pendingPrintSettings.value ?: initialSettings,
            dialog = dialog,
            pickedFormat = pickedFormat,
            formats = formats,
        )
    }
    val exportProgress by viewModel.exportController.pdfExportProgress.collectAsStateWithLifecycle()
    val isFileTransferActive by viewModel.exportController.isFileTransferActive.collectAsStateWithLifecycle()
    val filePicker = LocalFilePicker.current
    val fontResolver = LocalFontFamilyResolver.current
    val fontFamily = LocalMonospaceFontFamily.current
    // The face the viewer sets lyrics in, Inter rather than FontFamily.Default.
    val textFontFamily = MaterialTheme.typography.bodyLarge.fontFamily ?: FontFamily.Default
    fun newRenderer() = PrintRenderer(
        measurer = TextMeasurer(fontResolver, Density(1f), LayoutDirection.Ltr, cacheSize = 256),
        monospaceFontFamily = fontFamily,
        textFontFamily = textFontFamily,
    )
    val renderer = remember(fontResolver, fontFamily, textFontFamily) { newRenderer() }
    val tabNoticeLabels = rememberTabNoticeLabels()
    val labels = rememberPrintLabels(preferences)
    LaunchedEffect(dialog, state.attempt) {
        state.failed = false
        try {
            state.source = viewModel.exportController.preparePrintSource(dialog, tabNoticeLabels)
            if (state.selected == null) state.selected = state.source!!.songs.indices.toSet()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            state.failed = true
        }
    }
    // The setlist's day as the setlist dialog shows it, in the app's language. The formatter counts in UTC midnights, so
    // the day goes in as one: any other zone would print the day before or after it on one side of UTC.
    val languageCode = currentLanguage.value.code
    val locale = remember(languageCode) { calendarLocale(languageCode) }
    val dateFormatter = remember { DatePickerDefaults.dateFormatter() }
    val date = dialog.setlist?.date?.let { day ->
        dateFormatter.formatDate(day.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), locale) ?: day.toString()
    }
    val songsCount = pluralStringResource(Res.plurals.songs_selection_count, dialog.songs.size, dialog.songs.size)
    // Several songs have no title of their own, so the file is titled with how many of them it holds.
    val chosenCount = state.selected.orEmpty().size
    val chosenSongsTitle = pluralStringResource(Res.plurals.songs_selection_count, chosenCount, chosenCount)
    val chosenSource = remember(state.source, state.selected, date, chosenSongsTitle) {
        state.source?.let { source ->
            source.copy(
                title = if (source.kind == PrintSource.Kind.SONGS) chosenSongsTitle else source.title,
                date = date,
                songs = source.songs.filterIndexed { index, _ -> index in state.selected.orEmpty() },
            )
        }
    }
    // What the Features tab switched off is left out of the file whatever the options say, the options themselves being
    // kept for the switch to be turned back on.
    val areChordsEnabled = preferences?.areChordsEnabled != false
    val isMetronomeEnabled = preferences?.isMetronomeEnabled != false
    val printedSettings = state.settings.withinFeatures(areChordsEnabled = areChordsEnabled, isMetronomeEnabled = isMetronomeEnabled)
    val laidOut by produceLaidOutDocument(state = state, source = chosenSource, settings = printedSettings, labels = labels, renderer = renderer, newRenderer = ::newRenderer)
    // Only a document laid out from what the screen shows now may be saved: the producer restarts a frame after an option
    // changes, and a Save in that frame would otherwise export the old options. The source is compared by identity,
    // since it is remembered and an equality check would walk every song in it.
    val isCurrent = laidOut.let { it != null && it.source === chosenSource && it.settings == printedSettings && it.labels == labels }
    // The screen stays composed while it slides away, after its settings were flushed and its drawing cancelled, so
    // nothing tapped in that moment may change an option or start an export. Equality, since an equal export opened
    // again during the slide is this one.
    val visibleDialog by viewModel.dialogHost.visibleDialog.collectAsStateWithLifecycle()
    val isOpen = visibleDialog == dialog
    val update: (PrintSettings) -> Unit = update@{
        if (!isOpen) return@update
        val normalized = it.normalized()
        if (normalized != state.settings) {
            if (normalized.format != state.settings.format) state.pickedFormat = normalized.format
            state.settings = normalized
            viewModel.exportController.setPrintSettings(normalized)
        }
    }
    val isFiles = state.settings.format == PrintSettings.Format.FILES
    val isLink = state.settings.format == PrintSettings.Format.LINK
    val isPdf = state.settings.format == PrintSettings.Format.PDF
    val link = shareTargetOf(dialog)?.let { target ->
        rememberLinkExport(
            viewModel = viewModel,
            dialog = dialog,
            target = target,
            title = dialog.setlist?.title ?: dialog.song?.title.orEmpty(),
            source = state.source,
            isOpen = isOpen,
        )
    }
    val chosenSongFileNames = chosenSongFileNames(source = state.source, selected = state.selected.orEmpty())
    val canExportFiles = canExportFiles(source = state.source, selected = state.selected.orEmpty())
    val hasPages = laidOut?.document?.pages?.isNotEmpty() == true
    val pageCount = laidOut?.document?.pages?.size ?: 0
    val pagerState = rememberPagerState(initialPage = state.page.coerceIn(0, maxOf(0, pageCount - 1))) { pageCount }
    val pageScope = rememberCoroutineScope()
    val pageResetSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    var pageResetAnimation by remember { mutableStateOf<Job?>(null) }
    // Keep paging alive even when the preview scrolls out of the portrait list.
    LaunchedEffect(pageCount) {
        if (pageCount > 0) pagerState.scrollToPage(state.page.coerceIn(0, pageCount - 1))
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (pagerState.pageCount > 0) {
                if (state.page != settled) {
                    pageResetAnimation?.cancel()
                    val initialView = state.pageView
                    pageResetAnimation = pageScope.launch {
                        // One spring moves zoom and focus together, returning the page smoothly to its fitted position.
                        animate(0f, 1f, animationSpec = pageResetSpec) { value, _ ->
                            val progress = value.coerceIn(0f, 1f)
                            state.pageView = PageView(
                                zoom = initialView.zoom + (1f - initialView.zoom) * progress,
                                focus = initialView.focus + (Offset(0.5f, 0.5f) - initialView.focus) * progress,
                            )
                        }
                    }
                }
                state.page = settled
            }
        }
    }
    val canSave = isCurrent && hasPages && !isFileTransferActive
    // A tap while the pages are being laid out again for an option just changed is kept until they are, rather than
    // being refused: a floating action button has no disabled look to say it would be, and the layout takes a moment.
    var requestedExport by remember(dialog) { mutableStateOf<ExportRequest?>(null) }
    LaunchedEffect(requestedExport, canSave, hasPages, isFiles) {
        val request = requestedExport ?: return@LaunchedEffect
        // A PDF asked for before the format was switched away is not one to come up once it is switched back.
        if (!hasPages || !isPdf) {
            requestedExport = null
        } else if (canSave) {
            requestedExport = null
            laidOut?.document?.let { snapshot ->
                // From the screen's own settings, which the saved preferences may not have caught up with yet.
                viewModel.exportController.exportPdf(
                    filePicker = filePicker,
                    fileName = pdfFileName(state.source!!, state.settings),
                    dialog = dialog,
                    pageCount = snapshot.pages.size,
                    isShare = request == ExportRequest.SHARE,
                ) { onPage ->
                    newRenderer().pdf(snapshot, state.source!!.title, onPage = onPage)
                }
            }
        }
    }
    // Any import or export running would make the view model ignore the tap, so it is not even asked.
    val requestExport = { request: ExportRequest ->
        if (isOpen && !isFileTransferActive) {
            when {
                isLink -> if (request == ExportRequest.SHARE) link?.onShare?.invoke() else link?.onSaveQrCode?.invoke()
                isFiles -> viewModel.exportController.exportFiles(filePicker, dialog, chosenSongFileNames, isShare = request == ExportRequest.SHARE)
                else -> requestedExport = request
            }
        }
    }
    val close = { viewModel.dialogHost.dismissSheet(dialog) }
    val content = exportContentOf(isFailed = state.failed, isLayoutFailed = state.layoutFailed, isPdf = isPdf, hasSource = state.source != null)
    val canSaveNow = content == PrintScreenContent.LOADED && when {
        // The QR code is what a link saves, by Ctrl / Cmd + S, and Share in the app bar shares the link.
        isLink -> link?.shared != null
        isFiles -> canExportFiles
        else -> hasPages
    }
    // Drawn in the same window as the screen under it, so it does not take the focus by being there, and a key would
    // otherwise go on reaching that screen - a pedal stepping the song being exported. A bare focus target draws no
    // indication and does not hand the focus on to the first button, so it is taken on every platform, whatever the
    // screen shows; the preview moves it further in where it takes it for its own arrows. Holding it is also what lets
    // Ctrl / Cmd + S press Save, which it only does while the button is there and is not counting pages.
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { rootFocus.requestFocus() }
    // Read when focus tries to leave, not at composition: the screen stays drawn while it slides away, and by then the
    // song under it is taking the focus back.
    val isTrappingFocus by rememberUpdatedState(isOpen)
    // A Surface, so that nothing of the app under it can be pressed through it.
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .saveShortcut { if (canSaveNow && exportProgress == null) requestExport(ExportRequest.SAVE) }
            // The screen under this one is still in the same window and still focusable, before this one in the
            // order, so Tab and Shift + Tab would walk into it - onto the buttons of a screen nobody can see. The focus
            // group around the root target keeps every move inside; a group is consulted only when focus leaves a
            // child of it, which is why the root target is a node of its own inside it.
            .focusProperties { onExit = { if (isTrappingFocus) cancelFocusChange() } }
            .focusGroup()
            .focusRequester(rootFocus)
            .focusTarget(),
        color = MaterialTheme.colorScheme.background,
    ) {
        val bottomInset = WindowInsets.contentEdges.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
        // In a short window the two floating controls, stacked at the end of the preview's pane, would leave a page
        // between their bands no taller than one of them, so there the page sits beside them instead, in the pane less
        // the column they take, which is as wide as the wider of the two. The pill is set as wide as its text at its
        // widest for the PDF's page count, every digit counted as the widest one, and known before it is laid out, so the
        // page beside it is right from the first frame and does not move as the pages are turned.
        val isShortWindow = LocalWindowInfo.current.containerDpSize.height < SHORT_WINDOW_HEIGHT
        val density = LocalDensity.current
        val pageButtonsWidth = pageButtonsWidthFor(pageCount)
        var saveButtonWidth by remember { mutableStateOf(0.dp) }
        // The Link button floats outside the body, which alone knows whether the options stand beside the preview.
        var isBodySideBySide by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                PrintTopAppBar(
                    title = stringResource(exportTitleOf(dialog)),
                    subtitle = dialog.setlist?.title ?: dialog.song?.let { songLabel(it) } ?: songsCount,
                    // The screen itself is called Share, so the Link format's button says what it sends.
                    shareLabel = stringResource(if (isLink) Res.string.share_send else Res.string.print_share),
                    canShare = filePicker.canShare && exportProgress == null && canSaveNow,
                    onShare = { requestExport(ExportRequest.SHARE) },
                    onClose = close,
                )
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.contentEdges.only(WindowInsetsSides.Horizontal)),
                ) {
                    val bodyWidth = maxWidth
                    val bodyHeight = maxHeight
                    val optionsWidth = optionsWidthFor(bodyWidth = bodyWidth, bodyHeight = bodyHeight)
                    val isSideBySide = optionsWidth != null
                    SideEffect { isBodySideBySide = isSideBySide }
                    val pageFit = pageFitFor(
                        isSideBySide = isSideBySide,
                        isShortWindow = isShortWindow,
                        floatingControlsWidth = maxOf(pageButtonsWidth, saveButtonWidth),
                        bottomInset = bottomInset,
                    )
                    val turnPage: (Int) -> Unit = { target -> pageScope.launch { pagerState.animateScrollToPage(target.coerceIn(0, pageCount - 1)) } }
                    AnimatedContent(content, Modifier.fillMaxSize(), transitionSpec = { fadeIn() togetherWith fadeOut() }) { shown ->
                        when (shown) {
                            PrintScreenContent.FAILED -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(Res.string.print_load_failed))
                                    TextButton(onClick = { state.attempt++ }) { Text(stringResource(Res.string.retry)) }
                                }
                            }
                            PrintScreenContent.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                DelayedLoadingIndicator()
                            }
                            PrintScreenContent.LOADED -> if (state.source != null) {
                                val preview: @Composable (Modifier) -> Unit = { modifier ->
                                    ExportPreviewPane(
                                        modifier = modifier,
                                        state = state,
                                        format = state.settings.format,
                                        setlistFileName = dialog.setlist?.fileName,
                                        isSideBySide = isSideBySide,
                                        bottomInset = bottomInset,
                                        laidOut = laidOut,
                                        isCurrent = isCurrent,
                                        renderer = renderer,
                                        pageFit = pageFit,
                                        pagerState = pagerState,
                                        magnifications = viewModel.fontScaleController.printPreviewMagnifications,
                                        onPageViewChanged = {
                                            // A new zoom or pan gesture takes over from the automatic reset.
                                            pageResetAnimation?.cancel()
                                            state.pageView = it
                                        },
                                        hasPages = hasPages,
                                        onTurn = turnPage,
                                        linkPreview = {
                                            if (link != null) {
                                                LinkPreview(
                                                    modifier = Modifier.fillMaxSize(),
                                                    state = link.state,
                                                    kind = shareTargetOf(dialog)!!.kind,
                                                    contentPadding = { shown ->
                                                        linkPreviewPaddingFor(
                                                            isSideBySide = isSideBySide,
                                                            isShortWindow = isShortWindow,
                                                            isShared = shown is LinkExportState.Shared,
                                                            bottomInset = bottomInset,
                                                        )
                                                    },
                                                )
                                            }
                                        },
                                    )
                                }
                                val options: @Composable (Modifier) -> Unit = { modifier ->
                                    PrintOptions(
                                        modifier = modifier,
                                        source = state.source!!,
                                        settings = state.settings,
                                        formats = formats,
                                        areChordsEnabled = areChordsEnabled,
                                        isMetronomeEnabled = isMetronomeEnabled,
                                        selected = state.selected.orEmpty(),
                                        // Scroll under the controls, with enough trailing space to bring the last option above them.
                                        bottomPadding = bottomInset + if (isSideBySide) 8.dp else FLOATING_CONTROLS_CLEARANCE,
                                        header = if (isSideBySide) null else { { preview(Modifier.fillMaxWidth().height(STACKED_PREVIEW_HEIGHT)) } },
                                        onSelected = { if (isOpen) state.selected = it },
                                        onSettings = update,
                                        linkOptions = {
                                            LinkOptions(
                                                isShared = link?.shared != null,
                                                onSaveQrCode = { link?.onSaveQrCode?.invoke() },
                                                onStopSharing = { link?.isStopAsked = true },
                                            )
                                        },
                                    )
                                }
                                PrintPanes(
                                    optionsWidth = optionsWidth,
                                    options = options,
                                    preview = preview,
                                )
                            }
                        }
                    }
                    PageButtons(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(PAGE_MARGIN),
                        isVisible = isSideBySide && content == PrintScreenContent.LOADED && isPdf && hasPages,
                        page = pagerState.currentPage,
                        pageCount = pageCount,
                        onTurn = turnPage,
                    )
                }
            }
            SaveButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.contentEdges.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
                    .padding(PAGE_MARGIN)
                    .onSizeChanged { saveButtonWidth = with(density) { it.width.toDp() } },
                isVisible = exportProgress != null && content == PrintScreenContent.LOADED || canSaveNow && !isLink,
                progress = exportProgress,
                onSave = { requestExport(ExportRequest.SAVE) },
                onCancel = viewModel.exportController::cancelPdfExport,
            )
            LinkButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.contentEdges.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
                    .padding(PAGE_MARGIN),
                action = link?.action?.takeIf { isLink && content == PrintScreenContent.LOADED },
                isInShortPane = isShortWindow && isBodySideBySide,
                onClick = { link?.onAction?.invoke(it) },
            )
        }
    }
    link?.takeIf { it.isStopAsked && it.shared != null }?.let {
        StopSharingConfirmation(
            title = dialog.setlist?.title ?: dialog.song?.title.orEmpty(),
            onDismiss = { it.isStopAsked = false },
            onConfirm = it.onStopSharing,
        )
    }
}

/** What a tap on Save or Share asked for, kept until the pages it is to export are laid out. */
private enum class ExportRequest { SAVE, SHARE }
