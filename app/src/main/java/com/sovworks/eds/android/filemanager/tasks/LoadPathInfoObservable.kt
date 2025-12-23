package com.sovworks.eds.android.filemanager.tasks

import com.sovworks.eds.android.helpers.CachedPathInfo
import com.sovworks.eds.android.helpers.CachedPathInfoBase
import com.sovworks.eds.locations.Location
import io.reactivex.Single
import io.reactivex.SingleEmitter


object LoadPathInfoObservable {
    @JvmStatic
    fun create(loc: Location): Single<CachedPathInfo?> {
        return Single.create { emitter: SingleEmitter<CachedPathInfo?> ->
            val cachedPathInfo: CachedPathInfo = CachedPathInfoBase()
            cachedPathInfo.init(loc.currentPath)
            emitter.onSuccess(cachedPathInfo)
        }
    }
}
