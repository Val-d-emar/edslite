package com.sovworks.eds.android.providers.cursor

import android.net.Uri
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.helpers.CachedPathInfo
import com.sovworks.eds.android.helpers.CachedPathInfoBase
import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.FSRecord
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.disposables.Disposables
import java.io.IOException


object ListDirObservable {
    fun create(lm: LocationsManager, locUri: Uri?): Observable<CachedPathInfo> {
        return Observable.create { observableEmitter: ObservableEmitter<CachedPathInfo> ->
            val loc = lm.getLocation(locUri)
            if (loc.currentPath.isDirectory) emitListDir(
                loc.currentPath.directory,
                observableEmitter
            )
            else if (loc.currentPath.isFile) emitFile(
                loc.currentPath.file,
                observableEmitter
            )
            else observableEmitter.onComplete()
        }
    }

    @JvmStatic
    fun create(loc: Location, listDir: Boolean): Observable<CachedPathInfo> {
        return Observable.create { observableEmitter: ObservableEmitter<CachedPathInfo> ->
            if (loc.currentPath.isDirectory) {
                if (listDir) emitListDir(
                    loc.currentPath.directory,
                    observableEmitter
                )
                else emitFile(loc.currentPath.directory, observableEmitter)
            } else if (loc.currentPath.isFile) emitFile(
                loc.currentPath.file,
                observableEmitter
            )
            else observableEmitter.onComplete()
        }
    }

    @Throws(IOException::class)
    private fun emitFile(f: FSRecord, observableEmitter: ObservableEmitter<CachedPathInfo>) {
        val cpi: CachedPathInfo = CachedPathInfoBase()
        cpi.init(f.path)
        observableEmitter.onNext(cpi)
        observableEmitter.onComplete()
    }

    @Throws(IOException::class)
    private fun emitListDir(dir: Directory, observableEmitter: ObservableEmitter<CachedPathInfo>) {
        val contents = dir.list()
        observableEmitter.setDisposable(Disposables.fromRunnable {
            try {
                contents.close()
            } catch (e: IOException) {
                Logger.log(e)
            }
        })
        for (p in contents) {
            val cpi: CachedPathInfo = CachedPathInfoBase()
            try {
                cpi.init(p)
                observableEmitter.onNext(cpi)
            } catch (e: IOException) {
                Logger.log(e)
            }
        }
        observableEmitter.onComplete()
    }
}
