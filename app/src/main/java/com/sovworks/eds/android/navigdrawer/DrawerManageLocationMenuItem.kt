package com.sovworks.eds.android.navigdrawer

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.activities.LocationListActivity

abstract class DrawerManageLocationMenuItem protected constructor(drawerController: DrawerControllerBase?) :
    DrawerMenuItemBase(drawerController) {
    override fun onClick(view: View, position: Int) {
        val i = Intent(context, LocationListActivity::class.java)
        i.putExtra(LocationListActivity.EXTRA_LOCATION_TYPE, locationType)
        context.startActivity(i)
        super.onClick(view, position)
    }

    override val icon: Drawable?
        get() = getIcon(context)

    override val viewType: Int
        get() = 3

    override val layoutId: Int
        get() = R.layout.drawer_folder_item

    protected abstract val locationType: String

    companion object {
        private var _icon: Drawable? = null

        @Synchronized
        private fun getIcon(context: Context): Drawable? {
            if (_icon == null) {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.manageLocationsIcon, typedValue, true)
                _icon = context.resources.getDrawable(typedValue.resourceId)
            }
            return _icon
        }
    }
}
