package com.sovworks.eds.android.filemanager.tasks

import android.content.Context
import com.sovworks.eds.android.filemanager.records.BrowserRecord
import com.sovworks.eds.android.filemanager.records.ExecutableFileRecord
import com.sovworks.eds.android.filemanager.records.FolderRecord
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.fs.util.PathUtil
import com.sovworks.eds.locations.Location
import com.sovworks.eds.settings.GlobalConfig
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Action
import io.reactivex.functions.Consumer
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import org.json.JSONException
import java.io.IOException

abstract class CreateNewFileBase
internal constructor(
    protected val _context: Context,
    protected val _location: Location,
    protected val _fileName: String,
    private val _fileType: Int,
    private val _returnExisting: Boolean
) {
    @Throws(Exception::class)
    protected fun create(): BrowserRecord {
        if (_returnExisting) {
            val path = PathUtil.buildPath(_location.currentPath, _fileName)
            if (path != null && path.exists()) return ReadDir.getBrowserRecordFromFsRecord(
                _context,
                _location,
                path,
                null
            )
        }
        return createFile(_fileName, _fileType)
    }

    @Throws(IOException::class, JSONException::class, ApplicationException::class)
    protected fun createFile(fileName: String, ft: Int): BrowserRecord {
        return when (ft) {
            FILE_TYPE_FOLDER -> createNewFolder(fileName)
            FILE_TYPE_FILE -> createNewFile(fileName)
            else -> throw IllegalArgumentException("Unsupported file type")
        }
    }


    @Throws(IOException::class)
    private fun createNewFolder(fileName: String): FolderRecord {
        val parentDir = _location.currentPath.directory
        val newDir = parentDir.createDirectory(fileName)
        val fr = FolderRecord(_context)
        fr.init(_location, newDir.path)
        return fr
    }

    @Throws(IOException::class)
    private fun createNewFile(fileName: String): ExecutableFileRecord {
        val parentDir = _location.currentPath.directory
        val newFile = parentDir.createFile(fileName)
        val r = ExecutableFileRecord(_context)
        r.init(_location, newFile.path)
        return r
    }

    companion object {
        fun createObservable(
            context: Context?,
            location: Location?,
            fileName: String?,
            fileType: Int,
            returnExisting: Boolean
        ): Single<BrowserRecord?> {
            var observable = Single.create { em: SingleEmitter<BrowserRecord?> ->
                val cnf: CreateNewFileBase =
                    CreateNewFile(context, location, fileName, fileType, returnExisting)
                if (!em.isDisposed) em.onSuccess(cnf.create())
            }
            if (GlobalConfig.isTest()) observable = observable.doOnSubscribe
            (Consumer<Disposable> { res: Disposable? -> TEST_OBSERVABLE!!.onNext(true) }).doFinally
            (Action { TEST_OBSERVABLE!!.onNext(false) })
            return observable
        }

        var TEST_OBSERVABLE: Subject<Boolean>? = null

        init {
            if (GlobalConfig.isTest()) TEST_OBSERVABLE = PublishSubject.create()
        }

        const val FILE_TYPE_FILE: Int = 0
        const val FILE_TYPE_FOLDER: Int = 1
    }
}