package com.sovworks.eds.locations

import android.content.Intent
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.provider.DocumentsContract.Document
import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.android.providers.ContainersDocumentProviderBase.Companion.getUriFromLocation
import com.sovworks.eds.crypto.SimpleCrypto.decrypt
import com.sovworks.eds.crypto.SimpleCrypto.encrypt
import com.sovworks.eds.crypto.SimpleCrypto.toHexString
import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.std.StdFs.Companion.stdFs
import com.sovworks.eds.fs.util.StringPathUtil
import com.sovworks.eds.locations.Location.ExternalSettings
import com.sovworks.eds.locations.Location.ProtectionKeyProvider
import com.sovworks.eds.settings.DefaultSettings
import com.sovworks.eds.settings.Settings
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

abstract class LocationBase protected constructor(
    settings: Settings?,
    protected val sharedData: SharedData
) :
    Location, Cloneable {
    open class ExternalSettings : com.sovworks.eds.locations.Location.ExternalSettings {
        override fun setProtectionKeyProvider(p: ProtectionKeyProvider?) {
            _protectionKeyProvider = p
        }

        override fun getTitle(): String {
            return _title!!
        }

        override fun setTitle(title: String?) {
            _title = title
        }

        override fun useExtFileManager(): Boolean {
            return _useExtFileManager
        }

        override fun setUseExtFileManager(`val`: Boolean) {
            _useExtFileManager = `val`
        }

        @Throws(JSONException::class)
        fun save(): String {
            val jo = JSONObject()
            saveToJSONObject(jo)
            return jo.toString()
        }

        fun load(settings: Settings, locationId: String?) {
            load(settings.getLocationSettingsString(locationId))
        }

        fun load(data: String) {
            var jo = try {
                JSONObject(data)
            } catch (e: Exception) {
                JSONObject()
            }
            try {
                loadFromJSONOjbect(jo)
            } catch (e: Exception) {
                log(e)
            }
        }

        override fun toString(): String {
            return try {
                save()
            } catch (e: JSONException) {
                "error"
            }
        }

        @Throws(JSONException::class)
        override fun saveToJSONObject(jo: JSONObject) {
            if (_title != null) jo.put(SETTINGS_TITLE, _title)
            jo.put(
                SETTINGS_VISIBLE_TO_USER,
                isVisibleToUser
            )
            jo.put(SETTINGS_USE_EXT_FILE_MANAGER, useExtFileManager())
        }

        @Throws(JSONException::class)
        override fun loadFromJSONOjbect(jo: JSONObject) {
            _title = jo.optString(SETTINGS_TITLE, null)
            isVisibleToUser = jo.optBoolean(SETTINGS_VISIBLE_TO_USER, false)
            _useExtFileManager = jo.optBoolean(SETTINGS_USE_EXT_FILE_MANAGER, true)
        }

        protected fun storeProtectedField(jo: JSONObject, key: String, data: String?) {
            try {
                if (data != null) jo.put(
                    key,
                    if (_protectionKeyProvider == null)
                        data
                    else
                        encrypt(_protectionKeyProvider.getProtectionKey(), data)
                )
            } catch (ignored: Exception) {
            }
        }

        protected fun storeProtectedField(jo: JSONObject, key: String, data: ByteArray?) {
            try {
                if (data != null) jo.put(key, encryptAndEncode(data))
            } catch (ignored: Exception) {
            }
        }

        protected fun encryptAndEncode(data: ByteArray): String {
            return if (_protectionKeyProvider == null)
                toHexString(data)
            else
                encrypt(_protectionKeyProvider.getProtectionKey(), data)
        }

        protected fun loadProtectedString(jo: JSONObject, key: String?): String? {
            val d = loadProtectedData(jo, key)
            return if (d == null) null else String(d)
        }

        protected fun loadProtectedData(jo: JSONObject, key: String?): ByteArray? {
            try {
                val data = jo.optString(key, null) ?: return null
                return decodeAndDecrypt(data)
            } catch (e: Exception) {
                log(e)
            }
            return null
        }

        protected fun decodeAndDecrypt(data: String): ByteArray {
            return if (_protectionKeyProvider == null)
                data.toByteArray()
            else
                decrypt(_protectionKeyProvider.getProtectionKey(), data)
        }

        private var _title: String? = null
        override var isVisibleToUser: Boolean = false
            set(val) {
                field = `val`
            }
        private var _useExtFileManager = false
        private var _protectionKeyProvider: ProtectionKeyProvider? = null

        companion object {
            private const val SETTINGS_TITLE = "title"
            const val SETTINGS_VISIBLE_TO_USER: String = "visible_to_user"
            private const val SETTINGS_USE_EXT_FILE_MANAGER = "use_ext_file_manager"
        }
    }

    protected constructor(sibling: LocationBase) : this(
        sibling._globalSettings,
        sibling.sharedData
    ) {
        _currentPathString = sibling._currentPathString
    }

    override val id: String
        get() = sharedData.locationId

    @get:Throws(IOException::class)
    override var currentPath: Path?
        get() = if (_currentPathString == null) fs.rootPath else fs.getPath(
            _currentPathString
        )
        set(path) {
            _currentPathString = path?.pathString
        }

    override val title: String?
        get() = externalSettings!!.title

    override val externalSettings: com.sovworks.eds.locations.Location.ExternalSettings?
        get() {
            val sd = sharedData
            if (sd.externalSettings == null) sd.externalSettings = loadExternalSettings()
            return sd.externalSettings
        }

    override fun saveExternalSettings() {
        try {
            _globalSettings.setLocationSettingsString(id, externalSettings.save())
        } catch (e: JSONException) {
            throw RuntimeException("Settings serialization failed", e)
        }
    }

    override fun loadFromUri(uri: Uri) {}

    override val isReadOnly: Boolean
        get() = false

    override val isEncrypted: Boolean
        get() = false

    override val isDirectlyAccessible: Boolean
        get() = false

    override fun getDeviceAccessibleUri(path: Path): Uri? {
        return null
    }

    override val externalFileManagerLaunchIntent: Intent?
        get() {
            if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) return null
            val exFmInfo = _globalSettings.externalFileManagerInfo
            if (exFmInfo == null || Document.MIME_TYPE_DIR != exFmInfo.mimeType) return null

            try {
                val intent = Intent(exFmInfo.action)
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.setClassName(exFmInfo.packageName!!, exFmInfo.className!!)
                val uri =
                    getUriFromLocation(this)
                if (exFmInfo.mimeType != null) intent.setDataAndType(uri, exFmInfo.mimeType)
                else intent.setData(uri)
                return intent
            } catch (e: Exception) {
                log(e)
                return null
            }
        }

    override fun toString(): String {
        var res = title!!
        try {
            if (isFileSystemOpen && currentPath != null) res += StringPathUtil(currentPath!!.pathDesc)
        } catch (ignored: Exception) {
        }
        return res
    }

    @Throws(IOException::class)
    override fun closeFileSystem(force: Boolean) {
        try {
            if (sharedData.fs != null) {
                sharedData.fs!!.close(force)
                sharedData.fs = null
            }
        } catch (e: Throwable) {
            if (!force) throw IOException(e)
            else log(e)
        }
    }

    override val isFileSystemOpen: Boolean
        get() = sharedData.fs != null

    /*@Override
 public boolean equals(Object obj)
 {
     try
     {
         return (obj instanceof LocationBase) &&
                 getId().equals(((LocationBase) obj).getId()) &&
                 getCurrentPath().equals(((LocationBase) obj).getCurrentPath());
     }
     catch (Exception e)
     {
         return false;
     }
 }*/
    protected open class SharedData protected constructor(val locationId: String) {
        @JvmField
        var fs: FileSystem? = null
        var externalSettings: ExternalSettings? = null
    }

    @JvmField
    protected val _globalSettings: Settings = settings ?: DefaultSettings()

    @JvmField
    protected var _currentPathString: String? = null

    @Throws(IOException::class)
    protected open fun loadPaths(paths: Collection<String>): ArrayList<Path>? {
        val res = ArrayList<Path>()
        for (path in paths) res.add(stdFs.getPath(path))
        return res
    }

    protected open fun loadExternalSettings(): ExternalSettings? {
        return ExternalSettings()
    }
}
