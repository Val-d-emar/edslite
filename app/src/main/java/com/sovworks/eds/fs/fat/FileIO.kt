package com.sovworks.eds.fs.fat

import com.sovworks.eds.fs.File.AccessMode
import com.sovworks.eds.fs.File.AccessMode.Read
import com.sovworks.eds.fs.File.AccessMode.ReadWriteTruncate
import com.sovworks.eds.fs.File.AccessMode.WriteAppend
import com.sovworks.eds.fs.fat.FatFS.ClusterChainIO
import com.sovworks.eds.fs.fat.FatFS.FatPath
import java.io.IOException
import java.util.Date

class FileIO(
    private val _fat: FatFS,
    private val _fileEntry: DirEntry,
    path: FatPath,
    mode: AccessMode,
    private val _opTag: Any
) :
    ClusterChainIO(_fileEntry.startCluster, path, _fileEntry.fileSize, mode) {
    @Throws(IOException::class)
    override fun flush() {
        synchronized(_rwSync) {
            if (_isBufferDirty || !_addedClusters.isEmpty()) {
                try {
                    super.flush()
                } finally {
                    updateFileEntry()
                }
            }
        }
    }

    @Throws(IOException::class)
    override fun setLength(newLength: Long) {
        synchronized(_rwSync) {
            super.setLength(newLength)
            updateFileEntry()
        }
    }

    private val _basePath = path.parentPath as FatPath

    init {
        if (mode == WriteAppend) seek(length())
        else if (mode == ReadWriteTruncate) setLength(0)
    }

    @Throws(IOException::class)
    override fun writeBuffer() {
        super.writeBuffer()
        _fileEntry.lastModifiedDateTime = Date()
    }

    @Throws(IOException::class)
    private fun updateFileEntry() {
        if (_mode == Read) return
        _fileEntry.startCluster =
            if (_clusterChain.isEmpty()) FatFS.Companion.LAST_CLUSTER else _clusterChain[0]
        _fileEntry.fileSize = _maxStreamPosition
        _fileEntry.writeEntry(_fat, _basePath, _opTag)
        if (!_basePath.isRootDirectory) {
            val parentPath = _basePath.parentPath as FatPath
            if (parentPath != null) {
                val entry = _fat.getDirEntry(_basePath, _opTag)
                if (entry != null) {
                    entry.lastModifiedDateTime = _fileEntry.lastModifiedDateTime
                    entry.writeEntry(_fat, parentPath, _opTag)
                }
            }
        }
    }
}
