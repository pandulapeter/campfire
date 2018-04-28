package com.pandulapeter.campfire.feature.home.collections

import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup

class CollectionListAdapter : RecyclerView.Adapter<CollectionListItemViewHolder<*, *>>() {

    companion object {
        private const val VIEW_TYPE_COLLECTION = 0
        private const val VIEW_TYPE_HEADER = 1
    }

    private var recyclerView: RecyclerView? = null
    var shouldScrollToTop = false
    var items = listOf<CollectionListItemViewModel>()
        set(newItems) {
            val oldItems = items
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = oldItems.size

                override fun getNewListSize() = newItems.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val old = oldItems[oldItemPosition]
                    val new = newItems[newItemPosition]
                    return if (old is CollectionListItemViewModel.CollectionViewModel) {
                        if (new is CollectionListItemViewModel.CollectionViewModel) old.collection.id == new.collection.id else false
                    } else {
                        if (new is CollectionListItemViewModel.CollectionViewModel) false else (old as CollectionListItemViewModel.HeaderViewModel).title == (new as CollectionListItemViewModel.HeaderViewModel).title
                    }
                }

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) = oldItems[oldItemPosition] == newItems[newItemPosition]
            }).dispatchUpdatesTo(this@CollectionListAdapter)
            if (shouldScrollToTop) {
                recyclerView?.run { scrollToPosition(0) }
                shouldScrollToTop = false
            }
            field = newItems
        }
    var itemClickListener: (position: Int, clickedView: View) -> Unit = { _, _ -> }
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
        is CollectionListItemViewModel.CollectionViewModel -> VIEW_TYPE_COLLECTION
        is CollectionListItemViewModel.HeaderViewModel -> VIEW_TYPE_HEADER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        VIEW_TYPE_COLLECTION -> CollectionListItemViewHolder.CollectionViewHolder(parent).apply {
            setItemClickListener(itemClickListener)
            setSaveActionClickListener(bookmarkActionClickListener)
        }
        VIEW_TYPE_HEADER -> CollectionListItemViewHolder.HeaderViewHolder(parent)
        else -> throw IllegalArgumentException("Unsupported item type.")
    }


    override fun onBindViewHolder(holder: CollectionListItemViewHolder<*, *>, position: Int) = Unit

    override fun onBindViewHolder(holder: CollectionListItemViewHolder<*, *>, position: Int, payloads: List<Any>) {
        when (holder) {
            is CollectionListItemViewHolder.CollectionViewHolder -> {
                (items[position] as? CollectionListItemViewModel.CollectionViewModel)?.run {
                    payloads.forEach { payload ->
                        when (payload) {
                            is Payload.BookmarkedStateChanged -> collection.isBookmarked = payload.isBookmarked
                        }
                    }
                    holder.bind(this, payloads.isEmpty())
                }
            }
            is CollectionListItemViewHolder.HeaderViewHolder -> (items[position] as? CollectionListItemViewModel.HeaderViewModel)?.let { holder.bind(it) }
        }
    }

    override fun getItemCount() = items.size

    override fun getItemId(position: Int) = items[position].getItemId()

    sealed class Payload {
        class BookmarkedStateChanged(val isBookmarked: Boolean) : Payload()
    }
}