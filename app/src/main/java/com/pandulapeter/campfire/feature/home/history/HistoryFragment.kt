package com.pandulapeter.campfire.feature.home.history

import android.content.Context
import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentHistoryBinding
import com.pandulapeter.campfire.feature.CampfireFragment

class HistoryFragment : CampfireFragment<FragmentHistoryBinding>(R.layout.fragment_history) {

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        defaultToolbar.updateToolbarTitle(R.string.home_history)
    }

    override fun inflateToolbarButtons(context: Context) = listOf<View>(
        context.createToolbarButton(R.drawable.ic_delete_24dp) { showSnackbar(R.string.work_in_progress) }
    )
}