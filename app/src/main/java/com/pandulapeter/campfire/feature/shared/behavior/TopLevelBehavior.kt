package com.pandulapeter.campfire.feature.shared.behavior

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.pandulapeter.campfire.feature.CampfireActivity

class TopLevelBehavior(
    private val getCampfireActivity: () -> CampfireActivity?,
    private val appBarView: View? = null,
    private val inflateToolbarTitle: ((Context) -> View)? = null,
    private val shouldChangeToolbarAutomatically: Boolean = true
) : Behavior() {
    val defaultToolbar by lazy { AppCompatTextView(getCampfireActivity()).apply { gravity = Gravity.CENTER_VERTICAL } }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        getCampfireActivity()?.run {
            onScreenChanged()
            if (shouldChangeToolbarAutomatically) {
                changeToolbar()
            }
            updateAppBarView(appBarView, savedInstanceState != null)
        }
    }

    fun changeToolbar() {
        getCampfireActivity()?.run { updateToolbarTitleView(inflateToolbarTitle?.invoke(toolbarContext) ?: defaultToolbar) }
    }
}