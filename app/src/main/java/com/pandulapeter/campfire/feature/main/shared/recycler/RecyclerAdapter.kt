package com.pandulapeter.campfire.feature.main.shared.recycler

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
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
import com.pandulapeter.campfire.util.UI
import com.pandulapeter.campfire.util.WORKER
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class RecyclerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>(), FastScrollRecyclerView.SectionedAdapter,
    FastScrollRecyclerView.MeasurableAdapter<RecyclerView.ViewHolder> {

    private var coroutineContext: CoroutineContext? = null
    var shouldScrollToTop = false
    var items = listOf<ItemViewModel>()
        set(newItems) {
            coroutineContext?.cancel()
            coroutineContext = GlobalScope.launch(UI) {
                withContext(WORKER) {
                    DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                            val old = field[oldItemPosition]
                            val new = newItems[newItemPosition]
                            return when (old) {
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
                        }

                        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) = field[oldItemPosition] == newItems[newItemPosition]

                        override fun getOldListSize() = field.size

                        override fun getNewListSize() = newItems.size

                    })
                }.dispatchUpdatesTo(this@RecyclerAdapter)
                if (shouldScrollToTop) {
                    recyclerView?.scrollToPosition(0)
                    shouldScrollToTop = false
                }
                field = newItems
            }
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

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is HeaderItemViewModel -> VIEW_TYPE_HEADER
        is CollectionItemViewModel -> VIEW_TYPE_COLLECTION
        is SongItemViewModel -> VIEW_TYPE_SONG
        else -> throw IllegalArgumentException("Unsupported item type.")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        VIEW_TYPE_HEADER -> HeaderViewHolder.create(parent)
        VIEW_TYPE_COLLECTION -> CollectionViewHolder.create(
            parent = parent,
            itemClickListener = { position, clickedView, image -> collectionClickListener((items[position] as CollectionItemViewModel).collection, clickedView, image) },
            bookmarkClickListener = { position -> collectionBookmarkClickListener((items[position] as CollectionItemViewModel).collection, position) }
        )
        VIEW_TYPE_SONG -> SongViewHolder.create(
            parent = parent,
            itemClickListener = { position, clickedView -> songClickListener((items[position] as SongItemViewModel).song, position, clickedView) },
            itemTouchListener = songDragTouchListener,
            playlistClickListener = { position -> songPlaylistClickListener((items[position] as SongItemViewModel).song) },
            downloadClickListener = { position -> songDownloadClickListener((items[position] as SongItemViewModel).song) }
        )
        else -> throw IllegalArgumentException("Unsupported item type.")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        when (holder) {
            is HeaderViewHolder -> holder.bind(items[position] as HeaderItemViewModel)
            is CollectionViewHolder -> {
                (items[position] as CollectionItemViewModel).let {
                    payloads.forEach { payload ->
                        when (payload) {
                            is Payload.BookmarkedStateChanged -> it.collection.isBookmarked = payload.isBookmarked
                        }
                    }
                    holder.bind(items[position] as CollectionItemViewModel)
                }
            }
            is SongViewHolder -> {
                (items[position] as SongItemViewModel).let {
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

    override fun getItemCount() = items.size

    override fun getViewTypeHeight(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, viewType: Int) =
        when (viewType) {
            VIEW_TYPE_HEADER -> headerItemHeight
            VIEW_TYPE_COLLECTION -> collectionItemHeight
            VIEW_TYPE_SONG -> songItemHeight
            else -> 0
        }

    override fun getSectionName(position: Int) = itemTitleCallback(items[position])

    override fun getItemId(position: Int) = items[position].getItemId()

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