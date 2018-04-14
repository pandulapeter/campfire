package com.pandulapeter.campfire.feature.home.shared

import android.content.Context
import android.support.v7.widget.LinearLayoutManager

class DisableScrollLinearLayoutManager(context: Context) : LinearLayoutManager(context) {

    var isScrollEnabled = false

    override fun canScrollVertically() = isScrollEnabled && super.canScrollVertically()
}