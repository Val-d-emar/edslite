package com.sovworks.eds.android.providers.cursor

import android.content.Context
import com.drew.lang.annotations.NotNull
import com.sovworks.eds.android.helpers.CachedPathInfo
import com.sovworks.eds.android.providers.cursor.ListDirObservable.create
import com.sovworks.eds.locations.Location
import io.reactivex.Observable

class FSCursor(
    context: Context,
    location: Location,
    @NotNull projection: Array<String?>,
    selection: String,
    selectionArgs: Array<String?>,
    listDir: Boolean
) :
    FSCursorBase(context, location, projection, selection, selectionArgs, listDir) {
    @Throws(Exception::class)
    override fun createObservable(): Observable<CachedPathInfo>? {
        val sc = SelectionChecker(_location, _selection, _selectionArgs)
        return create(_location, _listDir).filter(sc).cache()
    }
}
