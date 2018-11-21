package com.pandulapeter.campfire.feature.main.managePlaylists

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pandulapeter.campfire.PlaylistItemBinding
import com.pandulapeter.campfire.R

class ManagePlaylistListAdapter : ListAdapter<PlaylistViewModel, ManagePlaylistListAdapter.PlaylistViewHolder>(object : DiffUtil.ItemCallback<PlaylistViewModel>() {
    override fun areItemsTheSame(old: PlaylistViewModel, new: PlaylistViewModel) = old.playlist.id == new.playlist.id

    override fun areContentsTheSame(old: PlaylistViewModel, new: PlaylistViewModel) = old == new
}) {
    var items = listOf<PlaylistViewModel>()
        set(newItems) {
            field = newItems
            submitList(newItems)
        }

    private var itemClickListener: (position: Int) -> Unit = { }
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

    override fun getItemId(position: Int) = getItem(position).playlist.id.hashCode().toLong()

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