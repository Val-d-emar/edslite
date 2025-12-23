package com.sovworks.eds.android.navigdrawer

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.TypedValue
import android.view.View
import com.sovworks.eds.android.R
import com.sovworks.eds.settings.GlobalConfig

class DrawerHelpMenuItem(drawerController: DrawerControllerBase?) :
    DrawerMenuItemBase(drawerController) {
    override val title: String
        get() = drawerController.mainActivity.getString(R.string.help)

    override fun onClick(view: View, position: Int) {
        super.onClick(view, position)
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GlobalConfig.HELP_URL)))
    }

    override val icon: Drawable?
        get() = getIcon(drawerController.mainActivity)

    companion object {
        @Synchronized
        private fun getIcon(context: Context): Drawable? {
            if (_icon == null) {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.helpIcon, typedValue, true)
                _icon = context.resources.getDrawable(typedValue.resourceId)
            }
            return _icon
        }

        private var _icon: Drawable? = null
    }
}
