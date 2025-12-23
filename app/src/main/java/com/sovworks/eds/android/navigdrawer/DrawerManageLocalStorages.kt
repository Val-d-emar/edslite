package com.sovworks.eds.android.navigdrawer

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.DocumentTreeLocation

class DrawerManageLocalStorages(drawerController: DrawerControllerBase?) :
    DrawerManageLocationMenuItem(drawerController) {
    override val locationType: String
        get() = DocumentTreeLocation.URI_SCHEME

    override val title: String
        get() = context.getString(R.string.manage_local_storages)
}
