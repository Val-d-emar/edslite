package com.sovworks.eds.android.service

import android.content.Intent
import com.sovworks.eds.android.R
import com.sovworks.eds.android.helpers.TempFilesMonitor.Companion.getMonitor
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.util.SrcDstCollection.SrcDst
import java.io.IOException

internal class SaveTempFileChangesTask : CopyFilesTask() {
    override val notificationMainTextId: Int
        get() = R.string.saving_changes

    @Throws(IOException::class)
    override fun copyFile(record: SrcDst): Boolean {
        if (super.copyFile(record)) {
            val dstPath = calcDstPath(
                record.srcLocation.currentPath.file,
                record.dstLocation.currentPath.directory
            )
            if (dstPath != null && dstPath.isFile) getMonitor(_context!!).updateMonitoredInfo(
                record.srcLocation,
                dstPath.file.lastModified
            )
            return true
        } else return false
    }

    @Throws(IOException::class)
    override fun copyFile(srcFile: File, targetFolder: Directory): Boolean {
        try {
            val tmpFile = copyToTempFile(srcFile, targetFolder)
            val dstPath = calcDstPath(srcFile, targetFolder)
            if (dstPath != null && dstPath.exists()) prepareBackupCopy(dstPath.file, targetFolder)
            tmpFile.rename(srcFile.name)
            return true
        } catch (e: IOException) {
            throw IOException(_context!!.getText(R.string.err_failed_saving_changes).toString(), e)
        }
    }

    @Throws(IOException::class)
    protected fun copyToTempFile(srcFile: File, targetFolder: Directory): File {
        val tmpName = srcFile.name + TMP_EXTENSION
        val tmpPath = calcPath(targetFolder, tmpName)
        if (tmpPath != null && tmpPath.isFile) tmpPath.file.delete()
        val dstFile = targetFolder.createFile(tmpName)
        if (!super.copyFile(srcFile, dstFile)) throw IOException("Failed copying to temp file")
        return dstFile
    }

    @Throws(IOException::class)
    protected fun prepareBackupCopy(dstFile: File, targetFolder: Directory) {
        if (!UserSettings.getSettings(_context).disableModifiedFilesBackup() && dstFile.size > 0) {
            val bakName = dstFile.name + BAK_EXTENSION
            val bakPath = calcPath(targetFolder, bakName)
            if (bakPath != null && bakPath.isFile) bakPath.file.delete()
            dstFile.rename(bakName)
        } else dstFile.delete()
    }

    override fun initParam(i: Intent): CopyFilesTaskParam {
        return object : CopyFilesTaskParam(i) {
            override fun forceOverwrite(): Boolean {
                return true
            }
        }
    }

    companion object {
        private const val BAK_EXTENSION = ".edsbak"
        private const val TMP_EXTENSION = ".edstmp"
    }
}