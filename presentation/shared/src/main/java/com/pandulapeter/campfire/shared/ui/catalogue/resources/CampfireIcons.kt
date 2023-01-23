package com.pandulapeter.campfire.shared.ui.catalogue.resources

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object CampfireIcons {

    val Collections by lazy { makeIconFromXMLPath("M4 6H2V20C2 21.1 2.9 22 4 22H18V20H4V6M20 2H8C6.9 2 6 2.9 6 4V16C6 17.1 6.9 18 8 18H20C21.1 18 22 17.1 22 16V4C22 2.9 21.1 2 20 2M20 12L17.5 10.5L15 12V4H20V12Z") }
    val Songs by lazy { makeIconFromXMLPath("M12 3V13.55C11.41 13.21 10.73 13 10 13C7.79 13 6 14.79 6 17S7.79 21 10 21 14 19.21 14 17V7H18V3H12Z") }

    private fun makeIconFromXMLPath(
        pathStr: String,
        viewportWidth: Float = 24f,
        viewportHeight: Float = 24f,
        defaultWidth: Dp = 24.dp,
        defaultHeight: Dp = 24.dp,
        fillColor: Color = Color.White,
    ): ImageVector {
        val fillBrush = SolidColor(fillColor)
        val strokeBrush = SolidColor(fillColor)

        return ImageVector.Builder(
            defaultWidth = defaultWidth,
            defaultHeight = defaultHeight,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        ).run {
            addPath(
                pathData = addPathNodes(pathStr),
                name = "",
                fill = fillBrush,
                stroke = strokeBrush,
            )
            build()
        }
    }
}