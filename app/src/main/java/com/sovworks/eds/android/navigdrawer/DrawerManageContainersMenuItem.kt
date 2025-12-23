package com.sovworks.eds.android.navigdrawer

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.ContainerBasedLocation

class DrawerManageContainersMenuItem(drawerController: DrawerControllerBase?) :
    DrawerManageLocationMenuItem(drawerController) {
    override val locationType: String
        get() = ContainerBasedLocation.URI_SCHEME

    override val title: String
        get() = context.getString(R.string.manage_containers)
}
