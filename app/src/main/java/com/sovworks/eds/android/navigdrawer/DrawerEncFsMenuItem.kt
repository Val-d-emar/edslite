package com.sovworks.eds.android.navigdrawer

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity.Companion.openFileManager
import com.sovworks.eds.android.filemanager.fragments.FileListViewFragment
import com.sovworks.eds.android.locations.closer.fragments.LocationCloserBaseFragment
import com.sovworks.eds.android.locations.closer.fragments.OMLocationCloserFragment
import com.sovworks.eds.android.locations.opener.fragments.EncFSOpenerFragment
import com.sovworks.eds.android.locations.opener.fragments.LocationOpenerBaseFragment
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.Location

class DrawerEncFsMenuItem(container: EDSLocation, drawerController: DrawerControllerBase?) :
    DrawerLocationMenuItem(container, drawerController) {
    class Opener : EncFSOpenerFragment() {
        public override fun onLocationOpened(location: Location?) {
            val args = arguments
            openFileManager(
                (activity as FileManagerActivity),
                location,
                args?.getInt(FileListViewFragment.ARG_SCROLL_POSITION, 0) ?: 0
            )
        }
    }

    override val icon: Drawable?
        get() = if (location.isOpen())
            getOpenedIcon(context)
        else
            getClosedIcon(context)

    override val location: Location?
        get() = super.getLocation() as EDSLocation

    override val opener: LocationOpenerBaseFragment
        get() = Opener()

    override val closer: LocationCloserBaseFragment
        get() = OMLocationCloserFragment()

    override fun hasSettings(): Boolean {
        return true
    }

    companion object {
        @Synchronized
        private fun getOpenedIcon(context: Context): Drawable? {
            if (_openedIcon == null) {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.lockOpenIcon, typedValue, true)
                _openedIcon = context.resources.getDrawable(typedValue.resourceId)
            }
            return _openedIcon
        }

        @Synchronized
        private fun getClosedIcon(context: Context): Drawable? {
            if (_closedIcon == null) {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.lockIcon, typedValue, true)
                _closedIcon = context.resources.getDrawable(typedValue.resourceId)
            }
            return _closedIcon
        }

        private var _openedIcon: Drawable? = null
        private var _closedIcon: Drawable? = null
    }
}
