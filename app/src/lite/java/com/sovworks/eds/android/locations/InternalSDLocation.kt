package com.sovworks.eds.android.locations

import android.content.Context
import android.net.Uri
import android.net.Uri.Builder
import android.os.Environment
import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.locations.DeviceBasedLocation

class InternalSDLocation : DeviceBasedLocation {
    @JvmOverloads
    constructor(
        context: Context,
        currentPath: String? = Environment.getExternalStorageDirectory().path
    ) : this(context, context.getString(R.string.built_in_memory_card), null, currentPath)

    constructor(context: Context, title: String?, rootDir: String?, currentPath: String?) : super(
        UserSettings.getSettings(context),
        title,
        rootDir,
        currentPath
    ) {
        _context = context
    }

    constructor(context: Context, locationUri: Uri) : super(
        UserSettings.getSettings(context),
        locationUri
    ) {
        _context = context
    }

    override fun makeFullUri(): Builder? {
        return super.makeFullUri()!!.scheme(URI_SCHEME)
    }

    override fun copy(): InternalSDLocation? {
        return InternalSDLocation(_context, title, rootPath, _currentPathString)
    }

    private val _context: Context

    companion object {
        const val URI_SCHEME: String = "intmem"
    }
}
