package com.pandulapeter.campfire.feature.home.options.changelog

import android.databinding.DataBindingUtil
import android.os.Build
import android.support.v7.widget.RecyclerView
import android.text.Html
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
            binding.title.setText(changelogItem.versionName)
            binding.description.text = itemView.context.getString(changelogItem.description).let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT)
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(it)
                }
            }
            binding.executePendingBindings()
        }

        companion object {
            fun create(parent: ViewGroup) = ChangelogViewHolder(DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.item_changelog, parent, false))
        }
    }
}