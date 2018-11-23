package com.pandulapeter.campfire.feature.shared.recycler

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pandulapeter.campfire.feature.shared.recycler.viewHolder.CollectionViewHolder
import com.pandulapeter.campfire.feature.shared.recycler.viewHolder.HeaderViewHolder
import com.pandulapeter.campfire.feature.shared.recycler.viewHolder.SongViewHolder
import com.pandulapeter.campfire.feature.shared.recycler.viewModel.CollectionItemViewModel
import com.pandulapeter.campfire.feature.shared.recycler.viewModel.HeaderItemViewModel
import com.pandulapeter.campfire.feature.shared.recycler.viewModel.ItemViewModel
import com.pandulapeter.campfire.feature.shared.recycler.viewModel.SongItemViewModel

class RecyclerAdapter : ListAdapter<ItemViewModel, RecyclerView.ViewHolder>(object : DiffUtil.ItemCallback<ItemViewModel>() {

    override fun areItemsTheSame(old: ItemViewModel, new: ItemViewModel) = when (old) {
        is HeaderItemViewModel -> when (new) {
            is HeaderItemViewModel -> old.title == new.title
            else -> false
        }
        is CollectionItemViewModel -> when (new) {
            is CollectionItemViewModel -> old.collection.id == new.collection.id
            else -> false
        }
        is SongItemViewModel -> when (new) {
            is SongItemViewModel -> old.song.id == new.song.id
            else -> false
        }
        else -> false
    }

    override fun areContentsTheSame(old: ItemViewModel, new: ItemViewModel) = old == new

}) {

    private var recyclerView: RecyclerView? = null
    var shouldScrollToTop = false
    var items = listOf<ItemViewModel>()
        set(newItems) {
            submitList(newItems)
            if (shouldScrollToTop) {
                recyclerView?.run { scrollToPosition(0) }
                shouldScrollToTop = false
            }
            field = newItems
        }
    var collectionClickListener: (position: Int, clickedView: View, image: View) -> Unit = { _, _, _ -> }
    var collectionBookmarkClickListener: ((position: Int) -> Unit)? = null
    var songClickListener: (position: Int, clickedView: View) -> Unit = { _, _ -> }
    var songDragTouchListener: ((position: Int) -> Unit)? = null
    var songPlaylistClickListener: ((position: Int) -> Unit)? = null
    var songDownloadClickListener: ((position: Int) -> Unit)? = null

    init {
        setHasStableIds(true)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is HeaderItemViewModel -> VIEW_TYPE_HEADER
        is CollectionItemViewModel -> VIEW_TYPE_COLLECTION
        is SongItemViewModel -> VIEW_TYPE_SONG
        else -> throw IllegalArgumentException("Unsupported item type.")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        VIEW_TYPE_HEADER -> HeaderViewHolder.create(parent)
        VIEW_TYPE_COLLECTION -> CollectionViewHolder.create(parent, collectionClickListener, collectionBookmarkClickListener)
        VIEW_TYPE_SONG -> SongViewHolder.create(parent, songClickListener, songDragTouchListener, songPlaylistClickListener, songDownloadClickListener)
        else -> throw IllegalArgumentException("Unsupported item type.")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        when (holder) {
            is HeaderViewHolder -> holder.bind(getItem(position) as HeaderItemViewModel)
            is CollectionViewHolder -> {
                (getItem(position) as CollectionItemViewModel).let {
                    payloads.forEach { payload ->
                        when (payload) {
                            is Payload.BookmarkedStateChanged -> it.collection.isBookmarked = payload.isBookmarked
                        }
                    }
                    holder.bind(getItem(position) as CollectionItemViewModel)
                }
            }
            is SongViewHolder -> {
                (getItem(position) as SongItemViewModel).let {
                    payloads.forEach { payload ->
                        when (payload) {
                            is Payload.DownloadStateChanged -> it.downloadState = payload.downloadState
                            is Payload.IsSongInAPlaylistChanged -> it.isOnAnyPlaylists = payload.isSongInAPlaylist
                        }
                    }
                    holder.bind(it, payloads.isEmpty())
                }
            }
        }
    }

    override fun getItemId(position: Int) = getItem(position)?.getItemId() ?: 0

    sealed class Payload {
        class BookmarkedStateChanged(val isBookmarked: Boolean) : Payload()
        class DownloadStateChanged(val downloadState: SongItemViewModel.DownloadState) : Payload()
        class IsSongInAPlaylistChanged(val isSongInAPlaylist: Boolean) : Payload()
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_COLLECTION = 1
        private const val VIEW_TYPE_SONG = 2
    }
}