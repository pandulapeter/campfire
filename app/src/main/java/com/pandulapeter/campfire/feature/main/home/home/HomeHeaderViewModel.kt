package com.pandulapeter.campfire.feature.main.home.home

data class HomeHeaderViewModel(val title: String, val refreshAction: (() -> Unit)? = null) : HomeItemViewModel {

    val shouldShowRefreshButton = refreshAction != null

    override fun getItemId() = title.hashCode().toLong()

    fun onRefreshButtonPressed() {
        refreshAction?.invoke()
    }
}