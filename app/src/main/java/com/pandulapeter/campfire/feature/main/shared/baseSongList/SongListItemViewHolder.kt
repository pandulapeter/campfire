package com.pandulapeter.campfire.feature.main.shared.baseSongList

import android.annotation.SuppressLint
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import com.pandulapeter.campfire.BR
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.ItemHeaderBinding
import com.pandulapeter.campfire.databinding.ItemSongBinding
import com.pandulapeter.campfire.util.obtainColor

sealed class SongListItemViewHolder<out B : ViewDataBinding, in VM : SongListItemViewModel>(protected val binding: B) :
    androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

    open fun bind(viewModel: VM, skipAnimations: Boolean = false) {
        binding.setVariable(BR.viewModel, viewModel)
        binding.executePendingBindings()
    }

    class HeaderViewHolder(parent: ViewGroup) : SongListItemViewHolder<ItemHeaderBinding, SongListItemViewModel.HeaderViewModel>(
        DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.item_header, parent, false)
    )

    class SongViewHolder(parent: ViewGroup) : SongListItemViewHolder<ItemSongBinding, SongListItemViewModel.SongViewModel>(
        DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.item_song, parent, false)
    ) {

        init {
            binding.loadingIndicator.run { indeterminateDrawable.setColorFilter(context.obtainColor(android.R.attr.textColorSecondary), PorterDuff.Mode.SRC_IN) }
        }

        override fun bind(viewModel: SongListItemViewModel.SongViewModel, skipAnimations: Boolean) {
            if (skipAnimations) {
                binding.playlistAction.setImageDrawable(null)
            }
            super.bind(viewModel, skipAnimations)
        }

        fun setItemClickListener(itemClickListener: (position: Int, clickedView: View) -> Unit) {
            binding.root.setOnClickListener {
                if (adapterPosition != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    itemClickListener(adapterPosition, it)
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        fun setDragHandleTouchListener(itemTouchListener: ((position: Int) -> Unit)?) {
            if (itemTouchListener != null) {
                binding.dragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN && adapterPosition != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                        itemTouchListener(adapterPosition)
                    }
                    false
                }
            }
        }

        fun setPlaylistActionClickListener(itemClickListener: ((position: Int) -> Unit)?) = itemClickListener?.let {
            binding.playlistAction.setOnClickListener {
                if (adapterPosition != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    itemClickListener(adapterPosition)
                }
            }
        }

        fun setDownloadActionClickListener(itemClickListener: ((position: Int) -> Unit)?) = itemClickListener?.let {
            binding.downloadActionSwitcher.setOnClickListener {
                if (binding.downloadActionSwitcher.displayedChild == 1 && adapterPosition != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    itemClickListener(adapterPosition)
                }
            }
        }
    }
}