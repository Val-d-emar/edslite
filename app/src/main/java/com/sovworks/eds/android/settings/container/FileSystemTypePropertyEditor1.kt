package com.sovworks.eds.android.settings.container

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.CreateContainerFragmentBase
import com.sovworks.eds.android.locations.tasks.CreateContainerTaskFragmentBase
import com.sovworks.eds.android.settings.ChoiceDialogPropertyEditor
import com.sovworks.eds.fs.FileSystemInfo
import com.sovworks.eds.settings.GlobalConfig

class FileSystemTypePropertyEditor(createContainerFragment: CreateContainerFragmentBase) :
    ChoiceDialogPropertyEditor(
        createContainerFragment,
        R.string.file_system_type,
        createContainerFragment.getString(R.string.file_system_type),
        createContainerFragment.getString(
            R.string.file_system_type_desc,
            GlobalConfig.EXFAT_MODULE_URL
        ),
        createContainerFragment.tag
    ) {
    override fun loadValue(): Int {
        val names: List<String> = entries
        val cur: FileSystemInfo = hostFragment.getState
        ().getParcelable<FileSystemInfo>
        (CreateContainerTaskFragmentBase.ARG_FILE_SYSTEM_TYPE)
        return if (cur != null) names.indexOf(cur.fileSystemName)
        else if (!names.isEmpty()) 0
        else -1
    }

    override fun saveValue(value: Int) {
        val fs = FileSystemInfo.getSupportedFileSystems()
        val selected = fs[value]
        hostFragment.getState
        ().putParcelable
        (CreateContainerTaskFragmentBase.ARG_FILE_SYSTEM_TYPE, selected)
    }

    override fun getEntries(): ArrayList<String> {
        val res = ArrayList<String>()
        val supportedFS = FileSystemInfo.getSupportedFileSystems()
        if (supportedFS != null) {
            for (fsInfo in supportedFS) res.add(fsInfo.fileSystemName)
        }
        return res
    }

    protected val hostFragment: CreateContainerFragmentBase
        get() = host as CreateContainerFragmentBase
}
