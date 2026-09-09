package com.pandulapeter.campfire

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.presentation.ui.CampfireAndroidApp
import com.pandulapeter.campfire.presentation.ui.platform.toImportedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CampfireActivity : AppCompatActivity() {

    // The activity is singleTask, so a second file opened while Campfire is running arrives at onNewIntent rather
    // than at a new instance; both ends up here.
    private val filesToImport = Channel<List<ImportedFile>>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CampfireAndroidApp(
                urlOpener = ::openUrl,
                filesToImport = filesToImport.receiveAsFlow()
            )
        }
        importFrom(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        importFrom(intent)
    }

    /** The URIs an "open with" or a share carries, whichever of the three shapes the intent uses. */
    private fun importFrom(intent: Intent?) {
        val uris = when (intent?.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelableExtra(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableArrayListExtra(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        lifecycleScope.launch {
            // Reading them is disk work, and the intent arrives on the main thread.
            val files = withContext(Dispatchers.IO) { uris.mapNotNull { it.toImportedFile(this@CampfireActivity) } }
            if (files.isNotEmpty()) {
                filesToImport.send(files)
            }
        }
    }

    private fun openUrl(url: String, isDarkTheme: Boolean) = try {
        CustomTabsIntent.Builder()
            .setColorScheme(if (isDarkTheme) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
            .build()
            .launchUrl(this, Uri.parse(url))
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(this, exception.message, Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableExtra(name: String): Uri? = getParcelableExtra(name)

    @Suppress("DEPRECATION")
    private fun Intent.parcelableArrayListExtra(name: String): List<Uri>? = getParcelableArrayListExtra(name)
}
