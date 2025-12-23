package com.sovworks.eds.android.navigdrawer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity.Companion.openFileManager
import com.sovworks.eds.android.filemanager.fragments.FileListDataFragment
import com.sovworks.eds.android.filemanager.fragments.FileListDataFragment.HistoryItem
import com.sovworks.eds.android.filemanager.fragments.FileListViewFragment
import com.sovworks.eds.android.filemanager.fragments.FileListViewFragmentBase
import com.sovworks.eds.android.locations.activities.LocationSettingsActivity
import com.sovworks.eds.android.locations.closer.fragments.LocationCloserBaseFragment
import com.sovworks.eds.android.locations.closer.fragments.LocationCloserBaseFragment.Companion.getCloserTag
import com.sovworks.eds.android.locations.closer.fragments.LocationCloserBaseFragment.Companion.getDefaultCloserForLocation
import com.sovworks.eds.android.locations.opener.fragments.LocationOpenerBaseFragment
import com.sovworks.eds.android.locations.opener.fragments.LocationOpenerBaseFragment.Companion.getOpenerTag
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager

open class DrawerLocationMenuItem(
    private val _location: Location,
    drawerController: DrawerControllerBase?
) :
    DrawerMenuItemBase(drawerController) {
    class Opener : LocationOpenerBaseFragment() {
        public override fun onLocationOpened(location: Location?) {
            val args = arguments
            openFileManager(
                (activity as FileManagerActivity),
                location, args?.getInt(FileListViewFragment.ARG_SCROLL_POSITION, 0) ?: 0
            )
        }
    }

    open val location: Location?
        get() = _location

    override val title: String
        get() = _location.title

    override val viewType: Int
        get() = 2

    override fun updateView(view: View, position: Int) {
        super.updateView(view, position)
        val iv = view.findViewById<ImageView>(R.id.close)
        if (iv != null) {
            if (LocationsManager.isOpenableAndOpen(_location)) {
                iv.visibility = View.VISIBLE
                iv.setOnClickListener(_closeIconClickListener)
            } else iv.visibility = View.INVISIBLE
        }
    }

    override fun onClick(view: View, position: Int) {
        openLocation()
        super.onClick(view, position)
    }

    override fun onLongClick(view: View?, position: Int): Boolean {
        if (hasSettings()) {
            openLocationSettings()
            return true
        }
        return false
    }

    fun openLocation() {
        val fm = drawerController.mainActivity.fragmentManager
        val openerTag = getOpenerTag(_location)
        if (fm.findFragmentByTag(openerTag) == null) {
            val opener = opener
            opener.arguments = openerArgs
            fm.beginTransaction().add(opener, openerTag).commit()
        }
    }

    fun closeLocation() {
        val fm = drawerController.mainActivity.fragmentManager
        val closerTag = getCloserTag(_location)
        if (fm.findFragmentByTag(closerTag) == null) {
            val closer = closer
            closer.arguments = closerArgs
            fm.beginTransaction().add(closer, closerTag).commit()
        }
    }

    override val layoutId: Int
        get() = R.layout.drawer_location_item

    protected open val closer: LocationCloserBaseFragment
        get() = getDefaultCloserForLocation(_location)

    protected open val opener: LocationOpenerBaseFragment
        get() = Opener()

    protected val openerArgs: Bundle
        get() {
            val b = Bundle()
            val hi = findPrevLocation(_location)
            if (hi == null) LocationsManager.storePathsInBundle(b, _location, null)
            else {
                b.putParcelable(LocationsManager.PARAM_LOCATION_URI, hi.locationUri)
                b.putInt(FileListViewFragmentBase.ARG_SCROLL_POSITION, hi.scrollPosition)
            }
            return b
        }

    protected val closerArgs: Bundle
        get() {
            val b = Bundle()
            LocationsManager.storePathsInBundle(b, _location, null)
            return b
        }

    protected fun openLocationSettings() {
        val i = Intent(context, LocationSettingsActivity::class.java)
        LocationsManager.storePathsInIntent(i, _location, null)
        context.startActivity(i)
    }

    protected open fun hasSettings(): Boolean {
        return false
    }

    private val _closeIconClickListener =
        View.OnClickListener { v: View? -> closeLocation() }

    private fun findPrevLocation(loc: Location): HistoryItem? {
        val df: FileListDataFragment? = drawerController.getMainActivity
        ().getFragmentManager
        ().findFragmentByTag
        (FileListDataFragment.TAG) as FileListDataFragment
        if (df != null) {
            val hist = df.navigHistory
            val locId = loc.id
            if (locId != null) for (i in hist.indices.reversed()) {
                val hi = hist[i]
                if (locId == hi.locationId) return hi
            }
        }
        return null
    }
}
