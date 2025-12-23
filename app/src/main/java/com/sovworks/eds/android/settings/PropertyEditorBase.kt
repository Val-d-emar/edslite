package com.sovworks.eds.android.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.PropertyEditor.Host

abstract class PropertyEditorBase : PropertyEditor {
    @Synchronized
    override fun getView(parent: ViewGroup?): View? {
        if (_view == null) _view = createView(parent)

        return _view
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (getFieldId(requestCode) == startPosition) {
            onPropertyRequestResult(getFieldReqCode(requestCode), resultCode, data)
            return true
        }
        return false
    }

    override fun onClick() {
    }

    override fun load() {
    }

    @Throws(Exception::class)
    override fun save() {
    }

    override fun save(b: Bundle) {
    }

    override fun load(b: Bundle) {
    }

    protected constructor(host: Host?, layoutResId: Int, titleResId: Int, descResId: Int) {
        _layoutResId = layoutResId
        this.host = host
        _titleResId = titleResId
        _descResId = descResId
        id = _titleResId
        _title = null
        _desc = null
        //_view = createView();
    }

    protected constructor(
        host: Host?,
        propertyId: Int,
        layoutResId: Int,
        title: String?,
        desc: String?
    ) {
        _layoutResId = layoutResId
        this.host = host
        _title = title
        _desc = desc
        id = propertyId
        _titleResId = 0
        _descResId = 0
        _title = title
        _desc = desc
    }

    protected val _layoutResId: Int
    protected val _titleResId: Int
    protected val _descResId: Int
    protected var _title: String?
    protected var _desc: String?
    override val host: Host?
    protected var _view: View? = null

    protected open fun createView(parent: ViewGroup?): View {
        val li =
            host.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = li.inflate(_layoutResId, parent, false)
        var tv = view.findViewById<TextView>(R.id.title_edit)
        if (tv != null) {
            if (_titleResId != 0) tv.setText(_titleResId)
            else if (_title != null) tv.text = _title
        }

        tv = view.findViewById(R.id.desc)
        if (tv != null) {
            if (_descResId != 0) tv.setText(_descResId)
            else if (_desc != null) tv.text = _desc
            else tv.visibility = View.GONE
        }
        return view
    }

    protected val bundleKey: String
        get() = "property_" + id

    protected fun requestActivity(i: Intent?, propRequestCode: Int) {
        host!!.startActivityForResult(i, getReqCode(startPosition, propRequestCode))
    }

    protected val isInstantSave: Boolean
        get() = host.getPropertiesView().isInstantSave

    protected open fun onPropertyRequestResult(
        propertyRequestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
    }

    override var id: Int
    override var startPosition: Int = 0

    companion object {
        fun getReqCode(fieldIdx: Int, fieldRecCode: Int): Int {
            return (fieldRecCode shl 8) + fieldIdx
        }

        fun getFieldId(reqCode: Int): Int {
            return reqCode and 0xFF
        }

        fun getFieldReqCode(reqCode: Int): Int {
            return reqCode shr 8
        }
    }
}
