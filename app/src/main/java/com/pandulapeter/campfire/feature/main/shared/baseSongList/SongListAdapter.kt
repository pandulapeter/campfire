package com.pandulapeter.campfire.feature.main.shared.baseSongList

import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView

class SongListAdapter(private val songItemHeight: Int, private val headerItemHeight: Int) : RecyclerView.Adapter<SongListItemViewHolder<*, *>>(),
    FastScrollRecyclerView.SectionedAdapter,
    FastScrollRecyclerView.MeasurableAdapter<SongListItemViewHolder<*, *>> {

    companion object {
        private const val VIEW_TYPE_SONG = 0
        private const val VIEW_TYPE_HEADER = 1
    }

    private var recyclerView: RecyclerView? = null
    var shouldScrollToTop = false
    var items = listOf<SongListItemViewModel>()
        set(newItems) {
            val oldItems = items
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = oldItems.size

                override fun getNewListSize() = newItems.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val old = oldItems[oldItemPosition]
                    val new = newItems[newItemPosition]
                    return if (old is SongListItemViewModel.SongViewModel) {
                        if (new is SongListItemViewModel.SongViewModel) old.song.id == new.song.id else false
                    } else {
                        if (new is SongListItemViewModel.SongViewModel) false else (old as SongListItemViewModel.HeaderViewModel).title == (new as SongListItemViewModel.HeaderViewModel).title
                    }
                }

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) = oldItems[oldItemPosition] == newItems[newItemPosition]
            }).dispatchUpdatesTo(this@SongListAdapter)
            if (shouldScrollToTop) {
                recyclerView?.run { scrollToPosition(0) }
                shouldScrollToTop = false
            }
            field = newItems
        }
    var itemClickListener: (position: Int, clickedView: View) -> Unit = { _, _ -> }
    var dragHandleTouchListener: ((position: Int) -> Unit)? = null
    var playlistActionClickListener: ((position: Int) -> Unit)? = null
    var downloadActionClickListener: ((position: Int) -> Unit)? = null

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
        is SongListItemViewModel.SongViewModel -> VIEW_TYPE_SONG
        is SongListItemViewModel.HeaderViewModel -> VIEW_TYPE_HEADER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        VIEW_TYPE_SONG -> SongListItemViewHolder.SongViewHolder(parent).apply {
            setItemClickListener(itemClickListener)
            setDragHandleTouchListener(dragHandleTouchListener)
            setPlaylistActionClickListener(playlistActionClickListener)
            setDownloadActionClickListener(downloadActionClickListener)
        }
        VIEW_TYPE_HEADER -> SongListItemViewHolder.HeaderViewHolder(parent)
        else -> throw IllegalArgumentException("Unsupported item type.")
    }


    override fun onBindViewHolder(holder: SongListItemViewHolder<*, *>, position: Int) = Unit

    override fun onBindViewHolder(holder: SongListItemViewHolder<*, *>, position: Int, payloads: List<Any>) {
        when (holder) {
            is SongListItemViewHolder.SongViewHolder -> {
                (items[position] as? SongListItemViewModel.SongViewModel)?.run {
                    payloads.forEach { payload ->
                        when (payload) {
                            is Payload.DownloadStateChanged -> downloadState = payload.downloadState
                            is Payload.EditModeChanged -> shouldShowDragHandle = payload.shouldShowDragHandle
                            is Payload.IsSongInAPlaylistChanged -> isOnAnyPlaylists = payload.isSongInAPlaylist
                        }
                    }
                    holder.bind(this, payloads.isEmpty())
                }
            }
            is SongListItemViewHolder.HeaderViewHolder -> (items[position] as? SongListItemViewModel.HeaderViewModel)?.let { holder.bind(it) }
        }
    }

    override fun getViewTypeHeight(recyclerView: RecyclerView?, viewHolder: SongListItemViewHolder<*, *>?, viewType: Int) =
        if (viewType == VIEW_TYPE_SONG) songItemHeight else headerItemHeight

    override fun getItemCount() = items.size

    override fun getItemId(position: Int) = items[position].getItemId()

    override fun getSectionName(position: Int) = items[position].let {
        when (it) {
            is SongListItemViewModel.HeaderViewModel -> it.title[0].toString()
            is SongListItemViewModel.SongViewModel -> it.song.title[0].toString()
        }
    }

    sealed class Payload {
        class DownloadStateChanged(val downloadState: SongListItemViewModel.SongViewModel.DownloadState) : Payload()
        class EditModeChanged(val shouldShowDragHandle: Boolean) : Payload()
        class IsSongInAPlaylistChanged(val isSongInAPlaylist: Boolean) : Payload()
    }
}