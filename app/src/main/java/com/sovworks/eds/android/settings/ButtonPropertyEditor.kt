package com.sovworks.eds.android.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.PropertyEditor.Host

abstract class ButtonPropertyEditor : PropertyEditorBase {
    public override fun createView(parent: ViewGroup?): View {
        val view = super.createView(parent)
        _button = view!!.findViewById(android.R.id.button1)
        if (_buttonTextId != 0) _button.setText(_buttonTextId)
        else if (_buttonText != null) _button.setText(_buttonText)
        _button.setOnClickListener(View.OnClickListener { onButtonClick() })

        return view
    }

    override fun save(b: Bundle) {
    }

    override fun save() {
    }

    override fun load(b: Bundle) {
        load()
    }

    override fun load() {
    }

    protected constructor(host: Host?, titleResId: Int, descResId: Int, buttonTextId: Int) : super(
        host,
        R.layout.settings_button_editor,
        titleResId,
        descResId
    ) {
        _buttonTextId = buttonTextId
        _buttonText = null
    }

    protected constructor(
        host: Host?,
        propertyId: Int,
        title: String?,
        desc: String?,
        buttonText: String?
    ) : super(host, propertyId, R.layout.settings_button_editor, title, desc) {
        _buttonTextId = 0
        _buttonText = buttonText
    }

    protected var _button: Button? = null
    private val _buttonTextId: Int
    private val _buttonText: String?

    protected open fun onButtonClick() {
        if (_host.propertiesView.isInstantSave) save()
    }
}
