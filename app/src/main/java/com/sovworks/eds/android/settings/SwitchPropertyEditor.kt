package com.sovworks.eds.android.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.PropertyEditor.Host

abstract class SwitchPropertyEditor(host: Host?, titleResId: Int, descResId: Int) :
    PropertyEditorBase(host, R.layout.settings_switch_editor, titleResId, descResId) {
    public override fun createView(parent: ViewGroup?): View {
        val view = super.createView(parent)
        _switchButton = view!!.findViewById(android.R.id.button1)
        _switchButton.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView: CompoundButton, isChecked: Boolean ->
            if (!_loadingValue) {
                if (!onChecked(isChecked)) buttonView.isChecked = !isChecked
            }
        })
        return view
    }

    override fun load() {
        _loadingValue = true
        try {
            _switchButton!!.isChecked = loadValue()
        } finally {
            _loadingValue = false
        }
    }

    override fun load(b: Bundle) {
        if (_switchButton == null) return
        if (isInstantSave) load()
        else {
            _loadingValue = true
            try {
                _switchButton!!.isChecked = b.getBoolean(bundleKey)
            } finally {
                _loadingValue = false
            }
        }
    }

    override fun save() {
        saveValue(_switchButton!!.isChecked)
    }

    override fun save(b: Bundle) {
        if (!isInstantSave && _switchButton != null) b.putBoolean(
            bundleKey,
            _switchButton!!.isChecked
        )
    }

    override fun onClick() {
        _switchButton!!.toggle()
    }

    protected val currentValue: Boolean
        get() = _switchButton!!.isChecked

    protected var _switchButton: CompoundButton? = null
    private var _loadingValue = false

    protected abstract fun loadValue(): Boolean
    protected abstract fun saveValue(value: Boolean)

    protected open fun onChecked(isChecked: Boolean): Boolean {
        if (_host.propertiesView.isInstantSave) save()
        return true
    }
}
