package com.sovworks.eds.android.settings.encfs

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragment
import com.sovworks.eds.android.locations.tasks.CreateEncFsTaskFragment
import com.sovworks.eds.fs.encfs.AlgInfo
import com.sovworks.eds.fs.encfs.FS

class NameCodecPropertyEditor(hostFragment: CreateEDSLocationFragment) :
    CodecInfoPropertyEditor(hostFragment, R.string.filename_encryption_algorithm, 0) {
    override val codecs: Iterable<AlgInfo>
        get() = FS.getSupportedNameCodecs()

    override val paramName: String?
        get() = CreateEncFsTaskFragment.ARG_NAME_CIPHER_NAME
}
