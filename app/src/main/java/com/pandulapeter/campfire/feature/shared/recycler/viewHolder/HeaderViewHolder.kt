package com.pandulapeter.campfire.feature.shared.recycler.viewHolder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.ItemListHeaderBinding
import com.pandulapeter.campfire.feature.shared.recycler.viewModel.HeaderItemViewModel

class HeaderViewHolder(private val binding: ItemListHeaderBinding) : RecyclerView.ViewHolder(binding.root) {

    fun bind(viewModel: HeaderItemViewModel) {
        binding.viewModel = viewModel
        binding.executePendingBindings()
    }

    companion object {

        fun create(parent: ViewGroup) = HeaderViewHolder(
            DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.item_list_header, parent, false)
        )
    }
}