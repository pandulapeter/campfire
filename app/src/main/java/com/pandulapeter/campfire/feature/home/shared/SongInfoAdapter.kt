package com.pandulapeter.campfire.feature.home.shared

import android.databinding.DataBindingUtil
import android.databinding.ViewDataBinding
import android.support.annotation.LayoutRes
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.SongInfoBinding
import com.pandulapeter.campfire.data.model.SongInfo

/**
 * Custom [RecyclerView.Adapter] that handles a a list of [SongInfo] objects.
 */
class SongInfoAdapter : RecyclerView.Adapter<SongInfoAdapter.SongInfoViewHolder<SongInfoBinding>>() {

    var songInfoList = listOf<SongInfo>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, @LayoutRes viewType: Int): SongInfoViewHolder<SongInfoBinding> = SongInfoViewHolder.create(parent)

    override fun onBindViewHolder(holder: SongInfoViewHolder<SongInfoBinding>?, position: Int) = Unit

    override fun onBindViewHolder(holder: SongInfoViewHolder<SongInfoBinding>, position: Int, payloads: List<Any>?) {
        holder.binding.songInfo = songInfoList[position]
        holder.binding.executePendingBindings()
    }

    override fun getItemCount() = songInfoList.size

    class SongInfoViewHolder<out T : ViewDataBinding> private constructor(val binding: T) : RecyclerView.ViewHolder(binding.root) {

        companion object {
            fun <T : ViewDataBinding> create(parent: ViewGroup): SongInfoViewHolder<T> =
                SongInfoViewHolder(DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.item_song_info, parent, false))
        }
    }
}