package com.sovworks.eds.fs.fat

import android.os.Parcel
import android.os.Parcelable.Creator
import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.fs.FileSystemInfo
import com.sovworks.eds.fs.RandomAccessIO
import java.io.IOException

class FATInfo : FileSystemInfo() {
    override fun getFileSystemName(): String {
        return NAME
    }

    @Throws(IOException::class)
    override fun makeNewFileSystem(img: RandomAccessIO) {
        FatFS.Companion.formatFat(img, img.length())
    }

    @Throws(IOException::class)
    override fun openFileSystem(img: RandomAccessIO, readOnly: Boolean): FileSystem {
        val fs: FatFS = FatFS.Companion.getFat(img)
        fs.setReadOnlyMode(readOnly)
        return fs
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, i: Int) {}

    companion object {
        val CREATOR: Creator<FATInfo> = object : Creator<FATInfo?> {
            override fun createFromParcel(`in`: Parcel): FATInfo? {
                return FATInfo()
            }

            override fun newArray(size: Int): Array<FATInfo?> {
                return arrayOfNulls(size)
            }
        }

        const val NAME: String = "FAT"
    }
}
