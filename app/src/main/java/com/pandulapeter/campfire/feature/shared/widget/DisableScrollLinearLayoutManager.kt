package com.pandulapeter.campfire.feature.shared.widget

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import com.pandulapeter.campfire.feature.shared.InteractionBlocker

class DisableScrollLinearLayoutManager(context: Context) : LinearLayoutManager(context) {

    var interactionBlocker: InteractionBlocker? = null

    override fun canScrollVertically() = interactionBlocker?.isUiBlocked == false && super.canScrollVertically()
}