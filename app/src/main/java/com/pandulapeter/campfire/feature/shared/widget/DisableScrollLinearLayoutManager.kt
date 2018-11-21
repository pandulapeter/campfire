package com.pandulapeter.campfire.feature.shared.widget

import android.content.Context
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

class DisableScrollLinearLayoutManager(context: Context) : androidx.recyclerview.widget.LinearLayoutManager(context) {

    var isScrollEnabled = false

    override fun canScrollVertically() = isScrollEnabled && super.canScrollVertically()

    override fun onSaveInstanceState(): Parcelable {
        return State(isScrollEnabled, super.onSaveInstanceState())
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState((state as? State)?.superState ?: state)
        (state as? State)?.let { isScrollEnabled = it.isScrollEnabled }
    }

    @Parcelize
    data class State(val isScrollEnabled: Boolean, val superState: Parcelable?) : Parcelable
}