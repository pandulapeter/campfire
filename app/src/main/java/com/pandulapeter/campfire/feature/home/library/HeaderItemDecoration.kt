package com.pandulapeter.campfire.feature.home.library

import android.content.Context
import android.databinding.DataBindingUtil
import android.graphics.Canvas
import android.graphics.Rect
import android.support.v4.graphics.ColorUtils
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.HeaderItemBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.dimension

abstract class HeaderItemDecoration(context: Context) : RecyclerView.ItemDecoration() {
    private var headerBinding: HeaderItemBinding? = null
    private val headerHeight = context.dimension(R.dimen.header_height)
    private val visibleColor = context.color(R.color.accent)
    private val invisibleColor = context.color(android.R.color.transparent)

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
        if (parent.isAttachedToWindow) {
            if (headerBinding == null) {
                DataBindingUtil.inflate<HeaderItemBinding>(LayoutInflater.from(parent.context), R.layout.item_header, parent, false).let {
                    it.root.measure(
                        ViewGroup.getChildMeasureSpec(View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY), 0, it.root.layoutParams.width),
                        ViewGroup.getChildMeasureSpec(
                            View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED),
                            0,
                            it.root.layoutParams.height
                        )
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
                        it.title.setTextColor(
                            if (child.alpha != 1f) {
                                ColorUtils.blendARGB(invisibleColor, visibleColor, child.alpha)
                            } else {
                                visibleColor
                            }
                        )
                        drawHeader(canvas, child, it.root)
                        previousHeaderText = title
                    }
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