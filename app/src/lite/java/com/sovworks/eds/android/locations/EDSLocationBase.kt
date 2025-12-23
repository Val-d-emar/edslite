package com.sovworks.eds.android.locations

import android.content.Context
import android.net.Uri
import android.net.Uri.Builder
import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.android.Logger.Companion.showAndLog
import com.sovworks.eds.android.errors.UserException
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.UserSettingsCommon.getSettingsProtectionKey
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.Path.combine
import com.sovworks.eds.fs.util.ActivityTrackingFSWrapper
import com.sovworks.eds.fs.util.ActivityTrackingFSWrapper.getRootPath
import com.sovworks.eds.fs.util.ContainerFSWrapper
import com.sovworks.eds.fs.util.StringPathUtil
import com.sovworks.eds.fs.util.Util.readFromFile
import com.sovworks.eds.fs.util.Util.writeToFile
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.EDSLocation.InternalSettings
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.Location.ProtectionKeyProvider
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.locations.LocationsManagerBase
import com.sovworks.eds.locations.LocationsManagerBase.Companion.getLocationsManager
import com.sovworks.eds.locations.LocationsManagerBase.getLocations
import com.sovworks.eds.locations.OMLocationBase
import com.sovworks.eds.locations.OMLocationBase.ExternalSettings
import com.sovworks.eds.locations.OMLocationBase.SharedData
import com.sovworks.eds.settings.Settings
import com.sovworks.eds.settings.SettingsCommon.InvalidSettingsPassword
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

abstract class EDSLocationBase : OMLocationBase, Cloneable, EDSLocation {
    open class ExternalSettings : OMLocationBase.ExternalSettings(), EDSLocation.ExternalSettings {
        override fun shouldOpenReadOnly(): Boolean {
            return _openReadOnly
        }

        override fun setOpenReadOnly(`val`: Boolean) {
            _openReadOnly = `val`
        }

        override fun getAutoCloseTimeout(): Int {
            return _autoCloseTimeout
        }

        override fun setAutoCloseTimeout(timeout: Int) {
            _autoCloseTimeout = timeout
        }

        @Throws(JSONException::class)
        override fun saveToJSONObject(jo: JSONObject) {
            super.saveToJSONObject(jo)
            jo.put(SETTINGS_OPEN_READ_ONLY, shouldOpenReadOnly())
            if (_autoCloseTimeout > 0) jo.put(SETTINGS_AUTO_CLOSE_TIMEOUT, _autoCloseTimeout)
            else jo.remove(SETTINGS_AUTO_CLOSE_TIMEOUT)
        }

        @Throws(JSONException::class)
        override fun loadFromJSONOjbect(jo: JSONObject) {
            super.loadFromJSONOjbect(jo)
            setOpenReadOnly(jo.optBoolean(SETTINGS_OPEN_READ_ONLY, false))
            _autoCloseTimeout = jo.optInt(SETTINGS_AUTO_CLOSE_TIMEOUT, 0)
        }

        private var _openReadOnly = false
        private var _autoCloseTimeout = 0

        companion object {
            private const val SETTINGS_OPEN_READ_ONLY = "read_only"
            private const val SETTINGS_AUTO_CLOSE_TIMEOUT = "auto_close_timeout"
        }
    }

    class InternalSettings : EDSLocation.InternalSettings {
        fun save(): String {
            val jo = JSONObject()
            try {
                save(jo)
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }
            return jo.toString()
        }

        fun load(data: String) {
            var jo = try {
                JSONObject(data)
            } catch (e: JSONException) {
                JSONObject()
            }
            load(jo)
        }

        override fun toString(): String {
            return save()
        }

        @Throws(JSONException::class)
        protected fun save(jo: JSONObject?) {
        }

        protected fun load(jo: JSONObject?) {}
    }

    protected constructor(sibling: EDSLocationBase?) : super(sibling)

    protected constructor(settings: Settings?, sharedData: SharedData?) : super(
        settings,
        sharedData
    )

    override fun getLastActivityTime(): Long {
        if (isOpen) {
            val fs: ActivityTrackingFSWrapper?
            try {
                fs = fS
                return fs!!.lastActivityTime
            } catch (e: IOException) {
                log(e)
            }
        }
        return 0
    }

    @Synchronized
    @Throws(IOException::class)
    override fun getFS(): ContainerFSWrapper {
        if (sharedData.fs == null) {
            if (!isOpenOrMounted) throw IOException("Cannot access closed container.")
            val readOnly = externalSettings!!.shouldOpenReadOnly()
            try {
                sharedData.fs = createFS(readOnly)
            } catch (e: UserException) {
                throw RuntimeException(e)
            }
            try {
                readInternalSettings()
                applyInternalSettings()
            } catch (e: IOException) {
                showAndLog(context, e)
            }
        }
        return sharedData.fs as ContainerFSWrapper
    }

    override val title: String?
        get() {
            var title = super.title
            if (title == null) {
                val containerLocation = location
                title = if (containerLocation.isFileSystemOpen) {
                    try {
                        StringPathUtil(containerLocation.currentPath!!.pathString)
                            .fileNameWithoutExtension
                    } catch (e: Exception) {
                        "error"
                    }
                } else containerLocation.toString()
            }
            return title
        }

    override fun hasPassword(): Boolean {
        return true
    }

    override val isEncrypted: Boolean
        get() = true

    @Throws(IOException::class)
    override fun readInternalSettings() {
        var settingsPath: Path? = try {
            fS.getRootPath().combine(INTERNAL_SETTINGS_FILE_NAME)
        } catch (e: IOException) {
            null
        }
        if (settingsPath == null || !settingsPath.isFile) sharedData.internalSettings.load("")
        else sharedData
            .internalSettings
            .load(readFromFile(settingsPath))
    }

    override fun getInternalSettings(): InternalSettings {
        return sharedData.internalSettings
    }

    override val externalSettings: ExternalSettings?
        get() = super.externalSettings as ExternalSettings?

    @Throws(IOException::class)
    override fun writeInternalSettings() {
        val fs: FileSystem? = fS
        writeToFile(
            fs!!.rootPath!!.getDirectory()!!, INTERNAL_SETTINGS_FILE_NAME, internalSettings.save()
        )
    }

    override val isReadOnly: Boolean
        get() = super.isReadOnly || externalSettings!!.shouldOpenReadOnly()

    @Throws(IOException::class)
    override fun applyInternalSettings() {
    }

    override fun saveExternalSettings() {
        if (!_globalSettings.neverSaveHistory()) super.saveExternalSettings()
    }

    override fun getLocation(): Location {
        return sharedData.containerLocation
    }

    val context: Context
        get() = sharedData.context

    open class SharedData(
        id: String?,
        val internalSettings: InternalSettings,
        val containerLocation: Location,
        val context: Context
    ) :
        OMLocationBase.SharedData(id)

    @Throws(IOException::class, UserException::class)
    protected abstract fun createBaseFS(readOnly: Boolean): FileSystem?

    override val sharedData: SharedData
        get() = super.sharedData as SharedData

    override fun loadExternalSettings(): ExternalSettings? {
        val res = ExternalSettings()
        res.setProtectionKeyProvider(
            object : ProtectionKeyProvider {
                override val protectionKey: SecureBuffer?
                    get() {
                        return try {
                            UserSettings.getSettings(this.context).getSettingsProtectionKey()
                        } catch (invalidSettingsPassword: InvalidSettingsPassword) {
                            null
                        }
                    }
            })
        res.load(_globalSettings, id)
        return res
    }

    @Throws(IOException::class)
    override fun loadPaths(paths: Collection<String>): ArrayList<Path>? {
        return LocationsManager.getPathsFromLocations(
            LocationsManager.getLocationsManager(context).getLocations(paths)
        )
    }

    protected fun makeUri(uriScheme: String?): Builder {
        val ub = Builder()
        ub.scheme(uriScheme)
        if (_currentPathString != null) ub.path(_currentPathString)
        else ub.path("/")
        ub.appendQueryParameter(LOCATION_URI_PARAM, location.locationUri.toString())
        return ub
    }

    @Throws(IOException::class, UserException::class)
    protected fun createFS(readOnly: Boolean): FileSystem {
        val baseFS = createBaseFS(readOnly)
        return ContainerFSWrapper(baseFS)
    }

    companion object {
        const val INTERNAL_SETTINGS_FILE_NAME: String = ".eds-settings"

        @Throws(Exception::class)
        fun getContainerLocationFromUri(locationUri: Uri, lm: LocationsManagerBase): Location {
            var uriString = locationUri.getQueryParameter(LOCATION_URI_PARAM)
            if (uriString == null)  // maybe it's a legacy container
                uriString = locationUri.getQueryParameter("container_location")
            return lm.getLocation(Uri.parse(uriString))
        }

        protected const val LOCATION_URI_PARAM: String = "location"

        protected fun createInternalSettings(): InternalSettings {
            return InternalSettings()
        }
    }
}
