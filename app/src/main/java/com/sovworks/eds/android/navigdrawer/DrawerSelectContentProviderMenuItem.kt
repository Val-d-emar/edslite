package com.sovworks.eds.android.navigdrawer

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.fragments.FileListViewFragment

class DrawerSelectContentProviderMenuItem(drawerController: DrawerControllerBase?) :
    DrawerMenuItemBase(drawerController) {
    override val title: String
        get() = drawerController.mainActivity.getString(R.string.content_provider)

    override fun onClick(view: View, position: Int) {
        super.onClick(view, position)
        val i = if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) Intent(
            Intent.ACTION_OPEN_DOCUMENT
        )
        else Intent(Intent.ACTION_GET_CONTENT)
        i.setType("*/*")
        i.addCategory(Intent.CATEGORY_OPENABLE)

        //if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && !getDrawerController().getMainActivity().isSingleSelectionMode())
        //    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        val f = drawerController.mainActivity.fileListViewFragment
        f?.startActivityForResult(i, FileListViewFragment.REQUEST_CODE_SELECT_FROM_CONTENT_PROVIDER)
    }

    override val viewType: Int
        get() = 2

    override fun updateView(view: View, position: Int) {
        super.updateView(view, position)
        val iv = view.findViewById<View>(R.id.close) as ImageView
        if (iv != null) iv.visibility = View.INVISIBLE
    }

    override val layoutId: Int
        get() = R.layout.drawer_location_item

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
