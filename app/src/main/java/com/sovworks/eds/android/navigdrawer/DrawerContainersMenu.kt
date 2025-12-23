package com.sovworks.eds.android.navigdrawer

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.EncFsLocationBase
import com.sovworks.eds.locations.ContainerLocation
import com.sovworks.eds.locations.LocationsManager

class DrawerContainersMenu(drawerController: DrawerControllerBase?) :
    DrawerSubMenuBase(drawerController) {
    override val title: String
        get() = context.getString(R.string.containers)

    override val subItems: Collection<DrawerMenuItemBase>
        get() {
            val lm = LocationsManager.getLocationsManager(context)
            val res =
                ArrayList<DrawerMenuItemBase>()
            for (loc in lm.getLoadedEDSLocations(true)) {
                if (loc is ContainerLocation) res.add(
                    DrawerContainerMenuItem(
                        loc,
                        drawerController
                    )
                )
                else if (loc is EncFsLocationBase) res.add(
                    DrawerEncFsMenuItem(
                        loc,
                        drawerController
                    )
                )
            }
            res.add(DrawerManageContainersMenuItem(drawerController))

            return res
        }
}
