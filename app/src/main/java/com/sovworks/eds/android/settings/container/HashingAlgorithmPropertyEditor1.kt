package com.sovworks.eds.android.settings.container

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.CreateContainerFragmentBase
import com.sovworks.eds.android.locations.tasks.CreateContainerTaskFragmentBase
import com.sovworks.eds.android.settings.ChoiceDialogPropertyEditor
import com.sovworks.eds.container.VolumeLayoutBase
import com.sovworks.eds.crypto.hash.RIPEMD160
import com.sovworks.eds.crypto.hash.Whirlpool
import java.security.MessageDigest

class HashingAlgorithmPropertyEditor(createContainerFragment: CreateContainerFragmentBase) :
    ChoiceDialogPropertyEditor(
        createContainerFragment,
        R.string.hash_algorithm,
        0,
        createContainerFragment.tag
    ) {
    override fun loadValue(): Int {
        val algs = currentHashAlgList ?: return -1
        val algName = hostFragment.state.getString(CreateContainerTaskFragmentBase.ARG_HASHING_ALG)
        if (algName != null) {
            val md = VolumeLayoutBase.findHashFunc(algs, algName)
            return algs.indexOf(md)
        } else if (!algs.isEmpty()) return 0
        else return -1
    }

    override fun saveValue(value: Int) {
        val algs = currentHashAlgList
        val md = algs!![value]
        hostFragment.state.putString(CreateContainerTaskFragmentBase.ARG_HASHING_ALG, md.algorithm)
    }

    override fun getEntries(): ArrayList<String> {
        val res = ArrayList<String>()
        val supportedEngines =
            currentHashAlgList
        if (supportedEngines != null) {
            for (eng in supportedEngines) res.add(getHashFuncName(eng))
        }
        return res
    }

    protected val hostFragment: CreateContainerFragmentBase
        get() = host as CreateContainerFragmentBase

    private val currentHashAlgList: List<MessageDigest>?
        get() {
            val vl =
                hostFragment.selectedVolumeLayout
            return vl?.supportedHashFuncs
        }

    companion object {
        fun getHashFuncName(md: MessageDigest): String {
            if (md is RIPEMD160) return "RIPEMD-160"
            if (md is Whirlpool) return "Whirlpool"
            return md.algorithm
        }
    }
}
