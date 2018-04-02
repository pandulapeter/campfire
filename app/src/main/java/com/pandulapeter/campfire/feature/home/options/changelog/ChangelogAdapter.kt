package com.pandulapeter.campfire.feature.home.options.changelog

import android.databinding.DataBindingUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.ChangelogItem
import com.pandulapeter.campfire.databinding.ItemChangelogBinding

class ChangelogAdapter(private val items: List<ChangelogItem>) : RecyclerView.Adapter<ChangelogAdapter.ChangelogViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ChangelogViewHolder.create(parent)

    override fun onBindViewHolder(holder: ChangelogViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    class ChangelogViewHolder(private val binding: ItemChangelogBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(changelogItem: ChangelogItem) {
            binding.model = changelogItem
        }

        companion object {
            fun create(parent: ViewGroup) = ChangelogViewHolder(DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.item_changelog, parent, false))
        }
    }
}