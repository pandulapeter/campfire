package com.pandulapeter.campfire.feature.home.library

import android.content.Context
import android.databinding.DataBindingUtil
import android.graphics.Canvas
import android.graphics.Rect
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.HeaderItemBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.util.dimension

abstract class HeaderItemDecoration(context: Context) : RecyclerView.ItemDecoration() {
    private var headerBinding: HeaderItemBinding? = null
    private val headerHeight = context.dimension(R.dimen.header_height)

    abstract fun isHeader(position: Int): Boolean

    abstract fun getHeaderTitle(position: Int): String

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State?) {
        super.getItemOffsets(outRect, view, parent, state)
        val position = parent.getChildAdapterPosition(view)
        //TODO: Headers are removed during the "remove" animation causing a glitch.
        if (isHeader(position)) {
            outRect.top = headerHeight
        }
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State?) {
        super.onDraw(canvas, parent, state)
        if (headerBinding == null) {
            DataBindingUtil.inflate<HeaderItemBinding>(LayoutInflater.from(parent.context), R.layout.item_header, parent, false).let {
                it.root.measure(
                    ViewGroup.getChildMeasureSpec(View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY), 0, it.root.layoutParams.width),
                    ViewGroup.getChildMeasureSpec(View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED), 0, it.root.layoutParams.height)
                )
                it.root.layout(0, 0, it.root.measuredWidth, it.root.measuredHeight)
                headerBinding = it
            }
        }
        var previousHeaderText = ""
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            val title = getHeaderTitle(position)
            headerBinding?.let {
                if (previousHeaderText != title || isHeader(position)) {
                    it.title.text = title
                    //TODO: Item decorations are not animated during FragmentTransitions.
                    drawHeader(canvas, child, it.root)
                    previousHeaderText = title
                }
            }
        }
    }

    private fun drawHeader(canvas: Canvas, child: View, headerView: View) {
        canvas.save()
        canvas.translate(0f, child.y - headerView.height)
        headerView.draw(canvas)
        canvas.restore()
    }
}