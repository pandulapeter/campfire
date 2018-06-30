package com.pandulapeter.campfire.feature.main.home.home

import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup

class HomeAdapter : RecyclerView.Adapter<HomeItemViewHolder<*, *>>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_COLLECTION = 1
        private const val VIEW_TYPE_SONG = 2
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
                    return when (old) {
                        is HomeItemViewModel.HeaderViewModel -> when (new) {
                            is HomeItemViewModel.HeaderViewModel -> old.title == new.title
                            is HomeItemViewModel.CollectionViewModel -> false
                            is HomeItemViewModel.SongViewModel -> false
                        }
                        is HomeItemViewModel.CollectionViewModel -> when (new) {
                            is HomeItemViewModel.HeaderViewModel -> false
                            is HomeItemViewModel.CollectionViewModel -> old.collection.id == new.collection.id
                            is HomeItemViewModel.SongViewModel -> false
                        }
                        is HomeItemViewModel.SongViewModel -> when (new) {
                            is HomeItemViewModel.HeaderViewModel -> false
                            is HomeItemViewModel.CollectionViewModel -> false
                            is HomeItemViewModel.SongViewModel -> old.song.id == new.song.id
                        }
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
    var collectionClickListener: (position: Int, clickedView: View, image: View) -> Unit = { _, _, _ -> }
    var songClickListener: (position: Int, clickedView: View) -> Unit = { _, _ -> }

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
        is HomeItemViewModel.HeaderViewModel -> VIEW_TYPE_HEADER
        is HomeItemViewModel.CollectionViewModel -> VIEW_TYPE_COLLECTION
        is HomeItemViewModel.SongViewModel -> VIEW_TYPE_SONG
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        VIEW_TYPE_HEADER -> HomeItemViewHolder.HeaderViewHolder(parent)
        VIEW_TYPE_COLLECTION -> HomeItemViewHolder.CollectionViewHolder(parent).apply {
            setItemClickListener(collectionClickListener)
        }
        VIEW_TYPE_SONG -> HomeItemViewHolder.SongViewHolder(parent).apply {
            setItemClickListener(songClickListener)
        }
        else -> throw IllegalArgumentException("Unsupported item type.")
    }


    override fun onBindViewHolder(holder: HomeItemViewHolder<*, *>, position: Int) = Unit

    override fun onBindViewHolder(holder: HomeItemViewHolder<*, *>, position: Int, payloads: List<Any>) {
        when (holder) {
            is HomeItemViewHolder.HeaderViewHolder -> (items[position] as? HomeItemViewModel.HeaderViewModel)?.let { holder.bind(it) }
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
            is HomeItemViewHolder.SongViewHolder -> {
                (items[position] as? HomeItemViewModel.SongViewModel)?.run {
                    holder.bind(this, payloads.isEmpty())
                }
            }
        }
    }

    override fun getItemCount() = items.size

    override fun getItemId(position: Int) = items[position].getItemId()

    sealed class Payload {
        class BookmarkedStateChanged(val isBookmarked: Boolean) : Payload()
    }
}