package com.pandulapeter.campfire.feature

import android.app.ActivityManager
import android.content.Context
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.ActivityCampfireBinding
import com.pandulapeter.campfire.feature.library.LibraryFragment
import com.pandulapeter.campfire.feature.settings.SettingsFragment
import com.pandulapeter.campfire.util.addDrawerListener
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.hideKeyboard

class CampfireActivity : AppCompatActivity() {
    private val binding by lazy { DataBindingUtil.setContentView<ActivityCampfireBinding>(this, R.layout.activity_campfire) }
    private val currentFragment get() = supportFragmentManager.findFragmentById(R.id.fragment_container) as CampfireFragment<*>
    val floatingActionButton get() = binding.floatingActionButton
    val toolbarContext: Context get() = binding.toolbar.context

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.LightTheme)
        @Suppress("ConstantConditionIf")
        setTaskDescription(
            ActivityManager.TaskDescription(
                getString(R.string.campfire) + if (BuildConfig.BUILD_TYPE == "release") "" else " (" + BuildConfig.BUILD_TYPE + ")",
                null, color(R.color.primary)
            )
        )
        super.onCreate(savedInstanceState)
        binding.drawerLayout.addDrawerListener(onDrawerStateChanged = {
            if (it == DrawerLayout.STATE_DRAGGING) {
                currentFocus?.also {
                    it.clearFocus()
                    hideKeyboard(it)
                }
            }
        })
        binding.primaryNavigation.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.library -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { LibraryFragment() } }
                R.id.settings -> consumeAndCloseDrawers { supportFragmentManager.handleReplace { SettingsFragment() } }
                else -> false
            }
        }
        if (savedInstanceState == null) {
            supportFragmentManager.handleReplace { LibraryFragment() }
        }
        binding.toolbarMainButton.setOnClickListener { binding.drawerLayout.openDrawer(Gravity.START) }
    }

    override fun onResume() {
        super.onResume()
        binding.drawerLayout.run { post { closeDrawers() } }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(Gravity.START)) {
            binding.drawerLayout.closeDrawer(Gravity.START)
        } else {
            if (binding.drawerLayout.isDrawerOpen(Gravity.END)) {
                binding.drawerLayout.closeDrawer(Gravity.END)
            } else {
                if (!currentFragment.onBackPressed()) {
                    super.onBackPressed()
                }
            }
        }
    }

    fun openSecondaryNavigationDrawer() {
        hideKeyboard(currentFocus)
        binding.drawerLayout.openDrawer(Gravity.END)
    }

    fun changeToolbarTitle(toolbar: View) {
        binding.toolbarTitleContainer.removeAllViews()
        binding.toolbarTitleContainer.addView(toolbar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.CENTER_VERTICAL
        })
    }

    fun changeToolbarButtons(buttons: List<View>) {
        binding.toolbarButtonContainer.removeAllViews()
        buttons.forEach {
            binding.toolbarButtonContainer.addView(it, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private inline fun <reified T : Fragment> FragmentManager.handleReplace(crossinline newInstance: () -> T) {
        currentFocus?.also { hideKeyboard(it) }
        beginTransaction()
            .replace(R.id.fragment_container, findFragmentByTag(T::class.java.name) ?: newInstance.invoke(), T::class.java.name)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commit()
    }

    private inline fun consumeAndCloseDrawers(crossinline action: () -> Unit) = consume {
        action()
        binding.drawerLayout.closeDrawers()
    }
}