package com.sovworks.eds.android.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.PropertyEditor.Host

abstract class CheckBoxPropertyEditor(host: Host?, titleResId: Int, descResId: Int) :
    PropertyEditorBase(host, R.layout.settings_checkbox_editor, titleResId, descResId) {
    public override fun createView(parent: ViewGroup?): View {
        val view = super.createView(parent)
        _checkBox = view!!.findViewById<View>(android.R.id.checkbox) as CheckBox
        _checkBox!!.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!_loadingValue) onChecked(
                isChecked
            )
        }
        return view
    }

    override fun load() {
        _loadingValue = true
        try {
            _checkBox!!.isChecked = loadValue()
        } finally {
            _loadingValue = false
        }
    }

    override fun load(b: Bundle) {
        if (_checkBox != null) {
            if (isInstantSave) load()
            else {
                _loadingValue = true
                try {
                    _checkBox!!.isChecked = b.getBoolean(bundleKey)
                } finally {
                    _loadingValue = false
                }
            }
        }
    }

    override fun save() {
        saveValue(_checkBox!!.isChecked)
    }

    override fun save(b: Bundle) {
        if (!isInstantSave && _checkBox != null) b.putBoolean(bundleKey, _checkBox!!.isChecked)
    }

    override fun onClick() {
        _checkBox!!.isChecked = !_checkBox!!.isChecked
    }

    protected var _checkBox: CheckBox? = null
    protected var _loadingValue: Boolean = false

    protected abstract fun loadValue(): Boolean
    protected abstract fun saveValue(value: Boolean)

    protected fun onChecked(isChecked: Boolean) {
        if (_host.propertiesView.isInstantSave) save()
    }
}
