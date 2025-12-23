package com.sovworks.eds.android.locations

import android.content.Context
import android.net.Uri
import android.net.Uri.Builder
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.locations.DeviceBasedLocation
import com.sovworks.eds.locations.LocationBase.ExternalSettings
import org.json.JSONException
import org.json.JSONObject

class ExternalStorageLocation : DeviceBasedLocation {
    class ExternalSettings : com.sovworks.eds.locations.LocationBase.ExternalSettings() {
        fun dontAskWritePermission(): Boolean {
            return _dontAskWritePermission
        }

        fun setDontAskWritePermission(`val`: Boolean) {
            _dontAskWritePermission = `val`
        }

        @Throws(JSONException::class)
        override fun saveToJSONObject(jo: JSONObject) {
            super.saveToJSONObject(jo)
            jo.put(SETTINGS_DONT_ASK_WRITE_PERMISSION, _dontAskWritePermission)
            if (documentsAPIUriString == null) jo.remove(SETTINGS_DOC_API_URI_STRING)
            else jo.put(
                SETTINGS_DOC_API_URI_STRING,
                documentsAPIUriString
            )
        }

        @Throws(JSONException::class)
        override fun loadFromJSONOjbect(jo: JSONObject) {
            super.loadFromJSONOjbect(jo)
            _dontAskWritePermission = jo.optBoolean(SETTINGS_DONT_ASK_WRITE_PERMISSION, false)
            documentsAPIUriString = jo.optString(SETTINGS_DOC_API_URI_STRING, null)
        }

        var documentsAPIUriString: String? = null
        private var _dontAskWritePermission = false

        companion object {
            private const val SETTINGS_DOC_API_URI_STRING = "documents_api_uri_string"
            private const val SETTINGS_DONT_ASK_WRITE_PERMISSION = "dont_ask_write_permission"
        }
    }

    constructor(context: Context, label: String?, mountPath: String?, currentPath: String?) : super(
        UserSettings.getSettings(context),
        label,
        mountPath,
        currentPath
    ) {
        _context = context
    }

    constructor(context: Context, uri: Uri) : super(UserSettings.getSettings(context), uri) {
        _context = context
    }

    override fun makeFullUri(): Builder? {
        return super.makeFullUri()!!.scheme(URI_SCHEME)
    }

    @get:Synchronized
    override val externalSettings: ExternalSettings?
        get() = super.externalSettings as ExternalSettings?

    override fun saveExternalSettings() {
        try {
            UserSettings.getSettings(_context)
                .setLocationSettingsString(id, externalSettings!!.save())
        } catch (e: JSONException) {
            throw RuntimeException("Settings serialization failed", e)
        }
    }

    override fun copy(): ExternalStorageLocation? {
        return ExternalStorageLocation(_context, title, rootPath, _currentPathString)
    }

    protected val _context: Context

    override fun loadExternalSettings(): ExternalSettings? {
        val res = ExternalSettings()
        res.load(UserSettings.getSettings(_context), id)
        return res
    }

    companion object {
        const val URI_SCHEME: String = "ext-st"
    }
}
