package com.sovworks.eds.fs.exfat

import android.os.Parcel
import android.os.Parcelable.Creator
import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.fs.FileSystemInfo
import com.sovworks.eds.fs.RandomAccessIO
import java.io.IOException

class ExFATInfo : FileSystemInfo() {
    override fun getFileSystemName(): String {
        return NAME
    }

    @Throws(IOException::class)
    override fun makeNewFileSystem(img: RandomAccessIO) {
        makeNewFS(img)
    }

    @Throws(IOException::class)
    override fun openFileSystem(img: RandomAccessIO, readOnly: Boolean): FileSystem {
        return ExFat(img, readOnly)
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, i: Int) {}

    companion object {
        val CREATOR: Creator<ExFATInfo> = object : Creator<ExFATInfo?> {
            override fun createFromParcel(`in`: Parcel): ExFATInfo? {
                return ExFATInfo()
            }

            override fun newArray(size: Int): Array<ExFATInfo?> {
                return arrayOfNulls(size)
            }
        }

        const val NAME: String = "ExFAT"
    }
}
