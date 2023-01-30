package com.pandulapeter.campfire.shared.ui.catalogue.resources

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object CampfireIcons {

    val songs by lazy { makeIconFromXMLPath("M12 3V13.55C11.41 13.21 10.73 13 10 13C7.79 13 6 14.79 6 17S7.79 21 10 21 14 19.21 14 17V7H18V3H12Z") }
    val setlists = Icons.Rounded.Star
    val settings = Icons.Rounded.Settings
    val clear = Icons.Rounded.Clear
    val search = Icons.Rounded.Search

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