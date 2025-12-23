package com.sovworks.eds.android.filemanager.tasks

import com.sovworks.eds.android.filemanager.DirectorySettings
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.ContainerFSWrapper
import com.sovworks.eds.fs.util.Util
import com.sovworks.eds.locations.Location
import io.reactivex.Maybe
import io.reactivex.MaybeEmitter
import org.json.JSONException
import java.io.IOException

object LoadDirSettingsObservable {
    @Throws(IOException::class)
    fun getDirectorySettings(path: Path): DirectorySettings? {
        return if (path.fileSystem is ContainerFSWrapper)
            (path.fileSystem as ContainerFSWrapper).getDirectorySettings(path)
        else
            loadDirectorySettings(path)
    }

    @Throws(IOException::class)
    fun loadDirectorySettings(path: Path): DirectorySettings? {
        val dsPath: Path
        try {
            dsPath = path.combine(DirectorySettings.FILE_NAME)
        } catch (e: IOException) {
            return null
        }
        try {
            return if (dsPath.isFile)
                DirectorySettings(Util.readFromFile(dsPath))
            else
                null
        } catch (e: JSONException) {
            throw IOException(e)
        }
    }

    fun create(targetLocation: Location): Maybe<DirectorySettings?> {
        return Maybe.create { s: MaybeEmitter<DirectorySettings?> ->
            var p = targetLocation.currentPath
            if (p.isFile) p = p.parentPath
            val ds = getDirectorySettings(p)
            if (ds == null) s.onComplete()
            else s.onSuccess(ds)
        }
    }
}
