package com.pandulapeter.campfire.feature.main.shared.recycler

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.feature.main.shared.recycler.viewHolder.CollectionViewHolder
import com.pandulapeter.campfire.feature.main.shared.recycler.viewHolder.HeaderViewHolder
import com.pandulapeter.campfire.feature.main.shared.recycler.viewHolder.SongViewHolder
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.CollectionItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.HeaderItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.ItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.SongItemViewModel
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView

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

}), FastScrollRecyclerView.SectionedAdapter, FastScrollRecyclerView.MeasurableAdapter<RecyclerView.ViewHolder> {

    var shouldScrollToTop = false
    var items = listOf<ItemViewModel>()
        set(newItems) {
            if (shouldScrollToTop) {
                //TODO: Does not seem to work.
                recyclerView?.scrollToPosition(0)
                shouldScrollToTop = false
            }
            field = newItems
            submitList(newItems)
        }
    var collectionClickListener: (collection: Collection, clickedView: View, image: View) -> Unit = { _, _, _ -> }
    var collectionBookmarkClickListener: (collection: Collection, position: Int) -> Unit = { _, _ -> }
    var songClickListener: (song: Song, position: Int, clickedView: View) -> Unit = { _, _, _ -> }
    var songDragTouchListener: (position: Int) -> Unit = {}
    var songPlaylistClickListener: (song: Song) -> Unit = { }
    var songDownloadClickListener: (song: Song) -> Unit = { }
    var itemTitleCallback: (item: ItemViewModel) -> String = { "" }
    private var recyclerView: RecyclerView? = null
    private val headerItemHeight by lazy {
        recyclerView?.run {
            onCreateViewHolder(this, VIEW_TYPE_HEADER).itemView.let {
                it.measure(measuredWidth, measuredHeight)
                it.measuredHeight
            }
        } ?: 0
    }
    private val collectionItemHeight by lazy {
        recyclerView?.run {
            onCreateViewHolder(this, VIEW_TYPE_COLLECTION).itemView.let {
                it.measure(measuredWidth, measuredHeight)
                it.measuredHeight
            }
        } ?: 0
    }
    private val songItemHeight by lazy {
        recyclerView?.run {
            onCreateViewHolder(this, VIEW_TYPE_SONG).itemView.let {
                it.measure(measuredWidth, measuredHeight)
                it.measuredHeight
            }
        } ?: 0
    }

    init {
        setHasStableIds(true)
    }

    public override fun getItem(position: Int): ItemViewModel {
        return super.getItem(position)
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
        VIEW_TYPE_COLLECTION -> CollectionViewHolder.create(
            parent = parent,
            itemClickListener = { position, clickedView, image -> collectionClickListener((getItem(position) as CollectionItemViewModel).collection, clickedView, image) },
            bookmarkClickListener = { position -> collectionBookmarkClickListener((getItem(position) as CollectionItemViewModel).collection, position) }
        )
        VIEW_TYPE_SONG -> SongViewHolder.create(
            parent = parent,
            itemClickListener = { position, clickedView -> songClickListener((getItem(position) as SongItemViewModel).song, position, clickedView) },
            itemTouchListener = songDragTouchListener,
            playlistClickListener = { position -> songPlaylistClickListener((getItem(position) as SongItemViewModel).song) },
            downloadClickListener = { position -> songDownloadClickListener((getItem(position) as SongItemViewModel).song) }
        )
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
                            is Payload.EditModeChanged -> it.shouldShowDragHandle = payload.shouldShowDragHandle
                        }
                    }
                    holder.bind(it, payloads.isEmpty())
                }
            }
        }
    }

    override fun getViewTypeHeight(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, viewType: Int) =
        when (viewType) {
            VIEW_TYPE_HEADER -> headerItemHeight
            VIEW_TYPE_COLLECTION -> collectionItemHeight
            VIEW_TYPE_SONG -> songItemHeight
            else -> 0
        }

    override fun getSectionName(position: Int) = itemTitleCallback(getItem(position))

    override fun getItemId(position: Int) = getItem(position).getItemId()

    sealed class Payload {
        class BookmarkedStateChanged(val isBookmarked: Boolean) : Payload()
        class DownloadStateChanged(val downloadState: SongItemViewModel.DownloadState) : Payload()
        class IsSongInAPlaylistChanged(val isSongInAPlaylist: Boolean) : Payload()
        class EditModeChanged(val shouldShowDragHandle: Boolean) : Payload()
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_COLLECTION = 1
        private const val VIEW_TYPE_SONG = 2
    }
}