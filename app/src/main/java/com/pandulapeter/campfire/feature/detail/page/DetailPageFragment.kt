package com.pandulapeter.campfire.feature.detail.page

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.databinding.FragmentDetailPageBinding
import com.pandulapeter.campfire.feature.detail.DetailEventBus
import com.pandulapeter.campfire.feature.detail.DetailFragment
import com.pandulapeter.campfire.feature.detail.page.parsing.SongParser
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.withArguments
import org.koin.android.ext.android.inject
import kotlin.math.roundToInt

class DetailPageFragment : CampfireFragment<FragmentDetailPageBinding, DetailPageViewModel>(R.layout.fragment_detail_page), DetailEventBus.Subscriber {

    companion object {
        private var Bundle.song by BundleArgumentDelegate.Parcelable("song")
        private var Bundle.isContentVisible by BundleArgumentDelegate.Boolean("isContentVisible")

        fun newInstance(song: Song) = DetailPageFragment().withArguments {
            it.song = song
        }
    }

    private val song by lazy { arguments?.song as Song }
    private var isContentVisible = false
    private val detailEventBus by inject<DetailEventBus>()
    private var smoothScrollHolder = 0f
    override val viewModel by lazy {
        DetailPageViewModel(
            song = song,
            initialTextSize = mainActivity.dimension(R.dimen.text_normal),
            songParser = SongParser(mainActivity),
            onDataLoaded = { (parentFragment as? DetailFragment)?.onDataLoaded(song.id) }
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        savedInstanceState?.let { isContentVisible = it.isContentVisible }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.isContentVisible = isContentVisible
    }

    override fun onStart() {
        super.onStart()
        detailEventBus.subscribe(this)
        if (isContentVisible) {
            binding.container.alpha = 1f
        }
    }

    override fun onStop() {
        super.onStop()
        detailEventBus.unsubscribe(this)
    }

    override fun onTransitionEnd() {
        if (!isContentVisible) {
            binding.container.animate().alpha(1f).apply { duration = 100 }.start()
            isContentVisible = true
        }
        if (!viewModel.text.get().isNullOrBlank()) {
            (parentFragment as? DetailFragment)?.onDataLoaded(song.id)
        }
    }

    override fun onTextSizeChanged() = viewModel.updateTextSize()

    override fun onShouldShowChordsChanged() = viewModel.refreshText()

    override fun onTranspositionChanged(songId: String, value: Int) {
        if (songId == this.song.id) {
            viewModel.transposition.set(viewModel.transposition.get() + value)
        }
    }

    override fun scroll(songId: String, speed: Int) {
        if (viewModel.song.id == songId) {
            smoothScrollHolder += (1 + speed) / 5f
            while (smoothScrollHolder > 1) {
                binding.scrollView.scrollY += smoothScrollHolder.roundToInt()
                smoothScrollHolder -= 1
            }
        }
    }
}