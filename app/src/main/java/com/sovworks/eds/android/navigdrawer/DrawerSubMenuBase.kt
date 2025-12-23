package com.sovworks.eds.android.navigdrawer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.sovworks.eds.android.R

abstract class DrawerSubMenuBase protected constructor(drawerController: DrawerControllerBase?) :
    DrawerMenuItemBase(drawerController) {
    override fun onClick(view: View, position: Int) {
        rotateIconAndChangeState(view)
    }

    override fun saveState(state: Bundle) {
        if (isExpanded) state.putInt(
            STATE_EXPANDED_POSITION,
            positionInAdapter
        )
    }

    override fun restoreState(state: Bundle) {
        val expPos = state.getInt(STATE_EXPANDED_POSITION, -1)
        if (expPos >= 0 && expPos == positionInAdapter) expand()
    }

    override fun onBackPressed(): Boolean {
        if (isExpanded) {
            collapse()
            return true
        }
        return false
    }

    @SuppressLint("NewApi")
    override fun updateView(view: View, position: Int) {
        super.updateView(view, position)
        val tv = view.findViewById<View>(android.R.id.text1) as TextView
        tv.isPressed = isExpanded
        val drawable = view.background
        drawable?.setState(
            if (isExpanded) intArrayOf(android.R.attr.state_expanded) else IntArray(
                0
            )
        )
        val iv = view.findViewById<View>(android.R.id.icon) as ImageView
        if (iv != null && (VERSION.SDK_INT < VERSION_CODES.JELLY_BEAN || !iv.hasTransientState())) {
            iv.visibility = View.VISIBLE
            iv.rotation = (if (isExpanded) 180 else 0).toFloat()
        }
    }

    fun rotateIcon(view: View) {
        val icon = view.findViewById<View>(android.R.id.icon) as ImageView //getIconImageView();
        if (icon != null) {
            icon.clearAnimation()
            val anim =
                ObjectAnimator.ofFloat(icon, View.ROTATION, (if (isExpanded) 0 else 180).toFloat())
            anim.setDuration(200)
            if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) icon.setHasTransientState(true)
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) icon.setHasTransientState(false)
                }
            })
            anim.start()
        }
    }

    fun rotateIconAndChangeState(view: View) {
        if (!isExpanded) rotateExpandedIcons()
        val icon = view.findViewById<View>(android.R.id.icon) as ImageView //getIconImageView();
        if (icon != null) {
            IS_ANIMATING = true
            icon.clearAnimation()
            val anim =
                ObjectAnimator.ofFloat(icon, View.ROTATION, (if (isExpanded) 0 else 180).toFloat())
            anim.setDuration(200)
            if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) icon.setHasTransientState(true)
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (this.isExpanded) collapse()
                    else {
                        collapseAll()
                        expand()
                    }
                    adapter.notifyDataSetChanged()
                    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) icon.setHasTransientState(false)
                    IS_ANIMATING = false
                }
            })
            anim.start()
        } else {
            if (isExpanded) collapse()
            else expand()
        }
    }

    protected abstract val subItems: Collection<DrawerMenuItemBase>

    override val layoutId: Int
        get() = R.layout.drawer_folder

    override val viewType: Int
        get() = 1

    fun findView(list: ListView): View? {
        var i = 0
        val n = list.childCount
        while (i < n) {
            val v = list.getChildAt(i)
            if (v.tag === this) return v
            i++
        }
        /*int start = list.getFirstVisiblePosition();
        for (int i = start, j = list.getLastVisiblePosition(); i <= j; i++)
            if (this == list.getItemAtPosition(i))
                return list.getChildAt(i - start);*/
        return null
    }

    protected fun collapse() {
        isExpanded = false
        val adapter = adapter
        if (_subItems != null) {
            for (sub in _subItems!!) adapter!!.remove(sub)
        }
    }

    protected fun expand() {
        isExpanded = true
        val adapter = adapter
        _subItems = subItems
        if (_subItems != null) {
            var pos = positionInAdapter
            if (pos >= 0) for (sub in _subItems!!) adapter!!.insert(sub, ++pos)
        }
    }

    var isExpanded: Boolean = false
        private set


    private var _subItems: Collection<DrawerMenuItemBase>? = null

    private fun rotateExpandedIcons() {
        val lv = drawerController.drawerListView
        for (i in 0..<lv!!.count) {
            val di = lv.getItemAtPosition(i)
            if (di is DrawerSubMenuBase && di.isExpanded) {
                val v = di.findView(lv)
                if (v != null) di.rotateIcon(v)
            }
        }
    }

    private fun collapseAll() {
        val lv = drawerController.drawerListView
        for (i in 0..<lv!!.count) {
            val di = lv.getItemAtPosition(i)
            if (di is DrawerSubMenuBase && di.isExpanded) di.collapse()
        }
    } /*private ImageView getIconImageView()
    {
        ListView list = getDrawerController().getDrawerListView();
        int start = list.getFirstVisiblePosition();
        for(int i=start, j=list.getLastVisiblePosition();i<=j;i++)
            if(this == list.getItemAtPosition(i))
            {
                View v = getAdapter().getView(i, list.getChildAt(i - start), list);
                if(v!=null)
                    return (ImageView) v.findViewById(android.R.id.icon);
            }
        return null;
    }*/


    companion object {
        var IS_ANIMATING: Boolean = false
        private const val STATE_EXPANDED_POSITION =
            "com.sovworks.eds.android.navigdrawer.DrawerSubMenuBase.EXPANDED_POSITION"
    }
}
