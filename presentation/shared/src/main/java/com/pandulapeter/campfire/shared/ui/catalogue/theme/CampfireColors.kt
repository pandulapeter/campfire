package com.pandulapeter.campfire.shared.ui.catalogue.theme

import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object CampfireColors {

    val colorCampfireOrange = Color(245, 124, 0)
    val campfireLightColors by lazy {
        lightColors(
            primary = colorCampfireOrange,
            secondary = colorCampfireOrange
        )
    }
    val campfireDarkColors by lazy {
        darkColors(
            primary = colorCampfireOrange,
            secondary = colorCampfireOrange
        )
    }

    @Composable
    fun getUnselectedContentColor() = LocalContentColor.current.copy(alpha = ContentAlpha.medium)
}