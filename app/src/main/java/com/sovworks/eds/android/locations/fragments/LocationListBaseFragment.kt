package com.sovworks.eds.android.locations.fragments

import android.annotation.SuppressLint
import android.app.ListFragment
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.ActionMode
import android.view.ActionMode.Callback
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.activities.CreateLocationActivity
import com.sovworks.eds.android.locations.activities.LocationSettingsActivity
import com.sovworks.eds.android.locations.closer.fragments.LocationCloserBaseFragment.Companion.getCloserTag
import com.sovworks.eds.android.locations.closer.fragments.LocationCloserBaseFragment.Companion.getDefaultCloserForLocation
import com.sovworks.eds.android.locations.dialogs.RemoveLocationConfirmationDialog.Companion.showDialog
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager

abstract class LocationListBaseFragment : ListFragment() {
    open inner class LocationInfo {
        var location: Location? = null
        var isSelected: Boolean = false
        open fun hasSettings(): Boolean {
            return false
        }

        open val icon: Drawable?
            get() = null

        fun allowRemove(): Boolean {
            return true
        }
    }

    inner class ListViewAdapter internal constructor(
        context: Context,
        backingList: List<LocationInfo?>
    ) :
        ArrayAdapter<LocationInfo?>(context, R.layout.locations_list_row, backingList) {
        @SuppressLint("InflateParams")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v: View
            if (convertView == null) {
                val inflater = LayoutInflater.from(context)
                v = inflater.inflate(R.layout.locations_list_row, null)
            } else v = convertView

            val item = getItem(position)
            v.tag = item
            if (item == null) return v
            v.setBackgroundColor(if (item.isSelected) _selectedItemBackgroundColor else _notSelectedItemBackground)

            //Drawable back = v.getBackground();
            //if(back!=null)
            //    back.setState(item.isSelected ? new int[]{android.R.attr.state_focused } : new int[0]);
            val tv = v.findViewById<TextView>(android.R.id.text1)
            tv.text = item.location!!.title
            val iv = v.findViewById<ImageView>(android.R.id.icon)
            iv?.setImageDrawable(item.icon)
            return v
        }

        private val _selectedItemBackgroundColor: Int
        private val _notSelectedItemBackground: Int

        init {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(R.attr.selectedItemBackground, typedValue, true)
            _selectedItemBackgroundColor = context.resources.getColor(typedValue.resourceId)
            _notSelectedItemBackground = context.resources.getColor(android.R.color.transparent)
        }
    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(true)
        setEmptyText(emptyText)
        _locationsList = initAdapter()
        loadLocations()
        if (savedInstanceState != null) restoreSelection(savedInstanceState)

        if (haveSelectedLocations()) startSelectionMode()

        initListView()
        setListShown(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val selectedLocations =
            selectedLocations
        if (!selectedLocations.isEmpty()) LocationsManager.storeLocationsInBundle(
            outState,
            selectedLocations
        )
    }

    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater)
        menuInflater.inflate(R.menu.location_list_menu, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.add).setVisible(defaultLocationType != null)
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        val mhi = MenuHandlerInfo()
        mhi.menuItemId = menuItem.itemId
        val res = handleMenu(mhi)
        if (res && mhi.clearSelection) clearSelectedFlag()
        return res || super.onOptionsItemSelected(menuItem)
    }

    override fun onResume() {
        super.onResume()
        activity.registerReceiver(
            _reloadLocationsReceiver,
            IntentFilter(LocationsManager.BROADCAST_LOCATION_CHANGED)
        )
        activity.registerReceiver(
            _reloadLocationsReceiver,
            IntentFilter(LocationsManager.BROADCAST_LOCATION_REMOVED)
        )
        activity.registerReceiver(
            _reloadLocationsReceiver,
            IntentFilter(LocationsManager.BROADCAST_LOCATION_CREATED)
        )
        loadLocations()
    }

    override fun onPause() {
        super.onPause()
        activity.unregisterReceiver(_reloadLocationsReceiver)
    }

    open fun removeLocation(loc: Location) {
        LocationsManager.getLocationsManager(activity).removeLocation(loc)
        UserSettings.getSettings(activity).setLocationSettingsString(loc.id, null)
        LocationsManager.broadcastLocationRemoved(activity, loc)
    }

    protected var _locationsList: ListViewAdapter? = null

    protected class MenuHandlerInfo {
        var menuItemId: Int = 0
        var clearSelection: Boolean = false
    }

    protected abstract fun loadLocations()

    protected val selectedLocationInfo: LocationInfo?
        get() {
            for (i in 0..<_locationsList!!.count) {
                val li =
                    _locationsList!!.getItem(i)
                if (li != null && li.isSelected) return li
            }
            return null
        }

    protected val selectedLocations: ArrayList<Location?>
        get() {
            val res =
                ArrayList<Location?>()
            for (i in 0..<_locationsList!!.count) {
                val li =
                    _locationsList!!.getItem(i)
                if (li != null && li.isSelected) res.add(li.location)
            }
            return res
        }

    protected fun haveSelectedLocations(): Boolean {
        return !selectedLocations.isEmpty()
    }

    protected val emptyText: String
        get() = getString(R.string.list_is_empty)

    protected fun selectLocation(li: LocationInfo) {
        if (!haveSelectedLocations()) {
            li.isSelected = true
            startSelectionMode()
        } else if (isSingleSelectionMode) {
            clearSelectedFlag()
            li.isSelected = true
        }
        onSelectionChanged()
        updateRowView(listView, getItemPosition(li))
    }

    protected val isSingleSelectionMode: Boolean
        get() = true

    protected fun unselectLocation(li: LocationInfo) {
        li.isSelected = false
        if (!haveSelectedLocations()) stopSelectionMode()
        onSelectionChanged()
    }

    protected fun onLocationClicked(li: LocationInfo) {
        if (li.isSelected) unselectLocation(li)
        else selectLocation(li)
    }

    protected open val defaultLocationType: String?
        get() = null

    protected fun closeLocation(loc: Location) {
        val args = Bundle()
        //args.putString(LocationCloserBaseFragment.PARAM_RECEIVER_FRAGMENT_TAG, getTag());
        LocationsManager.storePathsInBundle(args, loc, null)
        val closer = getDefaultCloserForLocation(loc)
        closer.arguments = args
        fragmentManager.beginTransaction().add(closer, getCloserTag(loc)).commit()
    }

    protected val contextMenuId: Int
        get() = R.menu.location_context_menu

    protected fun handleMenu(mhi: MenuHandlerInfo): Boolean {
        when (mhi.menuItemId) {
            R.id.add -> {
                addNewLocation(defaultLocationType)
                return true
            }

            R.id.settings -> {
                openSelectedLocationSettings()
                mhi.clearSelection = true
                return true
            }

            R.id.remove -> {
                removeSelectedLocation()
                mhi.clearSelection = true
                return true
            }

            R.id.close -> {
                closeSelectedLocation()
                mhi.clearSelection = true
                return true
            }

            else -> return false
        }
    }

    protected fun prepareContextMenu(selectedLocationInfo: LocationInfo, menu: Menu): Boolean {
        val sl = selectedLocationInfo.location
        var mi = menu.findItem(R.id.close)
        val closeVisible = LocationsManager.isOpenableAndOpen(sl)
        mi.setVisible(closeVisible)
        mi = menu.findItem(R.id.remove)
        mi.setVisible(!closeVisible && selectedLocationInfo.allowRemove())
        mi = menu.findItem(R.id.settings)
        mi.setVisible(selectedLocationInfo.hasSettings())
        return true
    }

    protected open fun addNewLocation(locationType: String?) {
        val i = Intent(activity, CreateLocationActivity::class.java)
        i.putExtra(CreateLocationActivity.EXTRA_LOCATION_TYPE, locationType)
        startActivity(i)
    }

    private val _reloadLocationsReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadLocations()
        }
    }

    private var _actionMode: ActionMode? = null

    private fun openSelectedLocationSettings() {
        openLocationSettings(selectedLocationInfo!!)
    }

    private fun removeSelectedLocation() {
        showDialog(
            fragmentManager,
            selectedLocationInfo!!.location!!
        )
    }

    private fun closeSelectedLocation() {
        closeLocation(selectedLocationInfo!!.location!!)
    }

    private fun restoreSelection(state: Bundle) {
        if (state.containsKey(LocationsManager.PARAM_LOCATION_URIS)) {
            try {
                val selectedLocations = LocationsManager.getLocationsManager(
                    activity
                ).getLocationsFromBundle(state)
                for (loc in selectedLocations) for (i in 0..<_locationsList!!.count) {
                    val li = _locationsList!!.getItem(i)
                    if (li != null && li.location!!.locationUri == loc.locationUri) li.isSelected =
                        true
                }
            } catch (e: Exception) {
                Logger.showAndLog(activity, e)
            }
        }
    }

    private fun clearSelectedFlag() {
        val lv = listView
        var i = 0
        val count = lv.count
        while (i < count) {
            val li = lv.getItemAtPosition(i) as LocationInfo
            if (li.isSelected) {
                li.isSelected = false
                updateRowView(lv, i)
            }
            i++
        }
    }

    private fun getItemPosition(li: LocationInfo): Int {
        val lv = listView
        var i = 0
        val n = lv.count
        while (i < n) {
            val info = lv.getItemAtPosition(i) as LocationInfo
            if (li === info) return i
            i++
        }
        return -1
    }

    private fun updateRowView(lv: ListView, pos: Int) {
        val start = lv.firstVisiblePosition
        if (pos >= start && pos <= lv.lastVisiblePosition) {
            val view = lv.getChildAt(pos - start)
            lv.adapter.getView(pos, view, lv)
        }
    }

    private fun initAdapter(): ListViewAdapter {
        return ListViewAdapter(activity, ArrayList())
    }

    private fun initListView() {
        val lv = listView
        lv.choiceMode = ListView.CHOICE_MODE_NONE
        lv.itemsCanFocus = true

        lv.onItemLongClickListener =
            OnItemLongClickListener { adapterView, view, pos, itemId ->
                val rec =
                    adapterView.getItemAtPosition(pos) as LocationInfo
                if (rec != null) selectLocation(rec)
                true
            }
        lv.onItemClickListener = object : OnItemClickListener {
            override fun onItemClick(
                adapterView: AdapterView<*>,
                view: View,
                pos: Int,
                l: Long
            ) {
                val rec =
                    adapterView.getItemAtPosition(pos) as LocationInfo
                if (rec != null) {
                    if (rec.isSelected && !this.isSingleSelectionMode) unselectLocation(rec)
                    else if (haveSelectedLocations()) selectLocation(rec)
                    else onLocationClicked(rec)
                }
            }
        }
        lv.adapter = _locationsList
    }

    private fun startSelectionMode() {
        _actionMode = listView.startActionMode(object : Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                mode.menuInflater.inflate(this.contextMenuId, menu)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                if (!haveSelectedLocations()) {
                    mode.finish()
                    return true
                }
                return prepareContextMenu(this.selectedLocationInfo, menu)
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val mhi = MenuHandlerInfo()
                mhi.menuItemId = item.itemId
                val res = handleMenu(mhi)
                if (res && mhi.clearSelection) mode.finish()

                return res
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                clearSelectedFlag()
                _actionMode = null
            }
        })
    }

    private fun stopSelectionMode() {
        if (_actionMode != null) {
            _actionMode!!.finish()
            _actionMode = null
        }
    }

    private fun onSelectionChanged() {
        if (_actionMode != null) _actionMode!!.invalidate()
        activity.invalidateOptionsMenu()
    }

    private fun openLocationSettings(li: LocationInfo) {
        val i = Intent(activity, LocationSettingsActivity::class.java)
        LocationsManager.storePathsInIntent(i, li.location, null)
        startActivity(i)
    }

    companion object {
        const val TAG: String = "LocationListBaseFragment"
    }
}
