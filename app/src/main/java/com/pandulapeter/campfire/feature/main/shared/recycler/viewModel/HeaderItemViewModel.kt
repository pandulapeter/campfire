package com.pandulapeter.campfire.feature.main.shared.recycler.viewModel

import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

data class HeaderItemViewModel(val title: Any, val refreshAction: (() -> Unit)? = null) : ItemViewModel {

    override fun getItemId() = title.hashCode().toLong()
    val shouldShowRefreshButton = refreshAction != null
    private val animatorInterpolator = AccelerateDecelerateInterpolator()

    fun onRefreshButtonPressed(view: View) {
        refreshAction?.invoke()
        view.animate().cancel()
        view.animate().rotationBy(360f).apply {
            interpolator = animatorInterpolator
            duration = 600
        }.start()
    }
}