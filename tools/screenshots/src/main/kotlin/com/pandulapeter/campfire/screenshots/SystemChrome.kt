/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What a platform draws around an app, which the desktop build being rendered has none of: where the app's window is
 * on the screen ([windowTop], [windowBottom], the room a menu bar, a shelf or a taskbar keeps), which of its edges the
 * system draws over ([statusBar], [navigationBar] and, on the desktops, [captionBar], each reported to the app as
 * the inset the platform would report), and the drawing itself, laid over the whole screen once the app is in place.
 *
 * Every shot is taken at the same moment, 9:41 on Wednesday, October 7, with a full battery and a strong signal, as
 * the stores' own screenshots are. The phones' and tablets' bars are laid out to the dp and the point as the current
 * Android and iOS draw them, measured on their emulators and simulators.
 */
internal sealed interface SystemChrome {
    val windowTop: Dp get() = 0.dp
    val windowBottom: Dp get() = 0.dp
    val statusBar: Dp get() = 0.dp
    val navigationBar: Dp get() = 0.dp
    val captionBar: Dp get() = 0.dp

    @Composable
    fun Overlay(appearance: ChromeAppearance)

    /** The status bar Android makes as tall as the Pixel's camera cutout, and the gesture handle under the app. */
    /** No system at all around the app, for an image that is framed by whatever shows it. */
    data object None : SystemChrome {
        @Composable
        override fun Overlay(appearance: ChromeAppearance) = Unit
    }

    data object AndroidPhone : SystemChrome {
        override val statusBar = 53.dp
        override val navigationBar = 24.dp

        @Composable
        override fun Overlay(appearance: ChromeAppearance) = AndroidBars(
            appearance = appearance,
            statusBar = statusBar,
            navigationBar = navigationBar,
            metrics = AndroidBarMetrics(
                timeSize = 15.sp,
                startPadding = 16.dp,
                endPadding = 24.dp,
                iconHeight = 14.dp,
                batteryWidth = 28.dp,
                handleWidth = 108.dp,
                hasCellular = true,
            ),
        )
    }

    /** A Wi-Fi tablet's slimmer status bar, and the handle of the taskbar stashed under the app. */
    data object AndroidTablet : SystemChrome {
        override val statusBar = 24.dp
        override val navigationBar = 32.dp

        @Composable
        override fun Overlay(appearance: ChromeAppearance) = AndroidBars(
            appearance = appearance,
            statusBar = statusBar,
            navigationBar = navigationBar,
            metrics = AndroidBarMetrics(
                timeSize = 14.sp,
                startPadding = 12.dp,
                endPadding = 16.dp,
                iconHeight = 12.dp,
                batteryWidth = 24.dp,
                handleWidth = 220.dp,
                hasCellular = false,
            ),
        )
    }

    data object ChromeOs : SystemChrome {
        override val windowTop = SCREEN_BAR_UNDER_CAMERA_COVER_14
        override val windowBottom = 48.dp

        @Composable
        override fun Overlay(appearance: ChromeAppearance) = Box(Modifier.fillMaxSize()) {
            CameraCoverBand(height = windowTop)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(windowBottom)
                    .background(if (appearance.isDark) Color(0xFF202124) else Color(0xFFDDE3EA))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(appearance.content.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(10.dp).clip(CircleShape).background(appearance.content)) }
                Spacer(Modifier.weight(1f))
                AppIcon(appearance = appearance, size = 32.dp)
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(appearance.content.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChromeText(text = "9:41", appearance = appearance, size = 13.sp, weight = FontWeight.Medium)
                    Canvas(Modifier.size(16.dp)) { drawWifi(appearance.content) }
                    Canvas(Modifier.size(width = 18.dp, height = 10.dp)) { drawBattery(appearance.content, hasNub = false) }
                }
            }
        }
    }

    data object IPhone : SystemChrome {
        override val statusBar = 62.dp
        override val navigationBar = 34.dp

        @Composable
        override fun Overlay(appearance: ChromeAppearance) = Box(Modifier.fillMaxSize()) {
            // The time and the icons are centered in what the Dynamic Island leaves of the bar at either side of it,
            // 32.5pt down, level with the island's middle, which the device frame draws.
            Row(
                modifier = Modifier.fillMaxWidth().height(65.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    ChromeText(text = "9:41", appearance = appearance, size = 17.sp, weight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(111.dp))
                Row(
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(width = 21.dp, height = 13.dp)) { drawCellular(appearance.content) }
                    Canvas(Modifier.size(width = 19.dp, height = 13.dp)) { drawWifi(appearance.content) }
                    Canvas(Modifier.size(width = 28.dp, height = 13.dp)) { drawBattery(appearance.content, hasNub = true) }
                }
            }
            HomeIndicator(appearance = appearance, width = 144.dp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }
    }

    data object IPad : SystemChrome {
        override val statusBar = 24.dp
        override val navigationBar = 20.dp

        @Composable
        override fun Overlay(appearance: ChromeAppearance) = Box(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(statusBar).padding(start = 26.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChromeText(text = "9:41 AM  Wed Oct 7", appearance = appearance, size = 14.sp, weight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Canvas(Modifier.size(width = 15.dp, height = 10.dp)) { drawWifi(appearance.content) }
                Spacer(Modifier.width(7.dp))
                Canvas(Modifier.size(width = 26.dp, height = 12.dp)) { drawBattery(appearance.content, hasNub = true) }
            }
            HomeIndicator(appearance = appearance, width = 320.dp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }
    }

    /**
     * A window zoomed to fill the screen under the menu bar, which on a MacBook with a camera housing is black whatever
     * the theme and as tall as the housing. The Screenshot Bro rows cover the housing itself with a black strip. The
     * window's buttons are where the desktop app's own are, measured from its window: 12pt across, 20pt apart, the first
     * centered 14pt from the window's top and left edges.
     */
    data object MacOs : SystemChrome {
        override val windowTop = 37.dp
        override val captionBar = 28.dp

        @Composable
        override fun Overlay(appearance: ChromeAppearance) = Box(Modifier.fillMaxSize()) {
            val menuBar = ChromeAppearance(isDark = true, fontFamily = appearance.fontFamily, appIcon = appearance.appIcon)
            Row(
                modifier = Modifier.fillMaxWidth().height(windowTop).background(Color.Black).padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChromeText(text = "\uF8FF", appearance = menuBar, size = 16.sp)
                Spacer(Modifier.width(22.dp))
                ChromeText(text = "Campfire", appearance = menuBar, size = 13.sp, weight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(width = 25.dp, height = 12.dp)) { drawBattery(menuBar.content, hasNub = true) }
                    Canvas(Modifier.size(width = 16.dp, height = 12.dp)) { drawWifi(menuBar.content) }
                    Canvas(Modifier.size(14.dp)) { drawMagnifier(menuBar.content) }
                    Canvas(Modifier.size(width = 16.dp, height = 13.dp)) { drawControlCenter(menuBar.content) }
                    ChromeText(text = "Wed Oct 7  9:41 AM", appearance = menuBar, size = 13.sp)
                }
            }
            Row(
                modifier = Modifier.padding(top = windowTop).height(captionBar).padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(Color(0xFFFF5F57), Color(0xFFFEBC2E), Color(0xFF28C840)).forEach { color ->
                    Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                }
            }
        }
    }

    /**
     * A maximized window above a Windows 11 taskbar, sized in the effective pixels Windows lays both out in: caption
     * buttons of 46 by 32 with 10 wide glyphs (Restore rather than Maximize, the window being maximized), and a taskbar
     * of 48 whose 40 wide buttons hold 24 wide icons, centered - Start and the app, marked as the one in front - with
     * the system tray at its end.
     */
    data object Windows : SystemChrome {
        override val windowTop = SCREEN_BAR_UNDER_CAMERA_COVER_16
        override val windowBottom = 48.dp
        override val captionBar = 32.dp

        @Composable
        override fun Overlay(appearance: ChromeAppearance) = Box(Modifier.fillMaxSize()) {
            CameraCoverBand(height = windowTop)
            val glyph = 1.dp
            Row(Modifier.align(Alignment.TopEnd).padding(top = windowTop).height(captionBar)) {
                CaptionButton { drawLine(appearance.content, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), glyph.toPx()) }
                CaptionButton {
                    val back = size.width * 0.2f
                    val stroke = Stroke(glyph.toPx())
                    drawRoundRect(appearance.content, topLeft = Offset(back, 0f), size = Size(size.width - back, size.height - back), cornerRadius = CornerRadius(glyph.toPx() * 1.5f), style = stroke)
                    drawRoundRect(
                        color = if (appearance.isDark) Color(0xFF1C1B22) else Color(0xFFF7F2FA),
                        topLeft = Offset(0f, back),
                        size = Size(size.width - back, size.height - back),
                        cornerRadius = CornerRadius(glyph.toPx() * 1.5f),
                    )
                    drawRoundRect(appearance.content, topLeft = Offset(0f, back), size = Size(size.width - back, size.height - back), cornerRadius = CornerRadius(glyph.toPx() * 1.5f), style = stroke)
                }
                CaptionButton {
                    drawLine(appearance.content, Offset.Zero, Offset(size.width, size.height), glyph.toPx())
                    drawLine(appearance.content, Offset(size.width, 0f), Offset(0f, size.height), glyph.toPx())
                }
            }
            val taskbar = if (appearance.isDark) Color(0xFF1C1C1C) else Color(0xFFEEF0F3)
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(windowBottom)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(appearance.content.copy(alpha = if (appearance.isDark) 0.08f else 0.1f)))
                Box(Modifier.fillMaxWidth().weight(1f).background(taskbar)) {
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                        TaskbarButton { Canvas(Modifier.size(24.dp)) { drawWindowsLogo(appearance.isDark) } }
                        TaskbarButton(
                            indicator = {
                                Box(
                                    Modifier.size(width = 16.dp, height = 3.dp).clip(CircleShape)
                                        .background(if (appearance.isDark) Color(0xFF4CC2FF) else Color(0xFF005FB8)),
                                )
                            },
                            isActive = true,
                            isDark = appearance.isDark,
                        ) { AppIcon(appearance = appearance, size = 24.dp) }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Canvas(Modifier.size(width = 32.dp, height = 12.dp)) { drawChevronUp(appearance.content) }
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Canvas(Modifier.size(width = 16.dp, height = 12.dp)) { drawWifi(appearance.content) }
                            Canvas(Modifier.size(16.dp)) { drawSpeaker(appearance.content) }
                            Canvas(Modifier.size(width = 18.dp, height = 9.dp)) { drawBattery(appearance.content, hasNub = true) }
                        }
                        Column(modifier = Modifier.padding(horizontal = 10.dp), horizontalAlignment = Alignment.End) {
                            ChromeText(text = "9:41 AM", appearance = appearance, size = 12.sp)
                            ChromeText(text = "10/7/2026", appearance = appearance, size = 12.sp)
                        }
                        Canvas(Modifier.size(16.dp)) { drawBell(appearance.content) }
                        Spacer(Modifier.width(20.dp))
                    }
                }
            }
        }
    }
}

/**
 * The Chromebook and Windows shots are framed in MacBook Pros, whose camera housing the Screenshot Bro rows hide under a
 * black bar across the top of the screen. The window starts under that bar instead of behind it - a black band of its
 * height on top, the image keeping the frame's size - so nothing of the app is covered and nothing is stretched. The
 * heights are the bars' as measured in the rows (about 66 and 70 px of the 16" and 14" screens), rounded up, since
 * band running past a bar is black against black.
 */
private val SCREEN_BAR_UNDER_CAMERA_COVER_16 = 36.dp
private val SCREEN_BAR_UNDER_CAMERA_COVER_14 = 38.dp

@Composable
private fun CameraCoverBand(height: Dp) = Box(Modifier.fillMaxWidth().height(height).background(Color.Black))

/** What sets a phone's Android bars apart from a tablet's. */
private class AndroidBarMetrics(
    val timeSize: TextUnit,
    val startPadding: Dp,
    val endPadding: Dp,
    val iconHeight: Dp,
    val batteryWidth: Dp,
    val handleWidth: Dp,
    val hasCellular: Boolean,
)

/** The status bar of Android 16 and later - cellular, Wi-Fi and the battery as a pill, in that order - and the handle. */
@Composable
private fun AndroidBars(
    appearance: ChromeAppearance,
    statusBar: Dp,
    navigationBar: Dp,
    metrics: AndroidBarMetrics,
) = Box(Modifier.fillMaxSize()) {
    Row(
        modifier = Modifier.fillMaxWidth().height(statusBar).padding(start = metrics.startPadding, end = metrics.endPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChromeText(text = "9:41", appearance = appearance, size = metrics.timeSize, weight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (metrics.hasCellular) {
                Canvas(Modifier.size(width = metrics.iconHeight * 1.25f, height = metrics.iconHeight)) { drawCellular(appearance.content) }
            }
            Canvas(Modifier.size(width = metrics.iconHeight * 1.3f, height = metrics.iconHeight)) { drawWifi(appearance.content) }
            Box(
                Modifier
                    .size(width = metrics.batteryWidth, height = metrics.iconHeight)
                    .clip(RoundedCornerShape(metrics.iconHeight * 0.32f))
                    .background(appearance.content),
            )
        }
    }
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = (navigationBar - 4.dp) / 2)
            .size(width = metrics.handleWidth, height = 4.dp)
            .clip(CircleShape)
            .background(appearance.content.copy(alpha = 0.75f)),
    )
}

@Composable
private fun HomeIndicator(
    appearance: ChromeAppearance,
    width: Dp,
    modifier: Modifier = Modifier,
) = Box(
    modifier = modifier
        .size(width = width, height = 5.dp)
        .clip(CircleShape)
        .background(appearance.content),
)

@Composable
private fun CaptionButton(drawGlyph: DrawScope.() -> Unit) = Box(
    modifier = Modifier.width(46.dp).fillMaxHeight(),
    contentAlignment = Alignment.Center,
) { Canvas(Modifier.size(10.dp), onDraw = drawGlyph) }

/** A 40 by 40 button of the Windows taskbar, with the pill under it that marks a running app. */
@Composable
private fun TaskbarButton(
    indicator: (@Composable () -> Unit)? = null,
    isActive: Boolean = false,
    isDark: Boolean = false,
    icon: @Composable () -> Unit,
) = Box(
    modifier = Modifier
        .padding(horizontal = 2.dp)
        .size(40.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(if (isActive) (if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.7f)) else Color.Transparent),
    contentAlignment = Alignment.Center,
) {
    icon()
    indicator?.let { Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp)) { it() } }
}

@Composable
private fun AppIcon(
    appearance: ChromeAppearance,
    size: Dp,
) = appearance.appIcon?.let { icon ->
    Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(size))
} ?: Unit

@Composable
private fun ChromeText(
    text: String,
    appearance: ChromeAppearance,
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
) = BasicText(
    text = text,
    style = TextStyle(
        color = appearance.content,
        fontFamily = appearance.fontFamily,
        fontSize = size,
        fontWeight = weight,
        textAlign = TextAlign.Center,
    ),
)
