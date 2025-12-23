package com.sovworks.eds.android.filemanager.tasks

import android.content.Context
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.filemanager.DirectorySettings
import com.sovworks.eds.android.filemanager.records.BrowserRecord
import com.sovworks.eds.android.filemanager.records.DummyUpDirRecord
import com.sovworks.eds.android.filemanager.records.LocRootDirRecord
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.ContainerFSWrapper
import com.sovworks.eds.fs.util.Util
import com.sovworks.eds.locations.Location
import com.sovworks.eds.settings.GlobalConfig
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Action
import io.reactivex.functions.Consumer
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import org.json.JSONException
import java.io.IOException

abstract class ReadDirBase
internal constructor(
    private val _context: Context,
    private val _targetLocation: Location,
    selectedFiles: Collection<Path>?,
    private val _directorySettings: DirectorySettings,
    private val _showFolderLinks: Boolean
) {
    @Throws(IOException::class)
    fun readDir(em: ObservableEmitter<BrowserRecord?>) {
        var targetPath = _targetLocation.currentPath
        if (targetPath!!.isFile) targetPath = targetPath.parentPath
        if (targetPath == null) {
            em.onComplete()
            return
        }

        var count = 0
        val basePath = targetPath.parentPath
        if (basePath != null && !em.isDisposed) {
            val br: BrowserRecord = DummyUpDirRecord(_context)
            br.init(null, basePath)
            procRecord(br, count++)
            em.onNext(br)
        }

        if (targetPath.isRootDirectory && _showFolderLinks && !em.isDisposed) {
            val br: BrowserRecord = LocRootDirRecord(_context)
            br.init(_targetLocation, targetPath)
            procRecord(br, count++)
            em.onNext(br)
        }

        val dirReader = targetPath.directory.list()
        if (dirReader == null) {
            em.onComplete()
            return
        }
        try {
            em.setCancellable { dirReader.close() }
            for (path in dirReader) {
                if (em.isDisposed) break

                val record = getBrowserRecordFromFsRecord(_targetLocation, path, _directorySettings)
                    ?: continue
                procRecord(record, count++)
                em.onNext(record)
            }
        } finally {
            dirReader.close()
        }
        em.onComplete()
    }

    private fun getBrowserRecordFromFsRecord(
        loc: Location,
        path: Path,
        directorySettings: DirectorySettings
    ): BrowserRecord? {
        try {
            return ReadDir.getBrowserRecordFromFsRecord(_context, loc, path, directorySettings)
        } catch (e: ApplicationException) {
            Logger.log(e)
        } catch (e: IOException) {
            Logger.log(e)
        }
        return null
    }

    protected fun procRecord(
        rec: BrowserRecord,
        count: Int
    ) {
        if (_selectedFiles != null && _selectedFiles.contains(rec.path)) rec.isSelected = true
    }

    private val _selectedFiles: Set<Path>? =
        if (selectedFiles == null) null else HashSet(selectedFiles)

    companion object {
        fun createObservable(
            context: Context?,
            targetLocation: Location?,
            selectedFiles: Collection<Path?>?,
            dirSettings: DirectorySettings?,
            showRootFolderLink: Boolean
        ): Observable<BrowserRecord?> {
            var observable = Observable.create { em: ObservableEmitter<BrowserRecord?>? ->
                val rd =
                    ReadDir(context, targetLocation, selectedFiles, dirSettings, showRootFolderLink)
                rd.readDir(em)
            }
            if (GlobalConfig.isTest()) observable = observable.doOnSubscribe
            (Consumer<Disposable> { res: Disposable? -> TEST_READING_OBSERVABLE!!.onNext(true) }).doFinally
            (Action { TEST_READING_OBSERVABLE!!.onNext(false) })
            return observable
        }

        var TEST_READING_OBSERVABLE: Subject<Boolean>? = null

        init {
            if (GlobalConfig.isTest()) TEST_READING_OBSERVABLE =
                BehaviorSubject.createDefault(false)
        }

        @Throws(IOException::class, ApplicationException::class)
        fun getBrowserRecordFromFsRecord(
            context: Context?,
            loc: Location?,
            path: Path?,
            directorySettings: DirectorySettings?
        ): BrowserRecord? {
            var rec = ReadDir.createBrowserRecordFromFile(context, loc, path, directorySettings)
            if (rec != null) try {
                rec.init(loc, path)
            } catch (e: Exception) {
                Logger.log(e)
                rec = null
            }
            return rec
        }

        @Throws(IOException::class)
        fun getDirectorySettings(path: Path): DirectorySettings? {
            return if (path.fileSystem is ContainerFSWrapper)
                (path.fileSystem as ContainerFSWrapper).getDirectorySettings(path)
            else
                loadDirectorySettings(path)
        }

        @JvmStatic
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
    }
}