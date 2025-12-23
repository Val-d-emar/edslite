package com.sovworks.eds.android.settings

import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.PropertyEditor.Host

abstract class IntPropertyEditor(
    host: Host?,
    titleResId: Int,
    descResId: Int,
    hostFragmentTag: String?
) :
    TextPropertyEditor(host, titleResId, descResId, hostFragmentTag) {
    var currentValue: Int
        get() {
            val s = _selectedValueTextView!!.text.toString()
            return if (s.length > 0) s.toInt() else 0
        }
        set(value) {
            onTextChanged(value.toString())
        }

    protected abstract fun loadValue(): Int
    protected abstract fun saveValue(value: Int)

    override fun loadText(): String {
        val v = loadValue()
        return if (v != 0) v.toString() else ""
    }

    @Throws(Exception::class)
    override fun saveText(text: String) {
        saveValue(if (text.length > 0) text.toInt() else 0)
    }

    override val dialogViewResId: Int
        get() = R.layout.settings_edit_num
}
