package com.pandulapeter.campfire.feature.home.shared

import android.annotation.SuppressLint
import android.databinding.DataBindingUtil
import android.graphics.PorterDuff
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.ItemSongBinding
import com.pandulapeter.campfire.util.color

class SongAdapter : RecyclerView.Adapter<SongAdapter.SongInfoViewHolder>() {

    private var recyclerView: RecyclerView? = null
    var shouldScrollToTop = false
    var items = listOf<SongViewModel>()
        set(newItems) {
            val oldItems = items
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = oldItems.size

                override fun getNewListSize() = newItems.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                    oldItems[oldItemPosition].song.id == newItems[newItemPosition].song.id

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) = oldItems[oldItemPosition] == newItems[newItemPosition]
            }).dispatchUpdatesTo(this@SongAdapter)
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

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongInfoViewHolder {
        val viewHolder = SongInfoViewHolder.create(parent)
        viewHolder.setItemClickListener(itemClickListener)
        viewHolder.setDragHandleTouchListener(dragHandleTouchListener)
        viewHolder.setPlaylistActionClickListener(playlistActionClickListener)
        viewHolder.setDownloadActionClickListener(downloadActionClickListener)
        return viewHolder
    }

    override fun onBindViewHolder(holder: SongInfoViewHolder, position: Int) = onBindViewHolder(holder, position, listOf())

    override fun onBindViewHolder(holder: SongInfoViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty()) {
            payloads.forEach { payload ->
                items[position].run {
                    when (payload) {
                        is Payload.DownloadStateChanged -> downloadState = payload.downloadState
                        is Payload.EditModeChanged -> shouldShowDragHandle = payload.shouldShowDragHandle
                        is Payload.IsSongInAPlaylistChanged -> isOnAnyPlaylists = payload.isSongInAPlaylist
                    }
                }
            }
        }
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size


    class SongInfoViewHolder(private val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.loadingIndicator.run { indeterminateDrawable.setColorFilter(context.color(R.color.white), PorterDuff.Mode.SRC_IN) }
        }

        fun bind(song: SongViewModel) {
            binding.viewModel = song
            binding.executePendingBindings()
        }

        fun setItemClickListener(itemClickListener: (position: Int, clickedView: View) -> Unit) {
            binding.root.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    itemClickListener(adapterPosition, it)
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        fun setDragHandleTouchListener(itemTouchListener: ((position: Int) -> Unit)?) {
            if (itemTouchListener != null) {
                binding.dragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN && adapterPosition != RecyclerView.NO_POSITION) {
                        itemTouchListener(adapterPosition)
                    }
                    false
                }
            }
        }

        fun setPlaylistActionClickListener(itemClickListener: ((position: Int) -> Unit)?) = itemClickListener?.let {
            binding.playlistAction.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    itemClickListener(adapterPosition)
                }
            }
        }

        fun setDownloadActionClickListener(itemClickListener: ((position: Int) -> Unit)?) = itemClickListener?.let {
            binding.downloadActionContainer.setOnClickListener {
                if (binding.downloadActionSwitcher.displayedChild == 1 && adapterPosition != RecyclerView.NO_POSITION) {
                    itemClickListener(adapterPosition)
                }
            }
        }

        companion object {
            fun create(parent: ViewGroup) = SongInfoViewHolder(DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.item_song, parent, false))
        }
    }

    sealed class Payload {
        class DownloadStateChanged(val downloadState: SongViewModel.DownloadState) : Payload()
        class EditModeChanged(val shouldShowDragHandle: Boolean) : Payload()
        class IsSongInAPlaylistChanged(val isSongInAPlaylist: Boolean) : Payload()
    }
}