package com.sovworks.eds.android.filemanager.records

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.sovworks.eds.android.R

open class FolderRecord(context: Context?) : FsBrowserRecord(context) {
    override val viewType: Int
        get() = 1

    override fun createView(position: Int, parent: ViewGroup?): View? {
        if (_host == null) return null

        val inflater = _host!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        @SuppressLint("InflateParams") val v =
            inflater.inflate(R.layout.fs_browser_folder_row, parent, false)
        (v as ViewGroup).descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        updateView(v, position)
        return v
    }

    override fun allowSelect(): Boolean {
        return _host!!.allowFolderSelect()
    }

    @Throws(Exception::class)
    override fun open(): Boolean {
        if (path != null) _host!!.goTo(path)
        return true
    }

    @Throws(Exception::class)
    override fun openInplace(): Boolean {
        _host!!.showProperties(null, true)
        return open()
    }

    override val defaultIcon: Drawable?
        get() = getFolderIcon(_host)


    companion object {
        private var _folderIcon: Drawable? = null

        @Synchronized
        private fun getFolderIcon(context: Context?): Drawable? {
            if (_folderIcon == null && context != null) {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.folderIcon, typedValue, true)
                _folderIcon = context.resources.getDrawable(typedValue.resourceId)
            }
            return _folderIcon
        }
    }
}