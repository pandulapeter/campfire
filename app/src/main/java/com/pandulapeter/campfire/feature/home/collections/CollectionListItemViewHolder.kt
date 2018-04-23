package com.pandulapeter.campfire.feature.home.collections

import android.databinding.DataBindingUtil
import android.databinding.ViewDataBinding
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.BR
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.ItemCollectionBinding
import com.pandulapeter.campfire.databinding.ItemHeaderBinding

sealed class CollectionListItemViewHolder<out B : ViewDataBinding, in VM : CollectionListItemViewModel>(protected val binding: B) : RecyclerView.ViewHolder(binding.root) {

    open fun bind(viewModel: VM, skipAnimations: Boolean = false) {
        binding.setVariable(BR.viewModel, viewModel)
        binding.executePendingBindings()
    }

    class HeaderViewHolder(parent: ViewGroup) : CollectionListItemViewHolder<ItemHeaderBinding, CollectionListItemViewModel.HeaderViewModel>(
        DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.item_header, parent, false)
    )

    class CollectionViewHolder(parent: ViewGroup) : CollectionListItemViewHolder<ItemCollectionBinding, CollectionListItemViewModel.CollectionViewModel>(
        DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.item_collection, parent, false)
    ) {

        fun setItemClickListener(itemClickListener: (position: Int, clickedView: View) -> Unit) {
            binding.root.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    itemClickListener(adapterPosition, binding.root)
                }
            }
        }

        fun setSaveActionClickListener(saveActionClickListener: ((position: Int) -> Unit)?) {
            binding.save.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    saveActionClickListener?.invoke(adapterPosition)
                }
            }
        }
    }
}