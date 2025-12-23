package com.sovworks.eds.fs.util

import com.sovworks.eds.fs.Path
import java.io.IOException

class FilesCountAndSize {
    var filesCount: Int = 0
    var totalSize: Long = 0

    companion object {
        fun getFilesCount(srcDstCol: SrcDstCollection): FilesCountAndSize {
            val result = FilesCountAndSize()
            val iter: Iterator<*> = srcDstCol.iterator()
            while (iter.hasNext()) {
                result.filesCount++
                iter.next()
            }

            return result
        }

        fun getFilesCountAndSize(
            countDirs: Boolean, srcDstCol: SrcDstCollection
        ): FilesCountAndSize {
            val result = FilesCountAndSize()
            for (srcDst in srcDstCol) {
                try {
                    getFilesCountAndSize(result, srcDst.srcLocation.currentPath, countDirs)
                } catch (ignored: IOException) {
                }
            }
            return result
        }

        @Throws(IOException::class)
        fun getFilesCountAndSize(result: FilesCountAndSize, path: Path, countDirs: Boolean) {
            if (path.isFile) {
                result.totalSize += path.file.size
                result.filesCount++
            } else if (countDirs) result.filesCount++
        }
    }
}
