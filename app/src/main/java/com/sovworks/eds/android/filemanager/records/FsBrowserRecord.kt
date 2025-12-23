package com.sovworks.eds.android.filemanager.records

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.ImageView.ScaleType.CENTER_CROP
import android.widget.ListView
import android.widget.RadioButton
import android.widget.TextView
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.filemanager.fragments.FileListViewFragment
import com.sovworks.eds.android.helpers.CachedPathInfoBase
import com.sovworks.eds.android.helpers.ExtendedFileInfoLoader.ExtendedFileInfo
import com.sovworks.eds.fs.Path
import com.sovworks.eds.locations.Location
import java.io.IOException

abstract class FsBrowserRecord(protected val _context: Context?) : CachedPathInfoBase(),
    BrowserRecord {
    class RowViewInfo {
        var listView: ListView? = null
        var view: View? = null
        var position: Int = 0
    }

    override val viewType: Int
        get() = 0

    override fun createView(position: Int, parent: ViewGroup?): View? {
        if (_host == null) return null
        val inflater = _host!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        @SuppressLint("InflateParams") val v =
            inflater.inflate(R.layout.fs_browser_row, parent, false)
        (v as ViewGroup).descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        updateView(v, position)
        return v
    }

    override fun updateView(view: View, position: Int) {
        val hf = hostFragment
        //if(isSelected())
        //    //noinspection deprecation
        //    view.setBackgroundDrawable(getSelectedBackgroundDrawable(_context));
        val cb = view.findViewById<CheckBox>(android.R.id.checkbox)
        if (cb != null) {
            if (allowSelect() && (_host!!.isSelectAction || hf!!.isInSelectionMode) && (!_host!!.isSelectAction || !_host!!.isSingleSelectionMode)) {
                cb.setOnCheckedChangeListener(null)
                cb.isChecked = isSelected
                cb.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
                    if (isChecked) hf!!.selectFile(this@FsBrowserRecord)
                    else hf!!.unselectFile(this@FsBrowserRecord)
                }
                cb.visibility = View.VISIBLE
            } else cb.visibility = View.INVISIBLE
        }
        val rb = view.findViewById<RadioButton>(R.id.radio)
        if (rb != null) {
            if (allowSelect() && _host!!.isSelectAction && _host!!.isSingleSelectionMode) {
                rb.setOnCheckedChangeListener(null)
                rb.isChecked = isSelected
                rb.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
                    if (isChecked) hf!!.selectFile(this@FsBrowserRecord)
                    else hf!!.unselectFile(this@FsBrowserRecord)
                }
                rb.visibility = View.VISIBLE
            } else rb.visibility = View.INVISIBLE
        }

        val tv = view.findViewById<TextView>(android.R.id.text1)
        tv.text = name

        var iv = view.findViewById<ImageView>(android.R.id.icon)
        iv.setImageDrawable(defaultIcon)
        iv.scaleType = CENTER_CROP
        iv.setOnClickListener { view1: View? ->
            if (allowSelect()) {
                if (isSelected) {
                    if (!_host!!.isSelectAction || !_host!!.isSingleSelectionMode) hf!!.unselectFile(
                        this@FsBrowserRecord
                    )
                } else hf!!.selectFile(this@FsBrowserRecord)
            }
        }

        iv = view.findViewById(android.R.id.icon1)
        if (_miniIcon == null) iv.visibility = View.INVISIBLE
        else {
            iv.setImageDrawable(_miniIcon)
            iv.visibility = View.VISIBLE
        }
    }

    override fun updateView() {
        updateRowView(_host!!, this)
    }

    override fun setExtData(data: ExtendedFileInfo?) {
    }

    override fun loadExtendedInfo(): ExtendedFileInfo? {
        return null
    }

    override fun allowSelect(): Boolean {
        return true
    }

    @Throws(Exception::class)
    override fun open(): Boolean {
        return false
    }

    @Throws(Exception::class)
    override fun openInplace(): Boolean {
        return false
    }

    override fun setHostActivity(host: FileManagerActivity?) {
        _host = host
    }

    override fun needLoadExtendedInfo(): Boolean {
        return false
    }

    @Throws(IOException::class)
    override fun init(location: Location?, path: Path?) {
        init(path)
        _locationId = if (location == null) "" else location.id
    }

    protected var _locationId: String? = null
    protected var _host: FileManagerActivity? = null
    protected var _miniIcon: Drawable? = null

    protected abstract val defaultIcon: Drawable?

    protected val hostFragment: FileListViewFragment?
        get() = if (_host == null) null else _host!!.fragmentManager
            .findFragmentByTag(FileListViewFragment.TAG) as FileListViewFragment

    override var isSelected: Boolean = false /*private static Drawable _selectedItemBackground;

    private static synchronized Drawable getSelectedBackgroundDrawable(Context context)
    {
        if(_selectedItemBackground == null)
            //noinspection deprecation
            _selectedItemBackground = context.getResources().getDrawable(R.drawable.list_selected_item_background);
        return _selectedItemBackground;
    }*/
        set(val) {
            field = `val`
        }

    companion object {
        fun updateRowView(host: FileManagerActivity, item: Any) {
            updateRowView(
                host.fragmentManager.findFragmentByTag(FileListViewFragment.TAG) as FileListViewFragment,
                item
            )
        }

        fun updateRowView(host: FileListViewFragment?, item: Any) {
            val rvi = getCurrentRowViewInfo(host, item)
            if (rvi != null) updateRowView(rvi)
        }

        fun updateRowView(rvi: RowViewInfo) {
            rvi.listView!!.adapter.getView(rvi.position, rvi.view, rvi.listView)
        }

        fun getCurrentRowViewInfo(host: FileListViewFragment?, item: Any): RowViewInfo? {
            if (host == null || host.isRemoving || !host.isResumed) return null
            val list = host.listView ?: return null
            val start = list.firstVisiblePosition
            var i = start
            val j = list.lastVisiblePosition
            while (i <= j) {
                if (j < list.count && item === list.getItemAtPosition(i)) {
                    val rvi = RowViewInfo()
                    rvi.view = list.getChildAt(i - start)
                    rvi.position = i
                    rvi.listView = list
                    return rvi
                }
                i++
            }
            return null
        }

        fun getCurrentRowViewInfo(host: FileManagerActivity?, item: Any): RowViewInfo? {
            if (host == null) return null
            val f =
                host.fragmentManager.findFragmentByTag(FileListViewFragment.TAG) as FileListViewFragment
            return getCurrentRowViewInfo(f, item)
        }
    }
}
