package com.pandulapeter.campfire.feature.shared

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.pandulapeter.campfire.util.consume

@Suppress("unused")
class SnackbarSlideBehavior @JvmOverloads constructor(context: Context? = null, attrs: AttributeSet? = null) : CoordinatorLayout.Behavior<View>(context, attrs) {

    override fun onAttachedToLayoutParams(lp: CoordinatorLayout.LayoutParams) {
        if (lp.dodgeInsetEdges == Gravity.NO_GRAVITY) {
            lp.dodgeInsetEdges = Gravity.BOTTOM
        }
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: View, layoutDirection: Int) = consume { parent.onLayoutChild(child, layoutDirection) }

    override fun getInsetDodgeRect(parent: CoordinatorLayout, child: View, rect: Rect) = consume { rect.set(child.left, child.top, child.right, child.bottom) }
}