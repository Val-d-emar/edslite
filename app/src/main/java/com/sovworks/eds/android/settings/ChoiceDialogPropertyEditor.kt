package com.sovworks.eds.android.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.PropertyEditor.Host
import com.sovworks.eds.android.settings.dialogs.ChoiceDialog

abstract class ChoiceDialogPropertyEditor : PropertyEditorBase {
    constructor(host: Host?, titleResId: Int, descResId: Int, hostFragmentTag: String) : super(
        host,
        R.layout.settings_choice_dialog_editor,
        titleResId,
        descResId
    ) {
        _entries = entries
        _hostFragmentTag = hostFragmentTag
    }

    constructor(
        host: Host?,
        propertyId: Int,
        title: String?,
        desc: String?,
        hostFragmentTag: String
    ) : super(host, propertyId, R.layout.settings_choice_dialog_editor, title, desc) {
        _entries = entries
        _hostFragmentTag = hostFragmentTag
    }

    public override fun createView(parent: ViewGroup?): View {
        val view = super.createView(parent)
        _selectedItems = view!!.findViewById<View>(android.R.id.text1) as TextView
        _selectButton = view.findViewById<View>(android.R.id.button1) as Button
        _selectButton!!.setOnClickListener { startChoiceDialog() }
        return view
    }

    var selectedEntry: Int
        get() = _selectedEntry
        set(val) {
            _selectedEntry = `val`
            updateSelectionText()
            if (_host.propertiesView.isInstantSave) save()
        }

    override fun load() {
        _entries = entries
        _selectButton!!.visibility =
            if (_entries.size < 2) View.GONE else View.VISIBLE
        _selectedEntry = loadValue()
        updateSelectionText()
    }

    override fun load(b: Bundle) {
        if (_selectButton != null) {
            if (isInstantSave) load()
            else {
                _entries = entries
                _selectButton!!.visibility =
                    if (_entries.size < 2) View.GONE else View.VISIBLE
                _selectedEntry = b.getInt(bundleKey)
                updateSelectionText()
            }
        }
    }

    override fun save() {
        saveValue(_selectedEntry)
    }

    override fun save(b: Bundle) {
        if (!isInstantSave && _selectButton != null) b.putInt(bundleKey, _selectedEntry)
    }

    protected var _selectedEntry: Int = -1

    protected abstract fun loadValue(): Int
    protected abstract fun saveValue(value: Int)
    protected abstract val entries: List<String?>

    protected var _selectedItems: TextView? = null

    private var _entries: List<String?>
    private var _selectButton: Button? = null

    private fun updateSelectionText() {
        if (_selectedEntry >= 0 && _selectedEntry < _entries.size) _selectedItems!!.text =
            _entries[_selectedEntry]
        else _selectedItems!!.text = ""
    }

    private fun startChoiceDialog() {
        ChoiceDialog.showDialog(
            _host.fragmentManager,
            id,
            if (_title != null) _title else _host.context.getString(_titleResId),
            _entries,
            _hostFragmentTag
        )
    }

    private val _hostFragmentTag: String
}
