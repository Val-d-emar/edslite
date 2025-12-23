package com.sovworks.eds.android.navigdrawer

import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.drawerlayout.widget.DrawerLayout
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity

abstract class DrawerControllerBase
    (val mainActivity: FileManagerActivity) {
    fun init(savedState: Bundle?) {
        drawerLayout = mainActivity.findViewById(R.id.drawer_layout)
        drawerListView = mainActivity.findViewById(R.id.left_drawer)

        _drawerToggle = ActionBarDrawerToggle(
            mainActivity,  /* host Activity */
            drawerLayout,  /* DrawerLayout object */
            R.string.drawer_open,  /* "open drawer" description */
            R.string.drawer_close /* "close drawer" description */
        )

        // Set the drawer toggle as the DrawerListener
        drawerLayout.setDrawerListener(_drawerToggle)

        val ab = mainActivity.actionBar
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true)
            ab.setHomeButtonEnabled(true)
        }

        val list = fillDrawer()

        drawerListView.setChoiceMode(ListView.CHOICE_MODE_NONE)

        if (savedState != null) {
            val copy = ArrayList(list)
            for (item in copy) item.restoreState(savedState)
        }
        drawerListView.setOnItemClickListener(OnItemClickListener { adapterView: AdapterView<*>?, view: View, i: Int, l: Long ->
            val item = drawerListView.getItemAtPosition(i) as DrawerMenuItemBase
            item?.onClick(view, i)
        })
        drawerListView.setOnItemLongClickListener(OnItemLongClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
            val item = drawerListView.getItemAtPosition(position) as DrawerMenuItemBase
            item != null && item.onLongClick(view, position)
        })
    }

    fun onPostCreate() {
        if (_drawerToggle != null) _drawerToggle!!.syncState()
    }

    fun onConfigurationChanged(newConfig: Configuration?) {
        if (_drawerToggle != null) _drawerToggle!!.onConfigurationChanged(newConfig)
    }

    fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerLayout == null) return false

        if (item.itemId == android.R.id.home) {
            if (drawerLayout!!.isDrawerOpen(drawerListView!!)) drawerLayout!!.closeDrawer(
                drawerListView!!
            )
            else drawerLayout!!.openDrawer(drawerListView!!)
            return true
        }
        return false
    }

    fun closeDrawer() {
        drawerLayout!!.closeDrawer(drawerListView!!)
    }

    private fun openDrawer() {
        drawerLayout!!.openDrawer(drawerListView!!)
    }

    fun onBackPressed(): Boolean {
        if (drawerListView == null || !drawerLayout!!.isDrawerOpen(drawerListView!!)) return false
        for (i in 0..<drawerListView!!.count) {
            val item = drawerListView!!.getItemAtPosition(i) as DrawerMenuItemBase
            if (item != null && item.onBackPressed()) return true
        }
        drawerLayout!!.closeDrawer(drawerListView!!)
        return true
    }

    fun onSaveInstanceState(outState: Bundle) {
        if (drawerListView == null) return
        saveState(outState)
    }

    fun updateMenuItemViews() {
        val lv = drawerListView
        if (lv != null) {
            val adapter = lv.adapter as DrawerAdapter
            adapter.notifyDataSetChanged()
        }
    }

    fun reloadItems() {
        if (drawerListView == null) return
        val b = Bundle()
        saveState(b)
        val list = fillDrawer()
        val copy = ArrayList(list)
        for (item in copy) item.restoreState(b)
    }

    fun showContainers() {
        openDrawer()
        val da = drawerListView!!.adapter as DrawerAdapter
        var i = 0
        val l = da.count
        while (i < l) {
            val item = da.getItem(i)
            if (item is DrawerContainersMenu) {
                val dcm = item
                if (!dcm.isExpanded) dcm.rotateIconAndChangeState(
                    da.getView(
                        i, dcm.findView(
                            drawerListView!!
                        ),
                        drawerListView!!
                    )
                )
            }
            i++
        }
    }

    protected open fun fillDrawer(): List<DrawerMenuItemBase> {
        val i = mainActivity.intent
        val isSelectAction = mainActivity.isSelectAction
        val list = ArrayList<DrawerMenuItemBase>()
        val adapter = DrawerAdapter(list)
        if (i.getBooleanExtra(FileManagerActivity.EXTRA_ALLOW_BROWSE_CONTAINERS, true)) adapter.add(
            DrawerContainersMenu(
                this
            )
        )
        if (i.getBooleanExtra(FileManagerActivity.EXTRA_ALLOW_BROWSE_DEVICE, true)) adapter.add(
            DrawerLocalFilesMenu(
                this
            )
        )
        if (!isSelectAction) {
            adapter.add(DrawerSettingsMenuItem(this))
            adapter.add(DrawerHelpMenuItem(this))
            adapter.add(DrawerAboutMenuItem(this))
            adapter.add(DrawerExitMenuItem(this))
        }
        drawerListView!!.adapter = adapter
        return list
    }

    protected inner class DrawerAdapter internal constructor(itemsList: List<DrawerMenuItemBase>) :
        ArrayAdapter<DrawerMenuItemBase?>(mainActivity, R.layout.drawer_folder, itemsList) {
        override fun getItemViewType(position: Int): Int {
            val rec = getItem(position)
            return rec?.viewType ?: 0
        }

        override fun getViewTypeCount(): Int {
            return 4
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val rec = getItem(position)
            val v: View
            if (convertView != null) {
                v = convertView
                rec!!.updateView(v, position)
            } else v = rec!!.createView(position, parent)
            v.tag = rec
            return v
        }
    }

    var drawerListView: ListView? = null
        private set

    @get:Suppress("unused")
    var drawerLayout: DrawerLayout? = null
        private set

    @Suppress("deprecation")
    private var _drawerToggle: ActionBarDrawerToggle? = null

    private fun saveState(outState: Bundle) {
        for (i in 0..<drawerListView!!.count) {
            val item = drawerListView!!.getItemAtPosition(i) as DrawerMenuItemBase
            item?.saveState(outState)
        }
    }
}
