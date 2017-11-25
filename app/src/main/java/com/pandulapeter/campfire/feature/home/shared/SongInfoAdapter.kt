package com.pandulapeter.campfire.feature.home.shared

import android.databinding.DataBindingUtil
import android.support.annotation.LayoutRes
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
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
            val oldItems = items
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = oldItems.size

                override fun getNewListSize() = newItems.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int)
                    = oldItems[oldItemPosition].songInfo.id == newItems[newItemPosition].songInfo.id

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int)
                    = oldItems[oldItemPosition] == newItems[newItemPosition]
            }).dispatchUpdatesTo(this)
            field = newItems
        }
    var itemClickListener: (position: Int) -> Unit = { _ -> }
    var itemActionClickListener: (position: Int) -> Unit = { _ -> }

    override fun onCreateViewHolder(parent: ViewGroup, @LayoutRes viewType: Int): SongInfoViewHolder {
        val viewHolder = SongInfoViewHolder.create(parent)
        viewHolder.setItemClickListener(itemClickListener)
        viewHolder.setItemActionClickListener(itemActionClickListener)
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

        fun setItemActionClickListener(itemClickListener: (position: Int) -> Unit) {
            binding.action.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    itemClickListener(adapterPosition)
                }
            }
        }

        companion object {
            fun create(parent: ViewGroup): SongInfoViewHolder =
                SongInfoViewHolder(DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.item_song_info, parent, false))
        }
    }
}