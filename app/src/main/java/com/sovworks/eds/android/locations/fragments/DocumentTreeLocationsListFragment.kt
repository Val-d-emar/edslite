package com.sovworks.eds.android.locations.fragments

import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build.VERSION_CODES
import android.util.TypedValue
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.fs.DocumentTreeFS.DocumentPath
import com.sovworks.eds.android.locations.DocumentTreeLocation
import com.sovworks.eds.android.locations.fragments.LocationListBaseFragment.LocationInfo
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager

@TargetApi(VERSION_CODES.LOLLIPOP)
class DocumentTreeLocationsListFragment : LocationListBaseFragment() {
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == REQUEST_CODE_ADD_LOCATION) {
            if (resultCode == Activity.RESULT_OK) {
                val treeUri = data.data
                try {
                    activity.contentResolver.takePersistableUriPermission(
                        treeUri!!,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    Logger.log(e)
                }

                val loc = DocumentTreeLocation(activity.applicationContext, treeUri)
                loc.externalSettings.isVisibleToUser = true
                loc.saveExternalSettings()
                LocationsManager.getLocationsManager(activity).addNewLocation(loc, true)
                LocationsManager.broadcastLocationAdded(activity, loc)
            }
        } else super.onActivityResult(requestCode, resultCode, data)
    }

    override fun removeLocation(loc: Location) {
        val tl = loc as DocumentTreeLocation
        try {
            val p = tl.fs.rootPath as DocumentPath
            activity.contentResolver.releasePersistableUriPermission(
                p.documentUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Logger.log(e)
        } catch (e: Exception) {
            Logger.showAndLog(activity, e)
        }
        super.removeLocation(loc)
    }

    override fun loadLocations() {
        _locationsList!!.clear()
        for (loc in LocationsManager.getLocationsManager(
            activity
        ).getLoadedLocations(true)) if (loc is DocumentTreeLocation) {
            val li: LocationInfo = LocationInfo(
                loc
            )
            _locationsList!!.add(li)
        }
    }

    override val defaultLocationType: String?
        get() = DocumentTreeLocation.URI_SCHEME

    override fun addNewLocation(locationType: String?) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_CODE_ADD_LOCATION)
    }

    private inner class LocationInfo(l: DocumentTreeLocation) :
        com.sovworks.eds.android.locations.fragments.LocationListBaseFragment.LocationInfo() {
        init {
            location = l
        }

        override val icon: Drawable?
            get() = this.loadedIcon
    }

    @get:Synchronized
    private val loadedIcon: Drawable?
        get() {
            if (_icon == null) {
                val typedValue = TypedValue()
                activity.theme.resolveAttribute(R.attr.storageIcon, typedValue, true)
                _icon =
                    activity.resources.getDrawable(typedValue.resourceId)
            }
            return _icon
        }

    companion object {
        private const val REQUEST_CODE_ADD_LOCATION = Activity.RESULT_FIRST_USER

        private var _icon: Drawable? = null
    }
}
