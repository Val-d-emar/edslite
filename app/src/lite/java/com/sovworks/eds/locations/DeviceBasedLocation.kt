package com.sovworks.eds.locations

import android.net.Uri
import com.sovworks.eds.fs.Path
import com.sovworks.eds.settings.Settings

open class DeviceBasedLocation : DeviceBasedLocationBase {
    constructor(settings: Settings, currentPath: String) : this(settings, null, null) {
        _currentPathString = currentPath
    }

    constructor(settings: Settings, currentPath: Path) : this(
        settings,
        null,
        currentPath.fileSystem!!.rootPath!!.pathString
    ) {
        currentPath = currentPath
    }

    @JvmOverloads
    constructor(
        settings: Settings,
        title: String? = null,
        rootDir: String? = null,
        currentPath: String? = null
    ) : super(settings, title, rootDir, currentPath)

    constructor(settings: Settings, locationUri: Uri) : this(
        settings,
        locationUri.getQueryParameter("title"),
        getLocationId(locationUri),
        locationUri.path
    ) {
        loadFromUri(locationUri)
    }

    constructor(sibling: DeviceBasedLocation) : super(sibling)

    override fun copy(): DeviceBasedLocation? {
        return DeviceBasedLocation(this)
    }
}
