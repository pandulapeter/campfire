package com.pandulapeter.campfire.feature.shared.recycler.viewHolder

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.ItemListSongBinding
import com.pandulapeter.campfire.feature.shared.recycler.viewModel.SongItemViewModel
import com.pandulapeter.campfire.util.obtainColor

class SongViewHolder(
    private val binding: ItemListSongBinding,
    itemClickListener: (position: Int, clickedView: View) -> Unit,
    itemTouchListener: ((position: Int) -> Unit)?,
    playlistActionListener: ((position: Int) -> Unit)?,
    downloadActionListener: ((position: Int) -> Unit)?
) : RecyclerView.ViewHolder(binding.root) {

    init {
        binding.loadingIndicator.run { indeterminateDrawable.setColorFilter(context.obtainColor(android.R.attr.textColorSecondary), PorterDuff.Mode.SRC_IN) }
        binding.root.setOnClickListener {
            if (adapterPosition != RecyclerView.NO_POSITION) {
                itemClickListener(adapterPosition, it)
            }
        }
        if (itemTouchListener != null)
            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN && adapterPosition != RecyclerView.NO_POSITION) {
                    itemTouchListener(adapterPosition)
                }
                false
            }
        if (playlistActionListener != null) {
            binding.playlistAction.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    playlistActionListener(adapterPosition)
                }
            }
        }
        if (downloadActionListener != null) {
            binding.downloadActionSwitcher.setOnClickListener {
                if (binding.downloadActionSwitcher.displayedChild == 1 && adapterPosition != RecyclerView.NO_POSITION) {
                    downloadActionListener(adapterPosition)
                }
            }
        }
    }

    fun bind(viewModel: SongItemViewModel, skipAnimations: Boolean = false) {
        if (skipAnimations) {
            binding.playlistAction.setImageDrawable(null)
        }
        binding.viewModel = viewModel
        binding.executePendingBindings()
    }

    companion object {

        fun create(
            parent: ViewGroup,
            itemClickListener: (position: Int, clickedView: View) -> Unit,
            itemTouchListener: ((position: Int) -> Unit)?,
            playlistActionListener: ((position: Int) -> Unit)?,
            downloadActionListener: ((position: Int) -> Unit)?
        ) = SongViewHolder(
            DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.item_list_song, parent, false),
            itemClickListener,
            itemTouchListener,
            playlistActionListener,
            downloadActionListener
        )
    }
}