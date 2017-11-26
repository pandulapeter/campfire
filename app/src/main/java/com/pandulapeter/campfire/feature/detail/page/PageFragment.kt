package com.pandulapeter.campfire.feature.detail.page

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.PageBinding
import com.pandulapeter.campfire.R
import dagger.android.support.DaggerFragment

/**
 * Displays lyrics and chords to a song.
 *
 * Controlled by [PageViewModel].
 */
class PageFragment : DaggerFragment() {

    private lateinit var binding: PageBinding
    private val viewModel by lazy { PageViewModel(arguments.songId) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_page, container, false)
        binding.viewModel = viewModel
        return binding.root
    }

    companion object {
        private const val SONG_ID = "song_id"
        private val Bundle?.songId
            get() = this?.getString(SONG_ID) ?: ""

        fun newInstance(songId: String) = PageFragment().apply {
            arguments = Bundle().apply {
                putString(SONG_ID, songId)
            }
        }
    }
}