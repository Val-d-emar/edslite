package com.sovworks.eds.android.settings.encfs

import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragment
import com.sovworks.eds.android.settings.ChoiceDialogPropertyEditor
import com.sovworks.eds.fs.encfs.AlgInfo
import com.sovworks.eds.util.RefVal

abstract class CodecInfoPropertyEditor(
    hostFragment: CreateEDSLocationFragment,
    titleId: Int,
    descrId: Int
) :
    ChoiceDialogPropertyEditor(hostFragment, titleId, descrId, hostFragment.tag) {
    protected abstract val codecs: Iterable<AlgInfo>
    protected abstract val paramName: String

    protected fun findCodec(name: String, codec: RefVal<AlgInfo?>?): Int {
        var i = 0
        for (ci in codecs) {
            if (name == ci.name) {
                if (codec != null) codec.value = ci
                return i
            }
            i++
        }
        return -1
    }

    override fun loadValue(): Int {
        val encAlgName = hostFragment.state.getString(paramName)
        return if (encAlgName != null) findCodec(encAlgName, null) else 0
    }

    override fun saveValue(value: Int) {
        var i = 0
        for (ci in codecs) {
            if (i == value) {
                hostFragment.state.putString(paramName, ci.name)
                return
            }
            i++
        }
        hostFragment.state.remove(paramName)
    }

    override fun getEntries(): ArrayList<String> {
        val res = ArrayList<String>()
        for (ci in codecs) res.add(ci.descr)
        return res
    }

    protected val hostFragment: CreateEDSLocationFragment
        get() = host as CreateEDSLocationFragment
}
