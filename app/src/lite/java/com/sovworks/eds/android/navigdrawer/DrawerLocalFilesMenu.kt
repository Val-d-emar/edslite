package com.sovworks.eds.android.navigdrawer

import com.sovworks.eds.android.locations.DeviceRootNPLocation
import com.sovworks.eds.locations.Location

class DrawerLocalFilesMenu(drawerController: DrawerControllerBase?) :
    DrawerLocalFilesMenuBase(drawerController) {
    override fun addLocationMenuItem(list: MutableList<DrawerMenuItemBase>, loc: Location?) {
        if (loc is DeviceRootNPLocation && _allowDeviceLocations) list.add(
            DrawerDeviceRootMemoryItem(loc, drawerController)
        )
        else super.addLocationMenuItem(list, loc)
    }
}
