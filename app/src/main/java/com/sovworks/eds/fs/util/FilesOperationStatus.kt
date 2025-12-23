package com.sovworks.eds.fs.util

class FilesOperationStatus : Cloneable {
    var processed: FilesCountAndSize
    var total: FilesCountAndSize
    var fileName: String? = null

    var prevUpdateTime: Long = 0
    var prevProcSize: Long = 0

    init {
        total = FilesCountAndSize()
        processed = FilesCountAndSize()
    }

    fun updateProcessed() {}

    public override fun clone(): FilesOperationStatus {
        val res = FilesOperationStatus()
        res.total = total
        res.processed = processed
        res.fileName = fileName
        return res
    }
}
