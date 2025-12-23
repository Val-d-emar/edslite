package com.sovworks.eds.android.settings.encfs

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragment
import com.sovworks.eds.android.locations.tasks.CreateEDSLocationTaskFragment
import com.sovworks.eds.android.settings.views.PropertiesView
import com.sovworks.eds.fs.encfs.AlgInfo
import com.sovworks.eds.fs.encfs.FS

class DataCodecPropertyEditor(hostFragment: CreateEDSLocationFragment) :
    CodecInfoPropertyEditor(hostFragment, R.string.encryption_algorithm, 0) {
    init {
        id = ID
    }

    override val codecs: Iterable<AlgInfo>
        get() = FS.getSupportedDataCodecs()

    override val paramName: String?
        get() = CreateEDSLocationTaskFragment.ARG_CIPHER_NAME

    companion object {
        val ID: Int = PropertiesView.newId()
    }
}
