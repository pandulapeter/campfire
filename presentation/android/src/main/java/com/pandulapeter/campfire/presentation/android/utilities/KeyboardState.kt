package com.pandulapeter.campfire.presentation.android.utilities

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity

@Composable
internal fun keyboardState(): State<Boolean> = rememberUpdatedState(WindowInsets.ime.getBottom(LocalDensity.current) > 0)