package com.pandulapeter.campfire.feature

import android.animation.Animator
import android.animation.LayoutTransition
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.transition.Explode
import android.view.Gravity
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.MenuRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Observer
import com.crashlytics.android.Crashlytics
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.internal.NavigationMenuView
import com.google.android.material.navigation.NavigationView
import com.jakewharton.processphoenix.ProcessPhoenix
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.databinding.ActivityCampfireBinding
import com.pandulapeter.campfire.feature.detail.DetailFragment
import com.pandulapeter.campfire.feature.main.collections.CollectionsFragment
import com.pandulapeter.campfire.feature.main.collections.detail.CollectionDetailFragment
import com.pandulapeter.campfire.feature.main.history.HistoryFragment
import com.pandulapeter.campfire.feature.main.home.HomeContainerFragment
import com.pandulapeter.campfire.feature.main.manageDownloads.ManageDownloadsFragment
import com.pandulapeter.campfire.feature.main.managePlaylists.ManagePlaylistsFragment
import com.pandulapeter.campfire.feature.main.options.OptionsFragment
import com.pandulapeter.campfire.feature.main.options.about.AboutViewModel
import com.pandulapeter.campfire.feature.main.options.preferences.PreferencesViewModel
import com.pandulapeter.campfire.feature.main.playlist.PlaylistFragment
import com.pandulapeter.campfire.feature.main.sharedWithYou.SharedWithYouFragment
import com.pandulapeter.campfire.feature.main.songs.SongsFragment
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment
import com.pandulapeter.campfire.feature.shared.dialog.BaseDialogFragment
import com.pandulapeter.campfire.feature.shared.dialog.NewPlaylistDialogFragment
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.IntentExtraDelegate
import com.pandulapeter.campfire.util.addDrawerListener
import com.pandulapeter.campfire.util.addListener
import com.pandulapeter.campfire.util.animatedDrawable
import com.pandulapeter.campfire.util.animatedVisibilityEnd
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.drawable
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.obtainColor
import com.pandulapeter.campfire.util.toUrlIntent
import com.pandulapeter.campfire.util.visibleOrGone
import com.pandulapeter.campfire.util.visibleOrInvisible
import io.fabric.sdk.android.Fabric
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Locale

class CampfireActivity : AppCompatActivity(), BaseDialogFragment.OnDialogItemSelectedListener {

    private val binding by lazy { DataBindingUtil.setContentView<ActivityCampfireBinding>(this, R.layout.activity_campfire) }
    private val viewModel by viewModel<ActivityViewModel>()
    private val currentFragment get() = supportFragmentManager.findFragmentById(R.id.fragment_container) as? CampfireFragment<*, *>?
    private val drawableMenuToBack by lazy { animatedDrawable(R.drawable.avd_menu_to_back) }
    private val drawableBackToMenu by lazy { animatedDrawable(R.drawable.avd_back_to_menu) }
    private var currentPlaylistId = ""
    private var currentCollectionId = ""
    private var currentScreenId = R.id.songs
        set(value) {
            field = value
            binding.primaryNavigation.checkedItem?.isChecked = value != 0
        }
    private val colorWhite by lazy { color(R.color.white) }
    private val playlistsContainerItem by lazy { binding.primaryNavigation.menu.findItem(R.id.playlists).subMenu }
    private val playlistIdMap = mutableMapOf<Int, String>()
    private var newPlaylistId = 0
    private var startTime = 0L
    private val isBackStackEmpty get() = supportFragmentManager.backStackEntryCount == 0
    val isAfterFirstStart get() = System.currentTimeMillis() - startTime > APPROXIMATE_STARTUP_TIME
    var isUiBlocked
        get() = viewModel.isUiBlocked
        set(value) {
            viewModel.isUiBlocked = value
        }
    var lastSongId: String = ""
    var lastCollectionId: String = ""
    val autoScrollControl: View get() = binding.autoScrollControl
    val toolbarContext get() = binding.appBarLayout.context!!
    val toolbarHeight get() = binding.toolbarTitleContainer.height
    val secondaryNavigationMenu get() = binding.secondaryNavigation.menu ?: throw IllegalStateException("The secondary navigation drawer has no menu inflated.")
    val snackbarRoot: View get() = binding.rootCoordinatorLayout
    var transitionMode: Boolean? = null
        set(value) {
            if (field != value && isAfterFirstStart) {
                when (value) {
                    true -> {
                        binding.appBarLayout.layoutTransition = LayoutTransition().apply {
                            setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0)
                            disableTransitionType(LayoutTransition.APPEARING)
                            disableTransitionType(LayoutTransition.CHANGE_APPEARING)

                        }
                        binding.coordinatorLayout.layoutTransition = LayoutTransition().apply {
                            enableTransitionType(LayoutTransition.CHANGING)
                        }
                    }
                    false -> {
                        binding.appBarLayout.layoutTransition = LayoutTransition().apply {
                            disableTransitionType(LayoutTransition.DISAPPEARING)
                            disableTransitionType(LayoutTransition.APPEARING)
                            disableTransitionType(LayoutTransition.CHANGE_APPEARING)
                            enableTransitionType(LayoutTransition.CHANGING)
                            setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0)
                        }
                        binding.coordinatorLayout.layoutTransition = LayoutTransition().apply {
                            disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
                        }
                    }
                    null -> {
                        binding.appBarLayout.layoutTransition = null
                        binding.coordinatorLayout.layoutTransition = null
                    }
                }
                field = value
            }
        }
    val autoScrollSpeed get() = if (binding.autoScrollControl.visibleOrInvisible && binding.autoScrollControl.tag == null) binding.autoScrollSeekBar.progress else -1

    override fun onCreate(savedInstanceState: Bundle?) {

        // Enable crash reporting if the user opted in.
        @Suppress("ConstantConditionIf")
        if (viewModel.preferenceDatabase.isOnboardingDone && viewModel.preferenceDatabase.shouldShareCrashReports && BuildConfig.BUILD_TYPE != "debug") {
            Fabric.with(applicationContext, Crashlytics())
        }

        // Set the theme.
        AppCompatDelegate.setDefaultNightMode(
            when (PreferencesViewModel.Theme.fromId(viewModel.preferenceDatabase.theme)) {
                PreferencesViewModel.Theme.AUTOMATIC -> AppCompatDelegate.MODE_NIGHT_AUTO
                PreferencesViewModel.Theme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                PreferencesViewModel.Theme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            }
        )
        setTheme(R.style.Campfire)

        // Set the language
        @Suppress("DEPRECATION")
        PreferencesViewModel.Language.fromId(viewModel.preferenceDatabase.language).run {
            (if (this == PreferencesViewModel.Language.AUTOMATIC) Resources.getSystem().configuration.locale else Locale(id)).let {
                Locale.setDefault(it)
                resources.updateConfiguration(Configuration(resources.configuration).apply { locale = it }, resources.displayMetrics)
            }
        }
        super.onCreate(savedInstanceState)
        startTime = System.currentTimeMillis()
        viewModel.playlists.observe(this, Observer { updatePlaylists(it) })

        // Make sure the status bar color is properly set.
        window.decorView.systemUiVisibility = when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_UNDEFINED,
            Configuration.UI_MODE_NIGHT_NO -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            else -> 0
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.navigationBarColor = color(R.color.navigation_bar)
        }

        // Make sure the "What's new" snackbar does not appear after a fresh start.
        if (viewModel.preferenceDatabase.ftuxLastSeenChangelog == 0) {
            viewModel.preferenceDatabase.ftuxLastSeenChangelog = BuildConfig.VERSION_CODE
        }

        // Initialize the app bar.
        val appBarElevation = dimension(R.dimen.toolbar_elevation).toFloat()
        binding.toolbarMainButton.setOnClickListener {
            if (!isUiBlocked) {
                if (isBackStackEmpty) {
                    hideKeyboard(currentFocus)
                    binding.drawerLayout.openDrawer(GravityCompat.START)
                } else {
                    supportFragmentManager.popBackStack()
                }
            }
        }
        binding.appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, _ -> ViewCompat.setElevation(appBarLayout, appBarElevation) })

        // Initialize the drawer layout.
        val header = binding.primaryNavigation.getHeaderView(0)
        binding.drawerLayout.addDrawerListener(
            onDrawerStateChanged = {
                (currentFragment as? TopLevelFragment?)?.onDrawerStateChanged(it)
                if (it == DrawerLayout.STATE_DRAGGING) {
                    hideKeyboard(currentFocus)
                }
            },
            onDrawerSlide = { view, offset ->
                if (view == binding.primaryNavigation) {
                    header.translationX = (1 - offset) * header.width / 2
                }
            })

        // Initialize the primary side navigation drawer.
        binding.primaryNavigation.disableScrollbars()
        val headerView = binding.primaryNavigation.getHeaderView(0)
        (headerView?.findViewById<View>(R.id.version) as? TextView)?.text = getString(R.string.main_version_pattern, BuildConfig.VERSION_NAME)
        binding.rootCoordinatorLayout.insetChangeListener = {
            headerView?.apply { setPadding(paddingStart, it, paddingEnd, paddingBottom) }
        }
        binding.primaryNavigation.setNavigationItemSelectedListener { menuItem ->
            if (currentScreenId == menuItem.itemId) {
                consumeAndCloseDrawers()
            } else {
                if (menuItem.itemId != newPlaylistId) {
                    currentScreenId = menuItem.itemId
                }
                return@setNavigationItemSelectedListener when (menuItem.itemId) {
                    R.id.home -> consumeAndCloseDrawers {
                        supportFragmentManager.handleReplace {
                            viewModel.appShortcutManager.onHomeOpened()
                            HomeContainerFragment.newInstance()
                        }
                    }
                    R.id.collections -> consumeAndCloseDrawers {
                        supportFragmentManager.handleReplace {
                            viewModel.appShortcutManager.onCollectionsOpened()
                            CollectionsFragment.newInstance()
                        }
                    }
                    R.id.songs -> consumeAndCloseDrawers {
                        viewModel.appShortcutManager.onSongsOpened()
                        supportFragmentManager.handleReplace { SongsFragment() }
                    }
                    R.id.history -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { HistoryFragment() } }
                    R.id.options -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { OptionsFragment.newInstance(false) } }
                    R.id.manage_playlists -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { ManagePlaylistsFragment() } }
                    R.id.manage_downloads -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { ManageDownloadsFragment() } }
                    newPlaylistId -> {
                        if (!isUiBlocked) {
                            currentFragment?.hideSnackbar()
                            NewPlaylistDialogFragment.show(supportFragmentManager, AnalyticsManager.PARAM_VALUE_DRAWER)
                            binding.drawerLayout.closeDrawers()
                        }
                        false
                    }
                    else -> consumeAndCloseDrawers { playlistIdMap[menuItem.itemId]?.let { openPlaylistScreen(it) } }
                }
            }
        }

        // Initialize the secondary side navigation drawer.
        binding.secondaryNavigation.disableScrollbars()
        binding.secondaryNavigation.setNavigationItemSelectedListener { currentFragment?.onNavigationItemSelected(it) ?: false }

        // Initialize the floating action button.
        binding.floatingActionButton.setOnClickListener {
            if (binding.autoScrollControl.tag == null && binding.floatingActionButton.isShown && it.tag == null && !isUiBlocked) {
                (currentFragment as? TopLevelFragment?)?.onFloatingActionButtonPressed()
            }
        }
        binding.autoScrollSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = viewModel.analyticsManager.onAutoScrollSpeedChanged(binding.autoScrollSeekBar.progress)
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode) {
            viewModel.analyticsManager.trackSplitScreenEntered()
        }

        // Restore instance state if possible.
        if (savedInstanceState == null) {
            handleNewIntent()
        } else {
            binding.toolbarButtonContainer.apply { post { layoutTransition = LayoutTransition() } }
            currentScreenId = savedInstanceState.currentScreenId
            currentPlaylistId = savedInstanceState.currentPlaylistId
            currentCollectionId = savedInstanceState.currentCollectionId
            lastSongId = savedInstanceState.lastSongId
            lastCollectionId = savedInstanceState.lastCollectionId
            if (savedInstanceState.isFabVisible) {
                binding.floatingActionButton.show(false)
            }
        }
        binding.drawerLayout.setDrawerLockMode(
            if (currentFragment is DetailFragment || currentFragment is CollectionDetailFragment) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED,
            GravityCompat.START
        )
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        this.intent = intent
        handleNewIntent()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration?) {
        if (isInMultiWindowMode) {
            viewModel.analyticsManager.trackSplitScreenEntered()
        } else {
            viewModel.analyticsManager.trackSplitScreenClosed()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.subscribe()
        isUiBlocked = false
        if (currentFocus is EditText) {
            binding.drawerLayout.run { post { closeDrawers() } }
        }
        (currentFragment as? DetailFragment)?.notifyTransitionEnd()
        updateTaskDescription()
    }

    override fun onPause() {
        super.onPause()
        viewModel.unsubscribe()
    }

    override fun onBackPressed() {
        if (!isUiBlocked) {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.END)
                } else {
                    if (currentFragment?.onBackPressed() != true) {
                        if (isBackStackEmpty) {
                            if (viewModel.preferenceDatabase.shouldShowExitConfirmation && viewModel.preferenceDatabase.isOnboardingDone) {
                                AlertDialogFragment.show(
                                    id = DIALOG_ID_EXIT_CONFIRMATION,
                                    fragmentManager = supportFragmentManager,
                                    title = R.string.are_you_sure,
                                    message = R.string.main_exit_confirmation_message,
                                    positiveButton = R.string.main_exit_confirmation_close,
                                    negativeButton = R.string.cancel
                                )
                            } else {
                                onPositiveButtonSelected(DIALOG_ID_EXIT_CONFIRMATION)
                            }
                        } else {
                            super.onBackPressed()
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.currentScreenId = currentScreenId
        outState.currentPlaylistId = playlistIdMap[currentScreenId] ?: ""
        outState.currentCollectionId = currentCollectionId
        outState.lastSongId = lastSongId
        outState.lastCollectionId = lastCollectionId
        outState.isFabVisible = binding.floatingActionButton.isVisible()
    }

    override fun onPositiveButtonSelected(id: Int) {
        when (id) {
            DIALOG_ID_EXIT_CONFIRMATION -> supportFinishAfterTransition()
            DIALOG_ID_PLAY_STORE_RATING -> {
                viewModel.analyticsManager.trackRateApp()
                tryToOpenIntent(AboutViewModel.PLAY_STORE_URL.toUrlIntent())
            }
        }
    }

    fun showPlayStoreRatingDialogIfNeeded() {
        if (viewModel.preferenceDatabase.songsOpened > 10 && !viewModel.preferenceDatabase.ratingDialogShown) {
            binding.root.postDelayed({
                if (!viewModel.preferenceDatabase.ratingDialogShown) {
                    viewModel.analyticsManager.trackAskForRating()
                    AlertDialogFragment.show(
                        id = DIALOG_ID_PLAY_STORE_RATING,
                        fragmentManager = supportFragmentManager,
                        title = R.string.main_play_store_rating_title,
                        message = R.string.main_play_store_rating_message,
                        positiveButton = R.string.main_play_store_rating_positive,
                        negativeButton = R.string.main_play_store_rating_negative,
                        cancellable = false
                    )
                    viewModel.preferenceDatabase.ratingDialogShown = true
                }
            }, 600)
        }
    }

    fun updateAppBarView(view: View?, immediately: Boolean = false) {
        binding.appBarLayout.apply {
            fun removeViews() {
                while (childCount > 1) {
                    removeView(getChildAt(1))
                }
            }
            if (view == null) {
                if (childCount > 1) {
                    postDelayed({
                        transitionMode = true
                        removeViews()
                    }, 200)
                }
            } else {
                if (immediately || !isAfterFirstStart) {
                    layoutTransition = null
                    removeViews()
                    addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    transitionMode = true
                } else {
                    postDelayed({
                        transitionMode = true
                        removeViews()
                        addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    }, 200)
                }
            }
        }
    }

    private fun updateMainToolbarButton(shouldShowBackButton: Boolean) {
        binding.toolbarMainButton.run {
            setImageDrawable(if (if (tag == null) false else tag != shouldShowBackButton) {
                (if (shouldShowBackButton) drawableMenuToBack else drawableBackToMenu).apply { this?.start() }
            } else {
                drawable(if (shouldShowBackButton) R.drawable.ic_back else R.drawable.ic_menu)
            })
            tag = shouldShowBackButton
        }
    }

    fun updateToolbarTitleView(toolbar: View, width: Int = 0) {
        val oldView = binding.toolbarTitleContainer.run {
            while (childCount > 1) {
                removeViewAt(1)
            }
            if (childCount > 0) getChildAt(0) else null
        }
        if (toolbar != oldView) {
            oldView?.visibleOrGone = false
            binding.toolbarTitleContainer.removeView(oldView)
            if (toolbar.parent == null) {
                binding.toolbarTitleContainer.addView(
                    toolbar.apply { visibleOrGone = true },
                    FrameLayout.LayoutParams(if (width == 0) ViewGroup.LayoutParams.MATCH_PARENT else width, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                        gravity = Gravity.CENTER_VERTICAL
                    })
            } else {
                binding.root.post { updateToolbarTitleView(toolbar, width) }
            }
        }
        binding.toolbarTitleContainer.apply {
            if (layoutTransition == null) {
                post { layoutTransition = LayoutTransition() }
            }
        }
    }

    fun updateToolbarButtons(buttons: List<View>) = binding.toolbarButtonContainer.run {
        if (childCount == 0) {
            buttons.forEach { addView(it, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) }
        }
    }

    fun enableSecondaryNavigationDrawer(@MenuRes menuResourceId: Int) {
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
        binding.secondaryNavigation.menu.clear()
        binding.secondaryNavigation.inflateMenu(menuResourceId)
    }

    fun openSecondaryNavigationDrawer() {
        hideKeyboard(currentFocus)
        binding.drawerLayout.openDrawer(GravityCompat.END)
    }

    fun closeSecondaryNavigationDrawer() = binding.drawerLayout.closeDrawer(GravityCompat.END)

    fun isFloatingActionButtonEnabled() = binding.floatingActionButton.isVisible()

    fun enableFloatingActionButton() {
        binding.floatingActionButton.show()
        binding.autoScrollControl.run {
            (tag as? Animator)?.let {
                it.addListener(onAnimationEnd = { binding.floatingActionButton.show() })
            }
        }
        binding.floatingActionButton.tag = null
    }

    fun disableFloatingActionButton() = binding.autoScrollControl.run {
        binding.floatingActionButton.tag = "Hiding"
        if (animatedVisibilityEnd) {
            animatedVisibilityEnd = false
            (tag as? Animator)?.let {
                it.addListener(onAnimationEnd = {
                    binding.floatingActionButton.hide()
                    tag = null
                    visibleOrGone = false

                })
            }
        } else {
            binding.floatingActionButton.hide()
        }
    }

    fun updateFloatingActionButtonDrawable(drawable: Drawable?) = binding.floatingActionButton.setImageDrawable(drawable.apply { this?.setTint(colorWhite) })

    fun onScreenChanged() {

        // Hide the keyboard.
        hideKeyboard(currentFocus)

        // Reset the app bar.
        transitionMode = false
        if (isAfterFirstStart && binding.toolbarButtonContainer.layoutTransition == null) {
            binding.toolbarButtonContainer.layoutTransition = LayoutTransition()
        }
        binding.toolbarButtonContainer.removeAllViews()
        updateMainToolbarButton(!isBackStackEmpty)
        val shouldShowAppBar = (currentFragment as? TopLevelFragment?)?.shouldShowAppBar ?: false
        binding.rootCoordinatorLayout.paint = Paint().apply { color = obtainColor(if (shouldShowAppBar) android.R.attr.colorPrimary else android.R.attr.windowBackground) }
        binding.coordinatorLayout.clipChildren = shouldShowAppBar
        binding.appBarLayout.apply {
            if (shouldShowAppBar != visibleOrInvisible) {
                animate().cancel()
                if (shouldShowAppBar) {
                    visibleOrInvisible = true
                    translationY = -height.toFloat()
                    postDelayed({ animate().translationY(0f).start() }, 200)
                } else {
                    animate().translationY(-height.toFloat()).apply {
                        setListener(object : Animator.AnimatorListener {

                            override fun onAnimationStart(animation: Animator?) = Unit

                            override fun onAnimationRepeat(animation: Animator?) = Unit

                            override fun onAnimationEnd(animation: Animator?) {
                                visibleOrInvisible = false
                                setListener(null)
                            }

                            override fun onAnimationCancel(animation: Animator?) {
                                visibleOrInvisible = false
                                setListener(null)
                            }
                        })
                    }.start()
                }
            }
        }

        // Reset the primary navigation drawer.
        binding.drawerLayout.setDrawerLockMode(
            if (isBackStackEmpty && shouldShowAppBar) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
            GravityCompat.START
        )

        // Reset the secondary navigation drawer.
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
        binding.secondaryNavigation.menu.clear()

        // Reset the floating action button.
        disableFloatingActionButton()

        // Show the changelog snackbar if needed
        if (viewModel.preferenceDatabase.ftuxLastSeenChangelog < BuildConfig.VERSION_CODE) {
            viewModel.preferenceDatabase.ftuxLastSeenChangelog = BuildConfig.VERSION_CODE
            if (viewModel.preferenceDatabase.isOnboardingDone) {
                currentFragment?.showSnackbar(
                    message = R.string.options_changelog_app_updated,
                    actionText = R.string.options_changelog_what_is_new,
                    action = {
                        viewModel.analyticsManager.onWhatIsNewButtonPressed()
                        openOptionsScreen(true)
                    })
            }
        }
    }

    fun wasStartedFromDeepLink() = intent?.action == Intent.ACTION_VIEW && intent?.screenToOpen?.isEmpty() == true

    private fun handleNewIntent() {
        if (viewModel.preferenceDatabase.isOnboardingDone && wasStartedFromDeepLink()) {
            openSharedWithYouScreen()
        } else {
            startScreenFromIntent()
        }
    }

    private fun startScreenFromIntent() {
        if (currentFragment is DetailFragment) {
            if (intent.screenToOpen.isEmpty()) {
                return
            } else {
                supportFragmentManager.clearBackStack()
            }
        }
        var isFromAppShortcut = true
        val screen = when (intent.screenToOpen) {
            "" -> {
                viewModel.preferenceDatabase.lastScreen.let {
                    isFromAppShortcut = false
                    when (it) {
                        "", SCREEN_HOME -> openHomeScreen()
                        SCREEN_COLLECTIONS -> openCollectionsScreen()
                        SCREEN_SONGS -> openSongsScreen()
                        SCREEN_HISTORY -> openHistoryScreen()
                        SCREEN_OPTIONS -> openOptionsScreen()
                        SCREEN_MANAGE_PLAYLISTS -> openManagePlaylistsScreen()
                        SCREEN_MANAGE_DOWNLOADS -> openManageDownloadsScreen()
                        else -> openPlaylistScreen(it)
                    }
                }
            }
            SCREEN_HOME -> openHomeScreen()
            SCREEN_COLLECTIONS -> openCollectionsScreen()
            SCREEN_SONGS -> openSongsScreen()
            else -> openPlaylistScreen(intent.screenToOpen)
        }
        viewModel.analyticsManager.onAppOpened(
            screen,
            isFromAppShortcut,
            when (PreferencesViewModel.Theme.fromId(viewModel.preferenceDatabase.theme)) {
                PreferencesViewModel.Theme.AUTOMATIC -> AnalyticsManager.PARAM_VALUE_AUTOMATIC
                PreferencesViewModel.Theme.LIGHT -> AnalyticsManager.PARAM_VALUE_LIGHT
                PreferencesViewModel.Theme.DARK -> AnalyticsManager.PARAM_VALUE_DARK
            },
            PreferencesViewModel.Language.fromId(viewModel.preferenceDatabase.language).let {
                if (it == PreferencesViewModel.Language.AUTOMATIC) AnalyticsManager.PARAM_VALUE_AUTOMATIC else it.id
            }
        )
    }

    private fun updateTaskDescription() {
        @Suppress("ConstantConditionIf")
        val label = getString(R.string.campfire) + if (BuildConfig.BUILD_TYPE == "release") "" else " (" + BuildConfig.BUILD_TYPE + ")"
        val color = (binding.toolbarButtonContainer.background as ColorDrawable).color
        setTaskDescription(
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ActivityManager.TaskDescription(label, 0, color) else ActivityManager.TaskDescription(label, null, color)
        )
    }

    private fun openHomeScreen(): String {
        if (currentFragment !is HomeContainerFragment) {
            supportFragmentManager.clearBackStack()
            supportFragmentManager.handleReplace { HomeContainerFragment.newInstance() }
            currentScreenId = R.id.home
            binding.primaryNavigation.setCheckedItem(R.id.home)
            viewModel.appShortcutManager.onHomeOpened()
        }
        return AnalyticsManager.PARAM_VALUE_SCREEN_HOME
    }

    private fun openCollectionsScreen(): String {
        if (currentFragment !is CollectionsFragment) {
            supportFragmentManager.clearBackStack()
            supportFragmentManager.handleReplace { CollectionsFragment.newInstance() }
            currentScreenId = R.id.collections
            binding.primaryNavigation.setCheckedItem(R.id.collections)
            viewModel.appShortcutManager.onCollectionsOpened()
        }
        return AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTIONS
    }

    fun openSongsScreen(): String {
        if (currentFragment !is SongsFragment) {
            supportFragmentManager.clearBackStack()
            supportFragmentManager.handleReplace { SongsFragment() }
            currentScreenId = R.id.songs
            binding.primaryNavigation.setCheckedItem(R.id.songs)
            viewModel.appShortcutManager.onSongsOpened()
        }
        return AnalyticsManager.PARAM_VALUE_SCREEN_SONGS
    }

    fun openCollectionDetailsScreen(collection: Collection, clickedView: View?, image: View?, shouldExplode: Boolean) {
        lastCollectionId = collection.id
        if (currentFragment !is CollectionDetailFragment || currentCollectionId != collection.id) {
            currentCollectionId = collection.id
            fun createTransition(delay: Long) = Explode().apply {
                propagation = null
                startDelay = delay
                duration = DetailFragment.TRANSITION_DURATION
            }
            currentFragment?.run {
                if (shouldExplode) {
                    exitTransition = createTransition(0)
                    reenterTransition = createTransition(DetailFragment.TRANSITION_DELAY)
                } else {
                    exitTransition = null
                    reenterTransition = null
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CollectionDetailFragment.newInstance(collection))
                .apply {
                    if (clickedView == null || image == null) {
                        setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    } else {
                        setReorderingAllowed(true)
                        clickedView.transitionName = "card-$lastCollectionId"
                        addSharedElement(clickedView, clickedView.transitionName ?: "")
                        image.transitionName = "image-$lastCollectionId"
                        addSharedElement(image, image.transitionName ?: "")
                    }
                }
                .addToBackStack(null)
                .commit()
        }
    }

    private fun openHistoryScreen(): String {
        if (currentFragment !is HistoryFragment) {
            supportFragmentManager.clearBackStack()
            supportFragmentManager.handleReplace { HistoryFragment() }
            currentScreenId = R.id.history
            binding.primaryNavigation.setCheckedItem(R.id.history)
        }
        return AnalyticsManager.PARAM_VALUE_SCREEN_HISTORY
    }

    private fun openOptionsScreen(shouldOpenChangelog: Boolean = false): String {
        currentFragment.let {
            if (it is OptionsFragment) {
                if (shouldOpenChangelog) {
                    it.navigateToChangelog()
                }
            } else {
                supportFragmentManager.clearBackStack()
                supportFragmentManager.handleReplace { OptionsFragment.newInstance(shouldOpenChangelog) }
                currentScreenId = R.id.options
                binding.primaryNavigation.setCheckedItem(R.id.options)
            }
        }
        return AnalyticsManager.PARAM_VALUE_SCREEN_OPTIONS
    }

    private fun openPlaylistScreen(playlistId: String): String {
        if (currentFragment !is PlaylistFragment || currentPlaylistId != playlistId) {
            supportFragmentManager.clearBackStack()
            playlistIdMap.forEach {
                if (it.value == playlistId) {
                    currentScreenId = it.key
                    binding.primaryNavigation.setCheckedItem(it.key)
                }
            }
            currentPlaylistId = playlistId
            viewModel.appShortcutManager.onPlaylistOpened(playlistId)
            supportFragmentManager.handleReplace("${PlaylistFragment::class.java.simpleName}-$playlistId") { PlaylistFragment.newInstance(playlistId) }
        }
        return AnalyticsManager.PARAM_VALUE_SCREEN_PLAYLIST
    }

    private fun openManagePlaylistsScreen(): String {
        if (currentFragment !is ManagePlaylistsFragment) {
            supportFragmentManager.clearBackStack()
            currentScreenId = R.id.manage_playlists
            supportFragmentManager.handleReplace { ManagePlaylistsFragment() }
            binding.primaryNavigation.setCheckedItem(R.id.manage_playlists)
        }
        return AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_PLAYLISTS
    }

    private fun openManageDownloadsScreen(): String {
        if (currentFragment !is ManageDownloadsFragment) {
            supportFragmentManager.clearBackStack()
            currentScreenId = R.id.manage_downloads
            supportFragmentManager.handleReplace { ManageDownloadsFragment() }
            binding.primaryNavigation.setCheckedItem(R.id.manage_downloads)
        }
        return AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_DOWNLOADS
    }

    fun openDetailScreen(clickedView: View?, songs: List<Song>, shouldExplode: Boolean, index: Int, shouldShowManagePlaylist: Boolean) {
        lastSongId = songs[index].id
        fun createTransition(delay: Long) = Explode().apply {
            propagation = null
            startDelay = delay
            duration = DetailFragment.TRANSITION_DURATION
        }
        currentFragment?.run {
            if (this !is PlaylistFragment) {
                currentPlaylistId = ""
            }
            if (this !is CollectionDetailFragment) {
                currentCollectionId = ""
            }
            if (shouldExplode) {
                exitTransition = createTransition(0)
                reenterTransition = createTransition(DetailFragment.TRANSITION_DELAY)
            } else {
                exitTransition = null
                reenterTransition = null
            }
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, DetailFragment.newInstance(songs, index, shouldShowManagePlaylist, clickedView == null))
            .apply {
                if (clickedView == null) {
                    setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                } else {
                    setReorderingAllowed(true)
                    addSharedElement(clickedView, clickedView.transitionName ?: "")
                }
            }
            .addToBackStack(null)
            .commit()
    }

    fun openSharedWithYouScreen(): String {
        supportFragmentManager.clearBackStack()
        supportFragmentManager.handleReplace("${SharedWithYouFragment::class.java.simpleName}-${System.currentTimeMillis()}") { SharedWithYouFragment.newInstance(intent) }
        currentScreenId = 0
        return AnalyticsManager.PARAM_VALUE_SCREEN_SHARED_WITH_YOU
    }

    fun restartProcess() {
        currentFragment?.showSnackbar(
            message = R.string.options_preferences_share_usage_data_restart_hint,
            actionText = R.string.options_preferences_share_usage_data_restart_action,
            action = { ProcessPhoenix.triggerRebirth(this, getStartIntent(this)) })
    }

    private fun updatePlaylists(playlists: List<Playlist>) {
        fun SubMenu.addPlaylistItem(index: Int, id: Int, title: String, shouldUseAddIcon: Boolean = false) =
            add(R.id.playlist_container, id, index, title).run {
                setIcon(if (shouldUseAddIcon) R.drawable.ic_new_playlist else R.drawable.ic_playlist)
            }

        val isLookingForUpdatedId = currentFragment is PlaylistFragment || currentFragment is DetailFragment
        playlistsContainerItem.run {
            clear()
            playlistIdMap.clear()
            playlists
                .asSequence()
                .sortedBy { it.order }
                .filter { it.id != viewModel.playlistRepository.hiddenPlaylistId }
                .forEachIndexed { index, playlist ->
                    val id = View.generateViewId()
                    playlistIdMap[id] = playlist.id
                    if (isLookingForUpdatedId && playlist.id == currentPlaylistId) {
                        currentScreenId = id
                        binding.primaryNavigation.run { post { setCheckedItem(id) } }
                    }
                    addPlaylistItem(index, id, playlist.title ?: getString(R.string.main_favorites))
                }
            if (playlists.size < Playlist.MAXIMUM_PLAYLIST_COUNT) {
                newPlaylistId = View.generateViewId()
                addPlaylistItem(playlists.size, newPlaylistId, getString(R.string.main_new_playlist), true)
            }
            setGroupCheckable(R.id.playlist_container, true, true)
            viewModel.appShortcutManager.updateAppShortcuts()
        }
    }

    private fun FragmentManager.clearBackStack() = repeat(backStackEntryCount) { popBackStackImmediate() }

    private inline fun <reified T : Fragment> FragmentManager.handleReplace(
        tag: String = T::class.java.name,
        crossinline newInstance: () -> T
    ) {
        currentFragment?.exitTransition = null
        beginTransaction()
            .replace(R.id.fragment_container, findFragmentByTag(tag) ?: newInstance.invoke(), tag)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .commit()
    }

    private inline fun consumeAndCloseDrawers(crossinline action: () -> Unit = {}) = consume {
        action()
        binding.drawerLayout.closeDrawers()
    }

    private fun NavigationView.disableScrollbars() {
        (getChildAt(0) as? NavigationMenuView)?.isVerticalScrollBarEnabled = false
    }

    private fun Context.tryToOpenIntent(intent: Intent) {
        try {
            startActivity(intent)
            isUiBlocked = true
        } catch (exception: ActivityNotFoundException) {
            currentFragment?.showSnackbar(R.string.options_about_error)
        }
    }

    companion object {

        private var Bundle.currentScreenId by BundleArgumentDelegate.Int("currentScreenId")
        private var Bundle.currentPlaylistId by BundleArgumentDelegate.String("currentPlaylistId")
        private var Bundle.currentCollectionId by BundleArgumentDelegate.String("currentCollectionId")
        private var Bundle.lastSongId by BundleArgumentDelegate.String("lastSongId")
        private var Bundle.lastCollectionId by BundleArgumentDelegate.String("lastCollectionId")
        private var Bundle.isFabVisible by BundleArgumentDelegate.Boolean("isFabVisible")

        private const val DIALOG_ID_EXIT_CONFIRMATION = 1
        private const val DIALOG_ID_PLAY_STORE_RATING = 2
        private const val APPROXIMATE_STARTUP_TIME = 400L
        const val SCREEN_HOME = "home"
        const val SCREEN_COLLECTIONS = "collections"
        const val SCREEN_SONGS = "songs"
        const val SCREEN_HISTORY = "history"
        const val SCREEN_OPTIONS = "options"
        const val SCREEN_MANAGE_PLAYLISTS = "managePlaylists"
        const val SCREEN_MANAGE_DOWNLOADS = "manageDownloads"

        private var Intent.screenToOpen by IntentExtraDelegate.String("screenToOpen")

        private fun getStartIntent(context: Context) = Intent(context, CampfireActivity::class.java)

        fun getHomeIntent(context: Context) = getStartIntent(context).apply { screenToOpen = SCREEN_HOME }

        fun getCollectionsIntent(context: Context) = getStartIntent(context).apply { screenToOpen = SCREEN_COLLECTIONS }

        fun getSongsIntent(context: Context) = getStartIntent(context).apply { screenToOpen = SCREEN_SONGS }

        fun getPlaylistIntent(context: Context, playlistId: String) = getStartIntent(context).apply { screenToOpen = playlistId }
    }
}