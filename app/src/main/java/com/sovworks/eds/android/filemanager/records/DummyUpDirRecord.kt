package com.sovworks.eds.android.filemanager.records

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import com.sovworks.eds.android.R

class DummyUpDirRecord(context: Context?) : FolderRecord(context) {
    override fun getName(): String {
        return ".."
    }

    override fun allowSelect(): Boolean {
        return false
    }

    override fun isFile(): Boolean {
        return false
    }

    override fun isDirectory(): Boolean {
        return true
    }

    @Throws(Exception::class)
    override fun open(): Boolean {
        super.open()
        val nh = _host!!.fileListDataFragment!!.navigHistory
        if (!nh.empty()) nh.pop()
        return true
    }

    override val defaultIcon: Drawable?
        get() = getIcon(_host)

    companion object {
        private var _icon: Drawable? = null

        @Synchronized
        private fun getIcon(context: Context?): Drawable? {
            if (_icon == null && context != null) {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.folderUpIcon, typedValue, true)
                _icon = context.resources.getDrawable(typedValue.resourceId)
            }
            return _icon
        }
    }
}