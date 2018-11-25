package com.pandulapeter.campfire.feature.main.options.about

import android.animation.ObjectAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Property
import android.view.View
import androidx.browser.customtabs.CustomTabsIntent
import com.android.billingclient.api.*
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOptionsAboutBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.color
import org.koin.androidx.viewmodel.ext.android.viewModel

class AboutFragment : CampfireFragment<FragmentOptionsAboutBinding, AboutViewModel>(R.layout.fragment_options_about), PurchasesUpdatedListener {

    override val viewModel by viewModel<AboutViewModel>()
    private val billingClient by lazy { BillingClient.newBuilder(requireContext()).setListener(this).build() }
    private val scale = object : Property<View, Float>(Float::class.java, "scale") {

        override fun set(view: View?, value: Float) {
            view?.run {
                scaleX = value
                scaleY = value
            }
        }

        override fun get(view: View) = view.scaleX
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.apply {
            intentToStart.observeAndReset { startActivityFromIntent(it) }
            urlToStart.observeAndReset { openCustomTabFromUrl(it) }
            shouldStartPurchaseFlow.observeAndReset { startPurchaseFlow() }
            shouldShowErrorShowSnackbar.observeAndReset { showSnackbar(R.string.options_about_error) }
            shouldAnimateLogo.observeAndReset { animateLogo() }
        }
    }

    override fun onPurchasesUpdated(@BillingClient.BillingResponse responseCode: Int, purchases: List<Purchase>?) {
        if (responseCode == BillingClient.BillingResponse.OK && purchases != null && purchases.isNotEmpty()) {
            purchases[0].let { purchase ->
                billingClient.consumeAsync(purchase.purchaseToken) { responseCode, _ ->
                    if (responseCode == BillingClient.BillingResponse.OK) {
                        showSnackbar(R.string.options_about_in_app_purchase_success)
                    }
                    viewModel.isUiBlocked = false
                }
            }
        }
    }

    private fun startActivityFromIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (exception: ActivityNotFoundException) {
            viewModel.shouldShowErrorShowSnackbar.value = true
        }
    }

    private fun openCustomTabFromUrl(url: String) {
        context?.let { context ->
            CustomTabsIntent.Builder()
                .setToolbarColor(context.color(R.color.accent))
                .build()
                .launchUrl(context, Uri.parse(url))
        }
    }

    private fun startPurchaseFlow() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(@BillingClient.BillingResponse billingResponseCode: Int) {
                if (billingResponseCode == BillingClient.BillingResponse.OK) {
                    billingClient.launchBillingFlow(
                        activity, BillingFlowParams.newBuilder()
                            .setSku("buy_me_a_beer")
                            .setType(BillingClient.SkuType.INAPP)
                            .build()
                    )
                } else {
                    viewModel.isUiBlocked = false
                }
            }

            override fun onBillingServiceDisconnected() {
                viewModel.isUiBlocked = false
            }
        })
    }

    private fun animateLogo() = ObjectAnimator
        .ofFloat(binding.logo, scale, 1f, 1.5f, 0.5f, 1.25f, 0.75f, 1.1f, 0.9f, 1f)
        .setDuration(800)
        .start()

    companion object {

        fun newInstance() = AboutFragment()
    }
}