package com.sovworks.eds.android.settings

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.PropertyEditor.Host

abstract class CategoryPropertyEditor protected constructor(
    host: Host?,
    titleResId: Int,
    descResId: Int
) :
    PropertyEditorBase(host, R.layout.settings_category, titleResId, descResId) {
    public override fun createView(parent: ViewGroup?): View {
        val view = super.createView(parent)
        _indicatorIcon = view!!.findViewById(android.R.id.icon)
        _indicatorIcon.setRotation((if (isExpanded) 180 else 0).toFloat())
        return view
    }

    override fun onClick() {
        rotateIconAndChangeState()
    }

    override fun save(b: Bundle) {
        b.putBoolean(bundleKey, isExpanded)
    }

    override fun save() {
    }

    override fun load(b: Bundle) {
        if (b.getBoolean(bundleKey, false)) expand()
        else collapse()
    }

    fun collapse() {
        isExpanded = false
        load()
        if (_indicatorIcon != null) _indicatorIcon!!.rotation = 0f
    }

    fun expand() {
        isExpanded = true
        load()
        if (_indicatorIcon != null) _indicatorIcon!!.rotation = 180f
    }

    private var _indicatorIcon: ImageView? = null
    var isExpanded: Boolean = false
        private set


    private fun rotateIconAndChangeState() {
        IS_ANIMATING = true
        _indicatorIcon!!.clearAnimation()
        val anim = ObjectAnimator.ofFloat(
            _indicatorIcon,
            View.ROTATION,
            (if (isExpanded) 0 else 180).toFloat()
        )
        anim.setDuration(200)
        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) _indicatorIcon!!.setHasTransientState(true)
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (this.isExpanded) collapse()
                else expand()
                if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) _indicatorIcon!!.setHasTransientState(
                    false
                )
                IS_ANIMATING = false
            }
        })
        anim.start()
    }

    companion object {
        var IS_ANIMATING: Boolean = false
    }
}
