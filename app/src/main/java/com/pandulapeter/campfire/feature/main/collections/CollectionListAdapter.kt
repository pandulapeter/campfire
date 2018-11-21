package com.pandulapeter.campfire.feature.main.collections

import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup

class CollectionListAdapter : ListAdapter<CollectionListItemViewModel, CollectionListItemViewHolder<*, *>>(object : DiffUtil.ItemCallback<CollectionListItemViewModel>() {

    override fun areItemsTheSame(old: CollectionListItemViewModel, new: CollectionListItemViewModel) = if (old is CollectionListItemViewModel.CollectionViewModel) {
        if (new is CollectionListItemViewModel.CollectionViewModel) old.collection.id == new.collection.id else false
    } else {
        if (new is CollectionListItemViewModel.CollectionViewModel) false else (old as CollectionListItemViewModel.HeaderViewModel).title == (new as CollectionListItemViewModel.HeaderViewModel).title
    }

    override fun areContentsTheSame(old: CollectionListItemViewModel, new: CollectionListItemViewModel) = old == new
}) {

    companion object {
        private const val VIEW_TYPE_COLLECTION = 0
        private const val VIEW_TYPE_HEADER = 1
    }

    private var recyclerView: RecyclerView? = null
    var shouldScrollToTop = false
    var items = listOf<CollectionListItemViewModel>()
        set(newItems) {
            submitList(newItems)
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

    override fun getItemViewType(position: Int) = when (getItem(position)) {
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
                (getItem(position) as? CollectionListItemViewModel.CollectionViewModel)?.run {
                    payloads.forEach { payload ->
                        when (payload) {
                            is Payload.BookmarkedStateChanged -> collection.isBookmarked = payload.isBookmarked
                        }
                    }
                    holder.bind(this, payloads.isEmpty())
                }
            }
            is CollectionListItemViewHolder.HeaderViewHolder -> (getItem(position) as? CollectionListItemViewModel.HeaderViewModel)?.let { holder.bind(it) }
        }
    }

    override fun getItemId(position: Int) = getItem(position).getItemId()

    sealed class Payload {
        class BookmarkedStateChanged(val isBookmarked: Boolean) : Payload()
    }
}