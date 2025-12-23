package com.sovworks.eds.android.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.sovworks.eds.android.R
import com.sovworks.eds.android.helpers.CompatHelper
import com.sovworks.eds.android.helpers.CompatHelperBase.Companion.storeTextInClipboard
import com.sovworks.eds.android.settings.PropertyEditor.Host

open class StaticPropertyEditor @JvmOverloads constructor(
    host: Host?,
    titleResId: Int,
    descResId: Int = 0
) :
    PropertyEditorBase(host, R.layout.settings_simple_editor, titleResId, descResId) {
    public override fun createView(parent: ViewGroup?): View {
        val view = super.createView(parent)
        _descTextView = view!!.findViewById<View>(R.id.desc) as TextView
        return view
    }

    override fun load() {
        val txt = loadText()
        if (txt != null) {
            _descTextView!!.visibility = View.VISIBLE
            _descTextView!!.text = txt
        } else _descTextView!!.visibility = View.GONE
    }

    override fun load(b: Bundle) {
        if (_descTextView != null) {
            if (isInstantSave) load()
            else _descTextView!!.text = b.getString(bundleKey)
        }
    }

    override fun save() {
    }

    override fun save(b: Bundle) {
        if (!isInstantSave && _descTextView != null) b.putString(
            bundleKey,
            _descTextView!!.text.toString()
        )
    }

    override fun onClick() {
        super.onClick()
        CompatHelper.storeTextInClipboard(host.context, _descTextView!!.text.toString())
        Toast.makeText(host.context, R.string.text_has_been_copied, Toast.LENGTH_SHORT).show()
    }

    protected var _descTextView: TextView? = null

    protected open fun loadText(): String? {
        return null
    }
}
