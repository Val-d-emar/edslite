package com.sovworks.eds.android.settings

import android.app.DialogFragment
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.PropertyEditor.Host
import com.sovworks.eds.android.settings.dialogs.TextEditDialog
import com.sovworks.eds.android.settings.dialogs.TextEditDialog.TextResultReceiver

abstract class TextPropertyEditor(
    host: Host?,
    layoutId: Int,
    titleResId: Int,
    descResId: Int,
    private val _hostFragmentTag: String?
) :
    PropertyEditorBase(host, layoutId, titleResId, descResId), TextResultReceiver {
    constructor(host: Host?, titleResId: Int, descResId: Int, hostFragmentTag: String?) : this(
        host,
        R.layout.settings_text_editor,
        titleResId,
        descResId,
        hostFragmentTag
    )

    public override fun createView(parent: ViewGroup?): View {
        val view = super.createView(parent)
        _selectedValueTextView = view!!.findViewById<View>(android.R.id.text1) as TextView
        val selectButton = view.findViewById<View>(android.R.id.button1) as Button
        selectButton.setOnClickListener { startChangeValueDialog() }
        return view
    }

    override fun load() {
        _selectedValueTextView!!.text = loadText()
    }

    override fun load(b: Bundle) {
        if (_selectedValueTextView != null) {
            if (isInstantSave) load()
            else _selectedValueTextView!!.text = b.getString(bundleKey)
        }
    }

    @Throws(Exception::class)
    override fun save() {
        saveText(_selectedValueTextView!!.text.toString())
    }

    override fun save(b: Bundle) {
        if (!isInstantSave && _selectedValueTextView != null) b.putString(
            bundleKey,
            _selectedValueTextView!!.text.toString()
        )
    }

    @Throws(Exception::class)
    override fun setResult(value: String) {
        onTextChanged(value)
    }

    protected var _selectedValueTextView: TextView? = null

    protected abstract fun loadText(): String?

    @Throws(Exception::class)
    protected abstract fun saveText(text: String)

    protected fun startChangeValueDialog() {
        val args = initDialogArgs()
        val df: DialogFragment = TextEditDialog()
        df.arguments = args
        df.show(host.fragmentManager, TextEditDialog.TAG)
    }

    protected open val dialogViewResId: Int
        get() = R.layout.settings_edit_text

    protected fun initDialogArgs(): Bundle {
        val b = Bundle()
        b.putString(TextEditDialog.ARG_TEXT, _selectedValueTextView!!.text.toString())
        b.putInt(PropertyEditor.Companion.ARG_PROPERTY_ID, id)
        b.putInt(TextEditDialog.ARG_MESSAGE_ID, _titleResId)
        b.putInt(TextEditDialog.ARG_EDIT_TEXT_RES_ID, dialogViewResId)
        if (_hostFragmentTag != null) b.putString(
            PropertyEditor.Companion.ARG_HOST_FRAGMENT_TAG,
            _hostFragmentTag
        )
        return b
    }

    protected open fun onTextChanged(newValue: String?) {
        _selectedValueTextView!!.text = newValue
        if (!_host.propertiesView.isLoadingProperties && _host.propertiesView.isInstantSave) try {
            save()
        } catch (e: Exception) {
            Logger.showAndLog(host.context, e)
        }
    }
}
