package com.pandulapeter.campfire.feature

import android.content.Context
import android.databinding.DataBindingUtil
import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.annotation.CallSuper
import android.support.annotation.LayoutRes
import android.support.annotation.StringRes
import android.support.v4.app.Fragment
import android.support.v7.widget.AppCompatTextView
import android.text.Spannable
import android.text.SpannableString
import android.text.style.TextAppearanceSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.shared.EllipsizeLineSpan
import com.pandulapeter.campfire.util.obtainColor

abstract class CampfireFragment<T : ViewDataBinding>(@LayoutRes private var layoutResourceId: Int) : Fragment() {
    protected open val onFloatingActionButtonClicked: (() -> Unit)? = null
    private val toolbarTitle by lazy { inflateToolbarTitle(mainActivity.toolbarContext) }
    protected lateinit var binding: T
    protected val mainActivity get() = (activity as? CampfireActivity) ?: throw IllegalStateException("The Fragment is not attached to CampfireActivity.")

    final override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, layoutResourceId, container, false)
        return binding.root
    }

    @CallSuper
    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        mainActivity.changeToolbarTitle(toolbarTitle)
        mainActivity.changeToolbarButtons(inflateToolbarButtons(mainActivity.toolbarContext))
        mainActivity.floatingActionButton.run {
            setOnClickListener { onFloatingActionButtonClicked?.invoke() }
            if (onFloatingActionButtonClicked == null) hide() else show()
        }
    }

    protected open fun inflateToolbarTitle(context: Context): View = AppCompatTextView(context).apply { gravity = Gravity.CENTER_VERTICAL }

    protected open fun inflateToolbarButtons(context: Context): List<View> = listOf()

    protected fun updateToolbarTitle(@StringRes titleRes: Int, subtitle: String? = null) = updateToolbarTitle(context.getString(titleRes), subtitle)

    private fun updateToolbarTitle(title: String, subtitle: String? = null) {
        (toolbarTitle as? TextView)?.let {
            mainActivity.toolbarContext.let { context ->
                it.text = SpannableString("$title${subtitle?.let { "\n$it" } ?: ""}").apply {
                    setSpan(TextAppearanceSpan(context, R.style.TextAppearance_AppCompat_Title), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(EllipsizeLineSpan(context.obtainColor(android.R.attr.textColorPrimary)), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    subtitle?.let {
                        setSpan(EllipsizeLineSpan(context.obtainColor(android.R.attr.textColorSecondary)), title.length + 1, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }
    }
}