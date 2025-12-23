package com.sovworks.eds.android.navigdrawer

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import com.sovworks.eds.android.R
import com.sovworks.eds.android.dialogs.AboutDialog
import com.sovworks.eds.android.dialogs.AboutDialogBase.Companion.showDialog

class DrawerAboutMenuItem(drawerController: DrawerControllerBase?) :
    DrawerMenuItemBase(drawerController) {
    override val title: String
        get() = drawerController.mainActivity.getString(R.string.about)

    override fun onClick(view: View, position: Int) {
        super.onClick(view, position)
        AboutDialog.showDialog(drawerController.mainActivity.fragmentManager)
    }

    override val icon: Drawable?
        get() = getIcon(drawerController.mainActivity)

    companion object {
        @Synchronized
        private fun getIcon(context: Context): Drawable? {
            if (_icon == null) {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.aboutIcon, typedValue, true)
                _icon = context.resources.getDrawable(typedValue.resourceId)
            }
            return _icon
        }

        private var _icon: Drawable? = null
    }
}
