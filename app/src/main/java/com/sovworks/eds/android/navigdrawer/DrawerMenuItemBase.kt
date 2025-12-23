package com.sovworks.eds.android.navigdrawer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.sovworks.eds.android.R


abstract class DrawerMenuItemBase
protected constructor(protected val drawerController: DrawerControllerBase?) {
    abstract val title: String

    open fun onClick(view: View, position: Int) {
        drawerController!!.closeDrawer()
    }

    open fun onLongClick(view: View?, position: Int): Boolean {
        return false
    }

    open fun onBackPressed(): Boolean {
        return false
    }

    open val icon: Drawable?
        get() = null

    open val viewType: Int
        get() = 0

    open fun saveState(state: Bundle) {
    }

    open fun restoreState(state: Bundle) {
    }

    fun createView(position: Int, parent: ViewGroup?): View {
        val inflater = drawerController.getMainActivity()
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        @SuppressLint("InflateParams") val v = inflater.inflate(layoutId, parent, false)
        updateView(v, position)
        return v
    }

    open fun updateView(view: View, position: Int) {
        val tv = view.findViewById<TextView>(android.R.id.text1)
        tv.text = title
        val iconView = view.findViewById<ImageView>(android.R.id.icon)
        if (iconView != null) {
            iconView.contentDescription = title
            val icon = icon
            if (icon == null) iconView.visibility = View.INVISIBLE
            else {
                iconView.visibility = View.VISIBLE
                iconView.setImageDrawable(icon)
            }
        }
    }

    fun updateView(): View? {
        val list = drawerController.getDrawerListView()
        val start = list!!.firstVisiblePosition
        var i = start
        val j = list!!.lastVisiblePosition
        while (i <= j) {
            if (this === list!!.getItemAtPosition(i)) return adapter.getView(
                i, list!!.getChildAt(i - start),
                list!!
            )
            i++
        }
        return null
    }

    override fun toString(): String {
        return title
    }

    protected open val layoutId: Int
        get() = R.layout.drawer_item

    protected val adapter: ArrayAdapter<DrawerMenuItemBase>
        get() = drawerController.getDrawerListView().adapter

    protected val positionInAdapter: Int
        get() = adapter.getPosition(this) /*
        ArrayAdapter<?> adapter = getAdapter();
        for(int i=0;i<adapter.getCount();i++)
            if(adapter.getItem(i) == this)
                return i;
        return -1;*/

    protected val context: Context?
        get() = drawerController.getMainActivity()
}
