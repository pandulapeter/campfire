package com.pandulapeter.campfire.feature.home.shared.songInfoList

import android.annotation.SuppressLint
import android.databinding.DataBindingUtil
import android.support.annotation.LayoutRes
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.SongInfoBinding
import com.pandulapeter.campfire.data.model.SongInfo
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.cancel
import kotlin.coroutines.experimental.CoroutineContext

/**
 * Custom [RecyclerView.Adapter] that handles a a list of [SongInfo] objects.
 */
class SongInfoListAdapter : RecyclerView.Adapter<SongInfoListAdapter.SongInfoViewHolder>() {
    private var coroutine: CoroutineContext? = null
    var items = listOf<SongInfoViewModel>()
        set(newItems) {
            if (field.isEmpty()) {
                if (newItems.isNotEmpty()) {
                    field = newItems
                    notifyDataSetChanged()
                }
            } else {
                coroutine?.cancel()
                coroutine = async(UI) {
                    val oldItems = items
                    async(CommonPool) {
                        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                            override fun getOldListSize() = oldItems.size

                            override fun getNewListSize() = newItems.size

                            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                                oldItems[oldItemPosition].songInfo.id == newItems[newItemPosition].songInfo.id

                            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) = oldItems[oldItemPosition] == newItems[newItemPosition]
                        })
                    }.await().dispatchUpdatesTo(this@SongInfoListAdapter)
                    field = newItems
                }
            }
        }

    var itemClickListener: (position: Int) -> Unit = { _ -> }
    var dragHandleTouchListener: ((position: Int) -> Unit)? = null
    var playlistActionClickListener: ((position: Int) -> Unit)? = null
    var downloadActionClickListener: ((position: Int) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, @LayoutRes viewType: Int): SongInfoViewHolder {
        val viewHolder = SongInfoViewHolder.create(parent)
        viewHolder.setItemClickListener(itemClickListener)
        viewHolder.setDragHandleTouchListener(dragHandleTouchListener)
        viewHolder.setPlaylistActionClickListener(playlistActionClickListener)
        viewHolder.setDownloadActionClickListener(downloadActionClickListener)
        return viewHolder
    }

    override fun onBindViewHolder(holder: SongInfoViewHolder, position: Int) = onBindViewHolder(holder, position, null)

    override fun onBindViewHolder(holder: SongInfoViewHolder, position: Int, payloads: List<Any>?) {
        if (payloads?.isNotEmpty() == true) {
            payloads.forEach { payload ->
                items[position].run {
                    when (payload) {
                        Payload.SONG_DOWNLOADED -> isSongDownloaded = true
                        Payload.SONG_DOWNLOAD_DELETED -> isSongDownloaded = false
                        Payload.DOWNLOAD_STARTED -> isSongLoading = true
                        Payload.DOWNLOAD_SUCCESSFUL -> {
                            isSongDownloaded = true
                            shouldShowDownloadButton = false
                            alertText = null
                        }
                        Payload.DOWNLOAD_FAILED -> isSongLoading = false
                        Payload.EDIT_MODE_OPEN -> shouldShowDragHandle = true
                        Payload.EDIT_MODE_CLOSE -> shouldShowDragHandle = false
                        Payload.SONG_IS_IN_A_PLAYLIST -> isSongOnAnyPlaylist = true
                        Payload.SONG_IS_NOT_IN_A_PLAYLISTS -> isSongOnAnyPlaylist = false
                    }
                }
            }
        }
        holder.binding.viewModel = items[position]
        holder.binding.executePendingBindings()
    }

    override fun getItemCount() = items.size

    class SongInfoViewHolder(val binding: SongInfoBinding) : RecyclerView.ViewHolder(binding.root) {

        fun setItemClickListener(itemClickListener: (position: Int) -> Unit) {
            binding.root.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    itemClickListener(adapterPosition)
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        fun setDragHandleTouchListener(itemTouchListener: ((position: Int) -> Unit)?) {
            if (itemTouchListener != null) {
                //TODO: Fix Lint warning.
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
            binding.downloadActionSwitcher.setOnClickListener {
                if (binding.downloadActionSwitcher.displayedChild == 1 && adapterPosition != RecyclerView.NO_POSITION) {
                    itemClickListener(adapterPosition)
                }
            }
        }

        companion object {
            fun create(parent: ViewGroup): SongInfoViewHolder =
                SongInfoViewHolder(DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.item_song_info, parent, false))
        }
    }

    enum class Payload {
        SONG_DOWNLOADED,
        SONG_DOWNLOAD_DELETED,
        DOWNLOAD_STARTED,
        DOWNLOAD_SUCCESSFUL,
        DOWNLOAD_FAILED,
        EDIT_MODE_OPEN,
        EDIT_MODE_CLOSE,
        SONG_IS_IN_A_PLAYLIST,
        SONG_IS_NOT_IN_A_PLAYLISTS
    }
}