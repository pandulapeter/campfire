package com.pandulapeter.campfire.feature.home.shared

import android.graphics.Rect
import android.support.v7.widget.RecyclerView
import android.view.View

/**
 * Custom [RecyclerView.ItemDecoration] that adds spaces between items.
 */
class SpacesItemDecoration(private var space: Int) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State?) {
        outRect.apply {
            left = space
            top = if (parent.getChildAdapterPosition(view) == 0) space else 0
            right = 0
            bottom = space
        }
    }
}