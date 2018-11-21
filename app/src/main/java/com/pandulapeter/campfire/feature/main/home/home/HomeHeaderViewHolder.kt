package com.pandulapeter.campfire.feature.main.home.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import com.pandulapeter.campfire.BR
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.ItemHomeHeaderBinding

sealed class HomeHeaderViewHolder<out B : ViewDataBinding>(protected val binding: B) : RecyclerView.ViewHolder(binding.root) {

    open fun bind(viewModel: HomeHeaderViewModel, skipAnimations: Boolean = false) {
        binding.setVariable(BR.viewModel, viewModel)
        binding.executePendingBindings()
    }

    class HeaderViewHolder(parent: ViewGroup) : HomeHeaderViewHolder<ItemHomeHeaderBinding>(
        DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.item_home_header, parent, false)
    )
}