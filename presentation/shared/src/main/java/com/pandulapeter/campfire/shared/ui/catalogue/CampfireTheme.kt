package com.pandulapeter.campfire.shared.ui.catalogue

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colorBrand = Color(245, 124, 0)

private val campfireDarkColors = darkColors(
    primary = colorBrand,
    secondary = colorBrand
)

private val campfireLightColors = lightColors(
    primary = colorBrand,
    secondary = colorBrand
)

@Composable
fun CampfireTheme(
    shouldUseDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) = MaterialTheme(
    colors = if (shouldUseDarkTheme) campfireDarkColors else campfireLightColors,
    content = content
)