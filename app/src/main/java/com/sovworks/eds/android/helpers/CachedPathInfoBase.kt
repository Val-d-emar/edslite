package com.sovworks.eds.android.helpers

import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.StringPathUtil
import java.io.IOException
import java.util.Date

open class CachedPathInfoBase : CachedPathInfo {
    @Throws(IOException::class)
    override fun init(path: Path?) {
        this.path = path
        if (this.path != null) updateCommonPathParams()
    }

    fun updateCommonPathParams() {
        try {
            pathDesc = path!!.pathDesc
            isFile = path!!.isFile
            isDirectory = path!!.isDirectory
            if (isFile) {
                val f = path!!.file
                modificationDate = f.lastModified
                size = f.size
                name = f.name
            } else if (isDirectory) {
                val dir = path!!.directory
                modificationDate = dir.lastModified
                name = dir.name
            } else name = StringPathUtil(pathDesc).fileName
        } catch (ignored: IOException) {
        }
    }

    override var path: Path? = null
        protected set
    override var isFile: Boolean = false
        protected set
    override var isDirectory: Boolean = false
        protected set
    override var modificationDate: Date? = null
        private set
    override var size: Long = 0
        private set
    override var pathDesc: String? = null
        private set
    override var name: String? = null
}
