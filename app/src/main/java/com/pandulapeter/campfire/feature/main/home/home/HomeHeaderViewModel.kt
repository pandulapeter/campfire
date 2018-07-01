package com.pandulapeter.campfire.feature.main.home.home

import android.view.View

data class HomeHeaderViewModel(val title: String, val refreshAction: (() -> Unit)? = null) : HomeItemViewModel {

    val shouldShowRefreshButton = refreshAction != null

    override fun getItemId() = title.hashCode().toLong()

    fun onRefreshButtonPressed(view: View) {
        refreshAction?.invoke()
        view.animate().cancel()
        view.animate().rotationBy(360f).start()
    }
}