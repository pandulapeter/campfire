package com.pandulapeter.campfire.feature.main.options.about

import android.animation.ObjectAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Property
import android.view.View
import androidx.browser.customtabs.CustomTabsIntent
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.SkuDetailsParams
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

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && !purchases.isNullOrEmpty()) {
            purchases[0].let { purchase ->
                billingClient.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { result, _ ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        showSnackbar(R.string.options_about_in_app_purchase_success)
                    }
                    isUiBlocked = false
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
            override fun onBillingSetupFinished(result: BillingResult) {
                if (isAdded && context != null) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {

                        billingClient.querySkuDetailsAsync(
                            SkuDetailsParams
                                .newBuilder()
                                .setSkusList(listOf("buy_me_a_beer"))
                                .setType(BillingClient.SkuType.INAPP)
                                .build()
                        ) { _, skuDetails ->
                            if (skuDetails.isNullOrEmpty()) {
                                showSnackbar(R.string.something_went_wrong)
                                isUiBlocked = false
                            } else {
                                activity?.let { activity ->
                                    billingClient.launchBillingFlow(
                                        activity, BillingFlowParams.newBuilder()
                                            .setSkuDetails(skuDetails.first())
                                            .build()
                                    )
                                }
                            }
                        }
                    } else {
                        isUiBlocked = false
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                if (isAdded && context != null) {
                    isUiBlocked = false
                }
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