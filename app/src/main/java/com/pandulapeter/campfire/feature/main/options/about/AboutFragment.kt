package com.pandulapeter.campfire.feature.main.options.about

import android.animation.ObjectAnimator
import android.util.Property
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOptionsAboutBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.onEventTriggered

class AboutFragment : CampfireFragment<FragmentOptionsAboutBinding, AboutViewModel>(R.layout.fragment_options_about) {

    override val viewModel by lazy {
        AboutViewModel { getCampfireActivity().isUiBlocked }.apply {
            shouldShowErrorShowSnackbar.onEventTriggered(this@AboutFragment) { showSnackbar(R.string.options_about_error) }
            shouldShowWorkInProgressSnackbar.onEventTriggered(this@AboutFragment) { showSnackbar(R.string.options_about_no_in_app_purchase) }
            //TODO: Easter Egg.
            shouldShowNoEasterEggSnackbar.onEventTriggered(this@AboutFragment) {
                ObjectAnimator
                    .ofFloat(binding.logo, scale, 1f, 1.5f, 0.5f, 1.25f, 0.75f, 1.1f, 0.9f, 1f)
                    .setDuration(800)
                    .start()
                if (!isSnackbarVisible()) {
                    showSnackbar(R.string.options_about_no_easter_egg)
                }
            }
            shouldBlockUi.onEventTriggered { getCampfireActivity().isUiBlocked = true }
        }
    }
    private val scale by lazy {
        object : Property<View, Float>(Float::class.java, "scale") {

            override fun set(view: View?, value: Float) {
                view?.run {
                    view.scaleX = value
                    view.scaleY = value
                }
            }

            override fun get(view: View) = view.scaleX
        }
    }
}