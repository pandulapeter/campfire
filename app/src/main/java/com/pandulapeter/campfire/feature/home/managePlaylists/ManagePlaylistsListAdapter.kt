package com.pandulapeter.campfire.feature.home.managePlaylists

import android.annotation.SuppressLint
import android.databinding.DataBindingUtil
import android.support.annotation.LayoutRes
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import com.pandulapeter.campfire.PlaylistItemBinding
import com.pandulapeter.campfire.R
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.cancel
import kotlin.coroutines.experimental.CoroutineContext

class ManagePlaylistsListAdapter : RecyclerView.Adapter<ManagePlaylistsListAdapter.PlaylistViewHolder>() {
    private var coroutine: CoroutineContext? = null
    var items = listOf<PlaylistViewModel>()
        set(newItems) {
            coroutine?.cancel()
            coroutine = async(UI) {
                val oldItems = items
                async(CommonPool) {
                    DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                        override fun getOldListSize() = oldItems.size

                        override fun getNewListSize() = newItems.size

                        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                            oldItems[oldItemPosition].playlist.id == newItems[newItemPosition].playlist.id

                        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) = oldItems[oldItemPosition] == newItems[newItemPosition]
                    })
                }.await().dispatchUpdatesTo(this@ManagePlaylistsListAdapter)
                field = newItems
            }
        }

    var itemClickListener: (position: Int) -> Unit = { _ -> }
    var dragHandleTouchListener: ((position: Int) -> Unit)? = null

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, @LayoutRes viewType: Int) = PlaylistViewHolder.create(parent).apply {
        setItemClickListener(itemClickListener)
        setDragHandleTouchListener(dragHandleTouchListener)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) = onBindViewHolder(holder, position, listOf())

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int, payloads: List<Any>) {
        holder.binding.viewModel = items[position]
        holder.binding.executePendingBindings()
    }

    override fun getItemCount() = items.size

    override fun getItemId(position: Int) = items[position].playlist.id.hashCode().toLong()

    class PlaylistViewHolder(val binding: PlaylistItemBinding) : RecyclerView.ViewHolder(binding.root) {

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
                binding.dragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN && adapterPosition != RecyclerView.NO_POSITION) {
                        itemTouchListener(adapterPosition)
                    }
                    false
                }
            }
        }

        companion object {
            fun create(parent: ViewGroup): PlaylistViewHolder =
                PlaylistViewHolder(DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.item_playlist, parent, false))
        }
    }
}