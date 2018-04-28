package com.pandulapeter.campfire.feature.home.options.about

import android.animation.ObjectAnimator
import android.os.Bundle
import android.util.Property
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOptionsAboutBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.onEventTriggered

class AboutFragment : CampfireFragment<FragmentOptionsAboutBinding, AboutViewModel>(R.layout.fragment_options_about) {

    override val viewModel = AboutViewModel()
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.shouldShowErrorShowSnackbar.onEventTriggered(this) { showSnackbar(R.string.options_about_error) }
        viewModel.shouldShowWorkInProgressSnackbar.onEventTriggered(this) { showSnackbar(R.string.work_in_progress) }
        //TODO: Easter Egg.
        viewModel.shouldShowNoEasterEggSnackbar.onEventTriggered(this) {
            ObjectAnimator
                .ofFloat(binding.logo, scale, 1f, 1.5f, 0.5f, 1.25f, 0.75f, 1.1f, 0.9f, 1f)
                .setDuration(800)
                .start()
            if (!isSnackbarVisible()) {
                showSnackbar(R.string.options_about_no_easter_egg)
            }
        }
    }
}