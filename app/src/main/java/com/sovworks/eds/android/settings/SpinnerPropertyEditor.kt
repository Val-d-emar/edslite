package com.sovworks.eds.android.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.PropertyEditor.Host

abstract class SpinnerPropertyEditor(host: Host?, titleResId: Int, descResId: Int) :
    PropertyEditorBase(host, R.layout.settings_spinner_editor, titleResId, descResId) {
    public override fun createView(parent: ViewGroup?): View {
        val view = super.createView(parent)
        _spinner = view!!.findViewById<View>(R.id.spinner) as Spinner
        reloadElements()
        _spinner!!.onItemClickListener =
            OnItemClickListener { adapterView, view, i, l -> if (_host.propertiesView.isInstantSave) save() }
        return view
    }

    fun reloadElements() {
        _spinner!!.adapter = entries
    }

    override fun load() {
        _spinner!!.setSelection(loadValue())
    }

    override fun load(b: Bundle) {
        if (_spinner != null) {
            if (isInstantSave) load()
            else _spinner!!.setSelection(b.getInt(bundleKey))
        }
    }

    override fun save() {
        saveValue(_spinner!!.selectedItemPosition)
    }

    override fun save(b: Bundle) {
        if (!isInstantSave && _spinner != null) b.putInt(bundleKey, _spinner!!.selectedItemPosition)
    }

    protected var _spinner: Spinner? = null

    protected abstract fun loadValue(): Int
    protected abstract fun saveValue(value: Int)
    protected abstract val entries: ArrayAdapter<*>?
}
