package com.sovworks.eds.fs.util

import com.sovworks.eds.fs.FileSystem
import java.io.IOException

abstract class FileSystemWrapper(val base: FileSystem) : FileSystem {
    override fun equals(o: Any?): Boolean {
        return if (o is FileSystemWrapper)
            equals(o.base)
        else
            (base == o)
    }

    override fun hashCode(): Int {
        return base.hashCode()
    }

    @Throws(IOException::class)
    override fun close(force: Boolean) {
        base.close(force)
    }

    override fun isClosed(): Boolean {
        return base.isClosed
    }
}
