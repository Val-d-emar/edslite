package com.sovworks.eds.locations

import android.content.Intent
import android.net.Uri
import android.net.Uri.Builder
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.android.providers.MainContentProvider
import com.sovworks.eds.android.providers.MainContentProviderBase.Companion.getContentUriFromLocation
import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.std.StdFs.Companion.getStdFs
import com.sovworks.eds.fs.util.StringPathUtil
import com.sovworks.eds.locations.Location.DefaultExternalSettings
import com.sovworks.eds.locations.Location.ExternalSettings
import com.sovworks.eds.settings.Settings
import java.io.File
import java.io.IOException

abstract class DeviceBasedLocationBase : Location, Cloneable {
    constructor(settings: Settings, title: String?, rootDir: String?, currentPath: String?) {
        _settings = settings
        sharedData = SharedData(
            rootDir
                ?: "", title
        )
        _currentPathString = currentPath
    }

    constructor(sibling: DeviceBasedLocationBase) {
        _settings = sibling._settings
        sharedData = sibling.sharedData
        _currentPathString = sibling._currentPathString
    }

    override fun loadFromUri(uri: Uri) {
        _currentPathString = uri.path
    }

    override val title: String?
        get() {
            try {
                val title = sharedData.title
                return title ?: currentPath!!.pathDesc
            } catch (e: IOException) {
                return ""
            }
        }

    @get:Throws(IOException::class)
    @get:Synchronized
    override val fS: FileSystem?
        get() {
            val sd = sharedData
            if (sd.fs == null) sd.fs = createFS()
            return sd.fs
        }

    @get:Throws(IOException::class)
    override var currentPath: Path?
        get() = if (_currentPathString == null) fS!!.rootPath else fS!!.getPath(
            _currentPathString
        )
        set(path) {
            _currentPathString = path?.pathString
        }

    override val locationUri: Uri
        get() = makeSimpleUri()!!.build()

    override fun getDeviceAccessibleUri(path: Path): Uri? {
        if (_settings.dontUseContentProvider() && VERSION.SDK_INT < VERSION_CODES.N) {
            val pu = StringPathUtil(rootPath).combine(path.pathString)
            return Uri.fromFile(File(pu.toString()))
        }
        return MainContentProvider.getContentUriFromLocation(this, path)
    }

    @get:Synchronized
    override val externalSettings: ExternalSettings?
        get() {
            val sd = sharedData
            if (sd.externalSettings == null) sd.externalSettings = loadExternalSettings()
            return sd.externalSettings
        }

    override fun saveExternalSettings() {}

    override val externalFileManagerLaunchIntent: Intent?
        get() = null

    override val id: String
        get() = getId(sharedData.chroot)

    override fun toString(): String {
        return try {
            StringPathUtil(rootPath).combine(currentPath!!.pathString).toString()
        } catch (e: IOException) {
            "wrong path"
        }
    }

    @Throws(IOException::class)
    override fun closeFileSystem(force: Boolean) {
        val fs = sharedData.fs
        if (fs != null && rootPath.length != 0) fs.close(force)
        sharedData.fs = null
    }

    override val isFileSystemOpen: Boolean
        get() = sharedData.fs != null

    override val isReadOnly: Boolean
        get() = false

    override val isEncrypted: Boolean
        get() = false

    override val isDirectlyAccessible: Boolean
        get() = true

    val rootPath: String
        get() = sharedData.chroot

    open fun makeFullUri(): Builder? {
        try {
            val u = Uri.fromFile(File(currentPath!!.pathString))
            val b = u.buildUpon()
            val cr = StringPathUtil(rootPath)
            if (!cr.isEmpty) b.appendQueryParameter("root_dir", cr.toString())
            if (sharedData.title != null) b.appendQueryParameter("title", sharedData.title)
            return b
        } catch (e: IOException) {
            log(e)
            return null
        }
    }

    fun makeSimpleUri(): Builder? {
        try {
            val ub = Builder()
            val pu =
                StringPathUtil(rootPath).combine(currentPath!!.pathString)
            ub.path(pu.toString())
            return ub
        } catch (e: IOException) {
            log(e)
            return null
        }
    }

    /*@Override
  public boolean equals(Object obj)
  {
  	try
  	{
  		return (obj instanceof DeviceBasedLocation) &&
  				getId().equals(((DeviceBasedLocation) obj).getId()) &&
  				getCurrentPath().equals(((DeviceBasedLocation) obj).getCurrentPath());
  	}
  	catch (Exception e)
  	{
  		return false;
  	}
  }*/
    protected val _settings: Settings
    protected val sharedData: SharedData
    @JvmField
    protected var _currentPathString: String?

    protected class SharedData(val chroot: String, val title: String?) {
        var fs: FileSystem? = null
        var externalSettings: ExternalSettings? = null
    }

    @Throws(IOException::class)
    protected fun createFS(): FileSystem {
        return getStdFs(rootPath)
    }

    protected open fun loadExternalSettings(): ExternalSettings? {
        return DefaultExternalSettings()
    }

    companion object {
        fun getId(chrootPath: String): String {
            return "stdfs$chrootPath"
        }

        @JvmStatic
        fun getLocationId(locationUri: Uri): String? {
            return locationUri.getQueryParameter("root_dir")
        }

        const val URI_SCHEME: String = "file"
    }
}
