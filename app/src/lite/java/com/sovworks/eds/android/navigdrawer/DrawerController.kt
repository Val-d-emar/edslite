package com.sovworks.eds.android.navigdrawer

import com.sovworks.eds.android.filemanager.activities.FileManagerActivity

class DrawerController(activity: FileManagerActivity) : DrawerControllerBase(activity) {
    override fun fillDrawer(): List<DrawerMenuItemBase> {
        val i = mainActivity.intent
        val isSelectAction = mainActivity.isSelectAction
        val list = ArrayList<DrawerMenuItemBase>()
        val adapter = DrawerAdapter(list)
        if (i.getBooleanExtra(FileManagerActivity.EXTRA_ALLOW_BROWSE_CONTAINERS, true)) adapter.add(
            DrawerContainersMenu(
                this
            )
        )
        if (i.getBooleanExtra(FileManagerActivity.EXTRA_ALLOW_BROWSE_DEVICE, true)) adapter.add(
            DrawerLocalFilesMenu(
                this
            )
        )
        if (!isSelectAction) {
            adapter.add(DrawerSettingsMenuItem(this))
            adapter.add(DrawerHelpMenuItem(this))
            adapter.add(DrawerAboutMenuItem(this))
            adapter.add(DrawerExitMenuItem(this))
        }
        drawerListView!!.adapter = adapter
        return list
    }
}
