package com.pandulapeter.campfire.feature.home.shared.songList

import android.annotation.SuppressLint
import android.databinding.ViewDataBinding
import android.graphics.PorterDuff
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.BR
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.ItemHeaderBinding
import com.pandulapeter.campfire.databinding.ItemSongBinding
import com.pandulapeter.campfire.util.color

sealed class SongListViewHolder<out B : ViewDataBinding, in VM : SongListItemViewModel>(protected val binding: B) : RecyclerView.ViewHolder(binding.root) {

    fun bind(viewModel: VM) {
        binding.setVariable(BR.viewModel, viewModel)
        binding.executePendingBindings()
    }

    class SongViewHolder(parent: ViewGroup) :
        SongListViewHolder<ItemSongBinding, SongListItemViewModel.SongViewModel>(ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)) {

        init {
            binding.loadingIndicator.run { indeterminateDrawable.setColorFilter(context.color(R.color.white), PorterDuff.Mode.SRC_IN) }
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
    }

    class HeaderViewHolder(parent: ViewGroup) :
        SongListViewHolder<ItemHeaderBinding, SongListItemViewModel.HeaderViewModel>(ItemHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
}