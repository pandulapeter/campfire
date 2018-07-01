package com.pandulapeter.campfire.feature.main.home.home

data class HomeHeaderViewModel(val title: String) : HomeItemViewModel {

    override fun getItemId() = title.hashCode().toLong()
}