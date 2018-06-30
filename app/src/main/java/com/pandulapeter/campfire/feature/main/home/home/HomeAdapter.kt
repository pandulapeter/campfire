package com.pandulapeter.campfire.feature.main.home.home

import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup

class HomeAdapter : RecyclerView.Adapter<HomeItemViewHolder<*, *>>() {

    companion object {
        private const val VIEW_TYPE_COLLECTION = 0
        private const val VIEW_TYPE_HEADER = 1
    }

    private var recyclerView: RecyclerView? = null
    var shouldScrollToTop = false
    var items = listOf<HomeItemViewModel>()
        set(newItems) {
            val oldItems = items
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = oldItems.size

                override fun getNewListSize() = newItems.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val old = oldItems[oldItemPosition]
                    val new = newItems[newItemPosition]
                    return if (old is HomeItemViewModel.CollectionViewModel) {
                        if (new is HomeItemViewModel.CollectionViewModel) old.collection.id == new.collection.id else false
                    } else {
                        if (new is HomeItemViewModel.CollectionViewModel) false else (old as HomeItemViewModel.HeaderViewModel).title == (new as HomeItemViewModel.HeaderViewModel).title
                    }
                }

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) = oldItems[oldItemPosition] == newItems[newItemPosition]
            }).dispatchUpdatesTo(this@HomeAdapter)
            if (shouldScrollToTop) {
                recyclerView?.run { scrollToPosition(0) }
                shouldScrollToTop = false
            }
            field = newItems
        }
    var itemClickListener: (position: Int, clickedView: View, image: View) -> Unit = { _, _, _ -> }
    var bookmarkActionClickListener: ((position: Int) -> Unit)? = null

    init {
        setHasStableIds(true)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is HomeItemViewModel.CollectionViewModel -> VIEW_TYPE_COLLECTION
        is HomeItemViewModel.HeaderViewModel -> VIEW_TYPE_HEADER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        VIEW_TYPE_COLLECTION -> HomeItemViewHolder.CollectionViewHolder(parent).apply {
            setItemClickListener(itemClickListener)
        }
        VIEW_TYPE_HEADER -> HomeItemViewHolder.HeaderViewHolder(parent)
        else -> throw IllegalArgumentException("Unsupported item type.")
    }


    override fun onBindViewHolder(holder: HomeItemViewHolder<*, *>, position: Int) = Unit

    override fun onBindViewHolder(holder: HomeItemViewHolder<*, *>, position: Int, payloads: List<Any>) {
        when (holder) {
            is HomeItemViewHolder.CollectionViewHolder -> {
                (items[position] as? HomeItemViewModel.CollectionViewModel)?.run {
                    payloads.forEach { payload ->
                        when (payload) {
                            is Payload.BookmarkedStateChanged -> collection.isBookmarked = payload.isBookmarked
                        }
                    }
                    holder.bind(this, payloads.isEmpty())
                }
            }
            is HomeItemViewHolder.HeaderViewHolder -> (items[position] as? HomeItemViewModel.HeaderViewModel)?.let { holder.bind(it) }
        }
    }

    override fun getItemCount() = items.size

    override fun getItemId(position: Int) = items[position].getItemId()

    sealed class Payload {
        class BookmarkedStateChanged(val isBookmarked: Boolean) : Payload()
    }
}