package com.pandulapeter.campfire.feature.home.shared

import android.graphics.Canvas
import android.support.v4.view.ViewCompat
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper

/**
 * Provides support for specifying the elevation to use when an item is being dragged.
 */
abstract class ElevationItemTouchHelperCallback(
    private val activeElevationChange: Float,
    dragDirs: Int = 0,
    swipeDirs: Int = 0
) : ItemTouchHelper.SimpleCallback(dragDirs, swipeDirs) {
    private var isElevated = false
    private var originalElevation = 0f

    override fun onChildDraw(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, false)
        if (isCurrentlyActive && !isElevated) {
            updateElevation(recyclerView, viewHolder, true)
        }
    }

    override fun clearView(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        updateElevation(recyclerView!!, viewHolder, false)
    }

    private fun updateElevation(recyclerView: RecyclerView, holder: RecyclerView.ViewHolder, elevate: Boolean) {
        if (elevate) {
            originalElevation = ViewCompat.getElevation(holder.itemView)
            val newElevation = activeElevationChange + findMaxElevation(recyclerView)
            ViewCompat.setElevation(holder.itemView, newElevation)
            isElevated = true
        } else {
            ViewCompat.setElevation(holder.itemView, originalElevation)
            originalElevation = 0f
            isElevated = false
        }
    }

    private fun findMaxElevation(recyclerView: RecyclerView) = (0 until recyclerView.childCount)
        .map { ViewCompat.getElevation(recyclerView.getChildAt(it)) }
        .max() ?: 0f
}