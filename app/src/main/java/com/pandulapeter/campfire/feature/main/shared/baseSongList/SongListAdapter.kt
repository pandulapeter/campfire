package com.pandulapeter.campfire.feature.main.shared.baseSongList

import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.feature.main.songs.fastScroll.FastScrollRecyclerView

class SongListAdapter : ListAdapter<SongListItemViewModel, SongListItemViewHolder<*, *>>(object : DiffUtil.ItemCallback<SongListItemViewModel>() {

    override fun areItemsTheSame(old: SongListItemViewModel, new: SongListItemViewModel) = if (old is SongListItemViewModel.SongViewModel) {
        if (new is SongListItemViewModel.SongViewModel) old.song.id == new.song.id else false
    } else {
        if (new is SongListItemViewModel.SongViewModel) false else (old as SongListItemViewModel.HeaderViewModel).title == (new as SongListItemViewModel.HeaderViewModel).title
    }

    override fun areContentsTheSame(old: SongListItemViewModel, new: SongListItemViewModel) = old == new

}), FastScrollRecyclerView.SectionedAdapter, FastScrollRecyclerView.MeasurableAdapter<SongListItemViewHolder<*, *>> {

    companion object {
        private const val VIEW_TYPE_SONG = 0
        private const val VIEW_TYPE_HEADER = 1
    }

    private var recyclerView: RecyclerView? = null
    private val headerItemHeight by lazy {
        recyclerView?.run {
            onCreateViewHolder(this, VIEW_TYPE_HEADER).itemView.let {
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
    var shouldScrollToTop = false
    var itemTitleCallback: (Int) -> String = { "" }
    var items = listOf<SongListItemViewModel>()
        set(newItems) {
            submitList(newItems)
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

    override fun getItemViewType(position: Int) = when (getItem(position)) {
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
                (getItem(position) as? SongListItemViewModel.SongViewModel)?.run {
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
            is SongListItemViewHolder.HeaderViewHolder -> (getItem(position) as? SongListItemViewModel.HeaderViewModel)?.let { holder.bind(it) }
        }
    }

    override fun getViewTypeHeight(recyclerView: RecyclerView?, viewHolder: SongListItemViewHolder<*, *>?, viewType: Int) =
        if (viewType == VIEW_TYPE_SONG) songItemHeight else headerItemHeight

    override fun getItemId(position: Int) = getItem(position).getItemId()

    override fun getSectionName(position: Int) = itemTitleCallback(position)

    sealed class Payload {
        class DownloadStateChanged(val downloadState: SongListItemViewModel.SongViewModel.DownloadState) : Payload()
        class EditModeChanged(val shouldShowDragHandle: Boolean) : Payload()
        class IsSongInAPlaylistChanged(val isSongInAPlaylist: Boolean) : Payload()
    }
}