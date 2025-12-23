package com.sovworks.eds.android.navigdrawer

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import com.sovworks.eds.android.R
import com.sovworks.eds.locations.Location


class DrawerDocumentTreeMenuItem(location: Location, drawerController: DrawerControllerBase?) :
    DrawerLocationMenuItem(location, drawerController) {
    override val icon: Drawable?
        get() = getIcon(drawerController.mainActivity)

    companion object {
        @Synchronized
        private fun getIcon(context: Context): Drawable? {
            if (_icon == null) {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.storageIcon, typedValue, true)
                _icon = context.resources.getDrawable(typedValue.resourceId)
            }
            return _icon
        }

        private var _icon: Drawable? = null
    }
}
