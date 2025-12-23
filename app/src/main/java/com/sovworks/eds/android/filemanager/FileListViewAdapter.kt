package com.sovworks.eds.android.filemanager

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.records.BrowserRecord
import com.sovworks.eds.android.helpers.ExtendedFileInfoLoader

class FileListViewAdapter(context: Context) :
    ArrayAdapter<BrowserRecord?>(context.applicationContext, R.layout.fs_browser_row) {
    fun setCurrentLocationId(locationId: String?) {
        _currentLocationId = locationId
    }

    override fun getItemViewType(position: Int): Int {
        val rec = getItem(position)
        return rec?.viewType ?: 0
    }

    override fun getViewTypeCount(): Int {
        return 2
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val rec = getItem(position) ?: return View(context)
        if (rec.needLoadExtendedInfo() && _currentLocationId != null) ExtendedFileInfoLoader.getInstance()
            .requestExtendedInfo(_currentLocationId, rec)

        val v: View?
        if (convertView != null) {
            v = convertView
            rec.updateView(v, position)
        } else v = rec.createView(position, parent)
        v!!.tag = rec
        return v
    }

    private var _currentLocationId: String? = null
}
