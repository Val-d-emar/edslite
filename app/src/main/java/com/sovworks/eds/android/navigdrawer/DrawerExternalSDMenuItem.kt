package com.sovworks.eds.android.navigdrawer

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.locations.opener.fragments.ExternalStorageOpenerFragment
import com.sovworks.eds.android.locations.opener.fragments.LocationOpenerBaseFragment
import com.sovworks.eds.locations.Location


class DrawerExternalSDMenuItem internal constructor(
    location: Location,
    drawerController: DrawerControllerBase?,
    private val _allowDocumentsAPI: Boolean
) :
    DrawerLocationMenuItem(location, drawerController) {
    class Opener : ExternalStorageOpenerFragment() {
        public override fun onLocationOpened(location: Location?) {
            (activity as FileManagerActivity).goTo(location)
        }
    }

    override val icon: Drawable?
        get() = getIcon(drawerController.mainActivity)

    override val opener: LocationOpenerBaseFragment
        get() = if (_allowDocumentsAPI) Opener() else super.getOpener()

    companion object {
        @Synchronized
        private fun getIcon(context: Context): Drawable? {
            if (_icon == null) {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.extStorageIcon, typedValue, true)
                _icon = context.resources.getDrawable(typedValue.resourceId)
            }
            return _icon
        }

        private var _icon: Drawable? = null
    }
}
