package com.pandulapeter.campfire.feature.home.library

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.LibraryBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.detail.DetailActivity
import com.pandulapeter.campfire.feature.home.shared.SpacesItemDecoration
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged
import dagger.android.support.DaggerFragment
import javax.inject.Inject

/**
 * Displays the list of all available songs from the backend. The list is searchable and filterable
 * and contains headers. The list is also cached locally and automatically updated after a period or
 * manually using the pull-to-refresh gesture.
 *
 * Controlled by [LibraryViewModel].
 */
class LibraryFragment : DaggerFragment() {

    @Inject lateinit var songInfoRepository: SongInfoRepository
    private lateinit var binding: LibraryBinding
    private val viewModel by lazy { LibraryViewModel(songInfoRepository) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_library, container, false)
        binding.viewModel = viewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        context?.let { context ->
            // Initialize the list and pull-to-refresh functionality.
            binding.recyclerView.layoutManager = LinearLayoutManager(context)
            binding.recyclerView.addItemDecoration(SpacesItemDecoration(context.dimension(R.dimen.content_padding)))
            binding.swipeRefreshLayout.setOnRefreshListener { viewModel.update(true) }
            viewModel.isLoading.onPropertyChanged { binding.swipeRefreshLayout.isRefreshing = it }
            // Setup list item click listeners.
            viewModel.adapter.itemClickListener = { position ->
                viewModel.adapter.items[position].let { songInfoViewModel ->
                    startActivity(DetailActivity.getStartIntent(context, songInfoViewModel.songInfo.title, songInfoViewModel.songInfo.artist))
                }
            }
            viewModel.adapter.itemActionClickListener = { position ->
                viewModel.adapter.items[position].let { viewModel.downloadOrDeleteSong(it.songInfo) }
            }
            // Setup error handling.
            viewModel.shouldShowErrorSnackbar.onEventTriggered {
                Snackbar
                    .make(binding.coordinatorLayout, R.string.something_went_wrong, Snackbar.LENGTH_LONG)
                    .setAction(R.string.try_again, { viewModel.update(true) })
                    .show()
            }
        }
    }
}