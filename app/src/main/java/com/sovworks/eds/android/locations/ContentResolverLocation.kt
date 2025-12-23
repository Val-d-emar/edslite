package com.sovworks.eds.android.locations

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.net.Uri.Builder
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.fs.ContentResolverFs
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.fs.Path
import com.sovworks.eds.locations.LocationBase
import com.sovworks.eds.locations.LocationBase.SharedData
import java.io.IOException

class ContentResolverLocation : LocationBase {
    constructor(context: Context) : super(
        UserSettings.getSettings(context), SharedData(
            locationId,
            context
        )
    )

    constructor(context: Context, uri: Uri) : this(context) {
        loadFromUri(uri)
    }

    constructor(sibling: ContentResolverLocation) : super(sibling)

    override fun loadFromUri(uri: Uri) {
        super.loadFromUri(uri)
        if (uri.path != null && uri.path!!.length > 1) _currentPathString = uri.toString()
    }

    override fun getTitle(): String {
        return context.getString(R.string.content_provider)
    }

    @Synchronized
    @Throws(IOException::class)
    override fun getFS(): ContentResolverFs {
        if (sharedData.fs == null) sharedData.fs = ContentResolverFs(context.contentResolver)

        return sharedData.fs as ContentResolverFs
    }

    @Throws(IOException::class)
    override fun getCurrentPath(): Path {
        return if (_currentPathString == null) fs.rootPath else fs.getPath(_currentPathString)
    }

    override fun setCurrentPath(path: Path?) {
        _currentPathString = path?.pathString
    }

    override fun getLocationUri(): Uri {
        if (_currentPathString != null) return Uri.parse(_currentPathString)

        val ub = Builder()
        ub.scheme(locationId)
        ub.path("/")
        return ub.build()
    }

    override fun copy(): ContentResolverLocation {
        return ContentResolverLocation(this)
    }

    override fun getDeviceAccessibleUri(path: Path): Uri? {
        try {
            return (currentPath as ContentResolverFs.Path).uri
        } catch (e: IOException) {
            Logger.log(e)
            return null
        }
    }

    override fun saveExternalSettings() {
    }

    override fun getSharedData(): SharedData {
        return super.getSharedData() as SharedData
    }

    protected val context: Context
        get() = sharedData.context

    protected class SharedData(id: String?, val context: Context) : LocationBase.SharedData(id)

    companion object {
        const val locationId: String = ContentResolver.SCHEME_CONTENT
    }
}
