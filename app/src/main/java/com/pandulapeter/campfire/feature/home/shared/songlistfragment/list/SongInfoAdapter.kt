package com.pandulapeter.campfire.feature.home.shared.songlistfragment.list

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

/**
 * Custom [RecyclerView.Adapter] that handles a a list of [SongInfo] objects.
 */
class SongInfoAdapter : RecyclerView.Adapter<SongInfoAdapter.SongInfoViewHolder>() {

    var items = listOf<SongInfoViewModel>()
        set(newItems) {
            if (field != newItems) {
                val oldItems = items
                DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                    override fun getOldListSize() = oldItems.size

                    override fun getNewListSize() = newItems.size

                    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int)
                        = oldItems[oldItemPosition].songInfo.id == newItems[newItemPosition].songInfo.id

                    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int)
                        = oldItems[oldItemPosition] == newItems[newItemPosition]
                }).dispatchUpdatesTo(this@SongInfoAdapter)
                field = newItems
            }
        }

    var itemClickListener: (position: Int) -> Unit = { _ -> }
    var itemActionClickListener: ((position: Int) -> Unit)? = null
    var itemActionTouchListener: ((position: Int) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, @LayoutRes viewType: Int): SongInfoViewHolder {
        val viewHolder = SongInfoViewHolder.create(parent)
        viewHolder.setItemClickListener(itemClickListener)
        viewHolder.setItemActionClickListener(itemActionClickListener)
        viewHolder.setItemActionTouchListener(itemActionTouchListener)
        return viewHolder
    }

    override fun onBindViewHolder(holder: SongInfoViewHolder?, position: Int) = Unit

    override fun onBindViewHolder(holder: SongInfoViewHolder, position: Int, payloads: List<Any>?) {
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

        fun setItemActionClickListener(itemClickListener: ((position: Int) -> Unit)?) {
            if (itemClickListener == null) {
                binding.action.isClickable = false
                binding.action.background = null
            } else {
                binding.root.setOnLongClickListener {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        itemClickListener(adapterPosition)
                        true
                    } else false
                }
                binding.action.setOnClickListener {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        itemClickListener(adapterPosition)
                    }
                }
            }
        }

        fun setItemActionTouchListener(itemTouchListener: ((position: Int) -> Unit)?) {
            if (itemTouchListener != null) {
                //TODO: Fix Lint warning.
                binding.action.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN && adapterPosition != RecyclerView.NO_POSITION) {
                        itemTouchListener(adapterPosition)
                    }
                    false
                }
            }
        }

        companion object {
            fun create(parent: ViewGroup): SongInfoViewHolder =
                SongInfoViewHolder(DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.item_song_info, parent, false))
        }
    }
}