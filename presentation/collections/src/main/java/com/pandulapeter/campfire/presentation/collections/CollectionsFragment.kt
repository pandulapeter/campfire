package com.pandulapeter.campfire.presentation.collections

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.pandulapeter.campfire.presentation.collections.databinding.FragmentCollectionsBinding
import com.pandulapeter.campfire.presentation.collections.implementation.CollectionsViewModel
import com.pandulapeter.campfire.presentation.utilities.extensions.autoClearedValue
import com.pandulapeter.campfire.presentation.utilities.extensions.bind
import org.koin.androidx.viewmodel.ext.android.viewModel

class CollectionsFragment : Fragment(R.layout.fragment_collections) {

    private val viewModel by viewModel<CollectionsViewModel>()
    private var binding by autoClearedValue<FragmentCollectionsBinding>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = bind(view)
        binding.viewModel = viewModel
    }

    companion object {
        fun newInstance() = CollectionsFragment()
    }
}