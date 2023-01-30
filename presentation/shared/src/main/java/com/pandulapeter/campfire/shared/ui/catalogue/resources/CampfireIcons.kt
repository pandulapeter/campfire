package com.pandulapeter.campfire.shared.ui.catalogue.resources

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
    val add = Icons.Rounded.Add
    val sort by lazy { makeIconFromXMLPath("M18 21L14 17H17V7H14L18 3L22 7H19V17H22M2 19V17H12V19M2 13V11H9V13M2 7V5H6V7H2Z") }
    val filter by lazy { makeIconFromXMLPath("M14,12V19.88C14.04,20.18 13.94,20.5 13.71,20.71C13.32,21.1 12.69,21.1 12.3,20.71L10.29,18.7C10.06,18.47 9.96,18.16 10,17.87V12H9.97L4.21,4.62C3.87,4.19 3.95,3.56 4.38,3.22C4.57,3.08 4.78,3 5,3V3H19V3C19.22,3 19.43,3.08 19.62,3.22C20.05,3.56 20.13,4.19 19.79,4.62L14.03,12H14Z") }

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