package com.sovworks.eds.android.navigdrawer

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.locations.DocumentTreeLocation
import com.sovworks.eds.android.locations.ExternalStorageLocation
import com.sovworks.eds.android.locations.InternalSDLocation
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager

abstract class DrawerLocalFilesMenuBase(drawerController: DrawerControllerBase?) :
    DrawerSubMenuBase(drawerController) {
    override val title: String
        get() = context.getString(R.string.local_files)

    override val subItems: Collection<DrawerMenuItemBase>
        get() {
            val res =
                ArrayList<DrawerMenuItemBase>()
            val act = drawerController.mainActivity
            val i = act!!.intent
            for (loc in LocationsManager.getLocationsManager(
                act
            ).getLoadedLocations(true)) addLocationMenuItem(res, loc)

            if (act.isSelectAction && i.getBooleanExtra(
                    FileManagerActivity.EXTRA_ALLOW_SELECT_FROM_CONTENT_PROVIDERS,
                    false
                )
            ) res.add(DrawerSelectContentProviderMenuItem(drawerController))

            if (_allowDocumentTree) res.add(DrawerManageLocalStorages(drawerController))

            return res
        }

    @JvmField
    protected var _allowDeviceLocations: Boolean
    protected var _allowDocumentTree: Boolean

    init {
        val i = getDrawerController().mainActivity.intent
        _allowDeviceLocations =
            i.getBooleanExtra(FileManagerActivity.EXTRA_ALLOW_BROWSE_DEVICE, true)
        _allowDocumentTree = VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP
                && i.getBooleanExtra(
            FileManagerActivity.EXTRA_ALLOW_BROWSE_DOCUMENT_PROVIDERS,
            true
        )
    }

    protected open fun addLocationMenuItem(list: MutableList<DrawerMenuItemBase>, loc: Location?) {
        if (loc is InternalSDLocation && _allowDeviceLocations) list.add(
            DrawerInternalSDMenuItem(
                loc,
                drawerController
            )
        )
        else if (loc is ExternalStorageLocation && _allowDeviceLocations) list.add(
            DrawerExternalSDMenuItem(
                loc,
                drawerController, _allowDocumentTree
            )
        )
        else if (loc is DocumentTreeLocation && _allowDocumentTree) list.add(
            DrawerDocumentTreeMenuItem(
                loc,
                drawerController
            )
        )
    }
}
