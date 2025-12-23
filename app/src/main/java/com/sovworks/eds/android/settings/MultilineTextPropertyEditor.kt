package com.sovworks.eds.android.settings

import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.PropertyEditor.Host

abstract class MultilineTextPropertyEditor(
    host: Host?,
    titleResId: Int,
    descResId: Int,
    hostFragmentTag: String?
) :
    TextPropertyEditor(host, titleResId, descResId, hostFragmentTag) {
    override val dialogViewResId: Int
        get() = R.layout.settings_edit_text_ml
}
