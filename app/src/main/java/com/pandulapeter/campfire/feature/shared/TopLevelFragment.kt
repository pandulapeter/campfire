package com.pandulapeter.campfire.feature.shared

import com.pandulapeter.campfire.feature.shared.behavior.TopLevelBehavior

interface TopLevelFragment {

    val shouldShowAppBar: Boolean get() = true
    val topLevelBehavior: TopLevelBehavior

    fun onDrawerStateChanged(state: Int) = Unit

    fun onFloatingActionButtonPressed() = Unit
}