package com.pandulapeter.campfire.feature.shared.behavior

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.pandulapeter.campfire.feature.CampfireActivity

class TopLevelBehavior(
    private val getContext: () -> Context?,
    private val getCampfireActivity: () -> CampfireActivity?,
    private val appBarView: View? = null,
    private val inflateToolbarTitle: ((Context) -> View)? = null
) : Behavior() {
    val defaultToolbar by lazy { AppCompatTextView(getContext()).apply { gravity = Gravity.CENTER_VERTICAL } }
    var toolbarWidth = 0

    override fun onViewCreated(savedInstanceState: Bundle?) {
        getCampfireActivity()?.run {
            onScreenChanged()
            //TODO: if (this !is DetailFragment) {
            updateToolbarTitleView(inflateToolbarTitle?.invoke(toolbarContext) ?: defaultToolbar, toolbarWidth)
//        }
            //TODO   if (savedInstanceState == null || this !is SongsFragment) {
            updateAppBarView(appBarView, savedInstanceState != null)
//            }
        }
    }
}