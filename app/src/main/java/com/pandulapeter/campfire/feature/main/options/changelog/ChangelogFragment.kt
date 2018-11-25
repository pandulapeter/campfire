package com.pandulapeter.campfire.feature.main.options.changelog

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOptionsChangelogBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import org.koin.androidx.viewmodel.ext.android.viewModel

class ChangelogFragment : CampfireFragment<FragmentOptionsChangelogBinding, ChangelogViewModel>(R.layout.fragment_options_changelog) {

    override val viewModel by viewModel<ChangelogViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerView.apply {
            adapter = ChangelogAdapter(viewModel.data)
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(true)
            itemAnimator = object : DefaultItemAnimator() {
                init {
                    supportsChangeAnimations = false
                }
            }
        }
    }

    companion object {

        fun newInstance() = ChangelogFragment()
    }
}