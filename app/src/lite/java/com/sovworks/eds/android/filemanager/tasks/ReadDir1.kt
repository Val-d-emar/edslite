package com.sovworks.eds.android.filemanager.tasks

import android.content.Context
import com.sovworks.eds.android.filemanager.DirectorySettings
import com.sovworks.eds.android.filemanager.records.BrowserRecord
import com.sovworks.eds.android.filemanager.records.ExecutableFileRecord
import com.sovworks.eds.android.filemanager.records.FolderRecord
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.StringPathUtil
import com.sovworks.eds.locations.Location
import java.io.IOException

class ReadDir internal constructor(
    context: Context,
    targetLocation: Location,
    selectedFiles: Collection<Path?>?,
    dirSettings: DirectorySettings,
    showRootFolderLink: Boolean
) :
    ReadDirBase(context, targetLocation, selectedFiles, dirSettings, showRootFolderLink) {
    companion object {
        @Throws(IOException::class)
        fun createBrowserRecordFromFile(
            context: Context?,
            loc: Location?,
            path: Path,
            directorySettings: DirectorySettings?
        ): BrowserRecord? {
            if (directorySettings != null) {
                val pu = if (path.isFile) StringPathUtil(path.getFile()!!.name)
                else if (path.isDirectory) StringPathUtil(path.getDirectory()!!.name)
                else StringPathUtil(path.pathString)
                val masks = directorySettings.hiddenFilesMasks
                if (masks != null) for (mask in masks) {
                    if (pu.fileName.matches(mask.toRegex())) return null
                }
            }

            return if (path.isDirectory) FolderRecord(context) else ExecutableFileRecord(context)
        }
    }
}
