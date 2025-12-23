package com.sovworks.eds.android.filemanager.activities

import com.sovworks.eds.android.navigdrawer.DrawerController
import com.sovworks.eds.locations.Location

class FileManagerActivity : FileManagerActivityBase() {
    override fun createDrawerController(): DrawerController {
        return DrawerController(this)
    }

    override fun showPromoDialogIfNeeded() {
        if (_settings?.lastViewedPromoVersion!! < 211) super.showPromoDialogIfNeeded()
    }

    companion object {
        @JvmStatic
        fun openFileManager(fm: FileManagerActivity, location: Location?, scrollPosition: Int) {
            fm.goTo(location, scrollPosition)
        }
    }
}
