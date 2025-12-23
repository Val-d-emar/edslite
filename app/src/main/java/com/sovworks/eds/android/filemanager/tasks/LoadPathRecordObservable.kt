package com.sovworks.eds.android.filemanager.tasks

import android.content.Context
import com.sovworks.eds.android.filemanager.DirectorySettings
import com.sovworks.eds.android.filemanager.records.BrowserRecord
import com.sovworks.eds.locations.Location
import io.reactivex.Single
import io.reactivex.SingleEmitter

object LoadPathRecordObservable {
    fun create(
        context: Context?,
        targetLocation: Location,
        dirSettings: DirectorySettings?
    ): Single<BrowserRecord?> {
        return Single.create { s: SingleEmitter<BrowserRecord?> ->
            val p = targetLocation.currentPath
            s.onSuccess(
                ReadDir.getBrowserRecordFromFsRecord(
                    context,
                    targetLocation,
                    p,
                    dirSettings
                )
            )
        }
    }
}
