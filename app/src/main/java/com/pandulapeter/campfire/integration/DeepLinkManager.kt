package com.pandulapeter.campfire.integration

import android.net.Uri
import com.pandulapeter.campfire.feature.main.options.about.AboutViewModel
import java.net.URLDecoder

fun List<String>.toDeepLinkUri(): Uri = Uri.parse("$DEEP_LINK_DOMAIN&songIds=[${joinToString(",")}]") ?: Uri.EMPTY

fun String.fromDeepLinkUri() = URLDecoder.decode(this, "UTF-8").substringAfter('[').substringBeforeLast(']').split(',')

//TODO: Use your own domain and redirect to the Play Store.
private const val DEEP_LINK_DOMAIN = "https://play.google.com/store/apps/details?id=${AboutViewModel.APP_ID}"