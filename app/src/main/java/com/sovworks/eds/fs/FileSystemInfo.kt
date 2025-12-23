package com.sovworks.eds.fs

import android.os.Parcelable
import com.sovworks.eds.fs.exfat.ExFATInfo
import com.sovworks.eds.fs.exfat.ExFat.Companion.isModuleInstalled
import com.sovworks.eds.fs.fat.FATInfo
import java.io.IOException

abstract class FileSystemInfo : Parcelable {
    abstract val fileSystemName: String?

    @Throws(IOException::class)
    abstract fun makeNewFileSystem(img: RandomAccessIO?)

    @Throws(IOException::class)
    abstract fun openFileSystem(img: RandomAccessIO?, readOnly: Boolean): FileSystem?

    companion object {
        val supportedFileSystems: List<FileSystemInfo>
            get() {
                val res = ArrayList<FileSystemInfo>()
                res.add(FATInfo())
                if (isModuleInstalled) res.add(ExFATInfo())
                return res
            }
    }
}
