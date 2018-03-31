package com.pandulapeter.campfire.feature

import android.app.ActivityManager
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.support.v7.app.AppCompatActivity
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.ActivityCampfireBinding
import com.pandulapeter.campfire.old.util.color
import com.pandulapeter.campfire.old.util.consume

class MainActivity : AppCompatActivity() {
    private val homeFragment get() = supportFragmentManager.findFragmentById(R.id.fragment_container) as CampfireFragment
    private val binding by lazy { DataBindingUtil.setContentView<ActivityCampfireBinding>(this, R.layout.activity_campfire) }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.DarkTheme)
        @Suppress("ConstantConditionIf")
        setTaskDescription(
            ActivityManager.TaskDescription(
                getString(R.string.campfire) + if (BuildConfig.BUILD_TYPE == "release") "" else " (" + BuildConfig.BUILD_TYPE + ")",
                null, color(R.color.primary)
            )
        )
        super.onCreate(savedInstanceState)
        binding.viewModel = MainViewModel()
        binding.navigationViewStart.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.library -> consumeAndCloseDrawer { supportFragmentManager.handleReplace { LibraryFragment() } }
                R.id.settings -> consumeAndCloseDrawer { supportFragmentManager.handleReplace { SettingsFragment() } }
                else -> false
            }
        }
        if (savedInstanceState == null) {
            supportFragmentManager.handleReplace { LibraryFragment() }
        }
    }

    private inline fun <reified T : Fragment> FragmentManager.handleReplace(crossinline newInstance: () -> T) {
        beginTransaction()
            .replace(R.id.fragment_container, findFragmentByTag(T::class.java.name) ?: newInstance.invoke(), T::class.java.name)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commit()
    }

    private fun consumeAndCloseDrawer(action: () -> Unit) = consume {
        action()
        binding.drawerLayout.closeDrawers()
    }
}