package com.pandulapeter.campfire.feature.home.shared

import android.content.Context
import android.os.Parcelable
import android.support.v7.widget.LinearLayoutManager
import kotlinx.android.parcel.Parcelize

class DisableScrollLinearLayoutManager(context: Context) : LinearLayoutManager(context) {

    var isScrollEnabled = false

    override fun canScrollVertically() = isScrollEnabled && super.canScrollVertically()

    override fun onSaveInstanceState(): Parcelable {
        return State(isScrollEnabled, super.onSaveInstanceState())
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState((state as State).superState)
        isScrollEnabled = state.isScrollEnabled
    }

    @Parcelize
    data class State(val isScrollEnabled: Boolean, val superState: Parcelable) : Parcelable
}