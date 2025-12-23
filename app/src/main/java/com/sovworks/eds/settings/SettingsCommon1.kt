package com.sovworks.eds.settings

import com.sovworks.eds.crypto.SecureBuffer
import org.json.JSONException
import org.json.JSONObject

interface SettingsCommon {
    class InvalidSettingsPassword : Exception()

    class LocationShortcutWidgetInfo {
        @JvmField
        var widgetTitle: String? = null
        @JvmField
        var locationUriString: String? = null

        @Throws(JSONException::class)
        fun save(): String {
            val jo = JSONObject()
            jo.put(SETTINGS_WIDGET_TITLE, widgetTitle)
            jo.put(SETTINGS_LOCATION_URI, locationUriString)
            return jo.toString()
        }

        fun load(data: String) {
            val jo: JSONObject
            try {
                jo = JSONObject(data)
                widgetTitle = jo.optString(SETTINGS_WIDGET_TITLE)
                locationUriString = jo.optString(SETTINGS_LOCATION_URI)
            } catch (ignored: JSONException) {
            }
        }

        override fun toString(): String {
            return try {
                save()
            } catch (e: JSONException) {
                "error"
            }
        }

        companion object {
            private const val SETTINGS_WIDGET_TITLE = "widget_title"
            private const val SETTINGS_LOCATION_URI = "location_uri"
        }
    }

    class ExternalFileManagerInfo {
        var packageName: String? = null
        var className: String? = null
        var action: String? = null
        var mimeType: String? = null

        @Throws(JSONException::class)
        fun save(jo: JSONObject) {
            jo.put(SETTINGS_PACKAGE_NAME, packageName)
            jo.put(SETTINGS_CLASS_NAME, className)
            jo.put(SETTINGS_ACTION, action)
            jo.put(SETTINGS_MIME_TYPE, mimeType)
        }

        fun load(jo: JSONObject) {
            packageName = jo.optString(SETTINGS_PACKAGE_NAME)
            className = jo.optString(SETTINGS_CLASS_NAME)
            action = jo.optString(SETTINGS_ACTION)
            mimeType = jo.optString(SETTINGS_MIME_TYPE)
        }

        @Throws(JSONException::class)
        fun load(s: String) {
            val jo = JSONObject(s)
            load(jo)
        }

        @Throws(JSONException::class)
        fun save(): String {
            val jo = JSONObject()
            save(jo)
            return jo.toString()
        }

        companion object {
            private const val SETTINGS_PACKAGE_NAME = "package_name"
            private const val SETTINGS_CLASS_NAME = "class_name"
            private const val SETTINGS_ACTION = "action"
            private const val SETTINGS_MIME_TYPE = "mime_type"
        }
    }

    var storedLocations: String?

    fun getLocationSettingsString(locationId: String?): String?

    fun setLocationSettingsString(locationId: String?, data: String?)

    val maxTempFileSize: Int

    fun wipeTempFiles(): Boolean

    fun showPreviews(): Boolean

    val workDir: String?

    val extensionsMimeMapString: String?

    val lastViewedPromoVersion: Int

    val internalImageViewerMode: Int

    fun getLocationShortcutWidgetInfo(widgetId: Int): LocationShortcutWidgetInfo?

    fun setLocationShortcutWidgetInfo(widgetId: Int, info: LocationShortcutWidgetInfo?)

    fun disableLargeSceenLayouts(): Boolean

    val filesSortMode: Int

    val maxContainerInactivityTime: Int

    val isImageViewerFullScreenModeEnabled: Boolean

    val isImageViewerAutoZoomEnabled: Boolean

    fun neverSaveHistory(): Boolean

    fun disableDebugLog(): Boolean

    fun disableModifiedFilesBackup(): Boolean

    val visitedHintSections: List<String>

    val isHintDisabled: Boolean

    @get:Throws(InvalidSettingsPassword::class)
    val settingsProtectionKey: SecureBuffer?

    val isFlagSecureEnabled: Boolean

    val currentSettingsVersion: Int

    fun alwaysForceClose(): Boolean

    val currentTheme: Int

    val externalFileManagerInfo: ExternalFileManagerInfo?

    fun dontUseContentProvider(): Boolean

    fun forceTempFiles(): Boolean

    companion object {
        const val VERSION: Int = 3

        const val USE_INTERNAL_IMAGE_VIEWER_NEVER: Int = 0
        const val USE_INTERNAL_IMAGE_VIEWER_VIRT_FS: Int = 1
        const val USE_INTERNAL_IMAGE_VIEWER_ALWAYS: Int = 2

        const val FB_SORT_DATE_ASC: Int = 2
        const val FB_SORT_DATE_DESC: Int = 3
        const val FB_SORT_SIZE_ASC: Int = 4
        const val FB_SORT_SIZE_DESC: Int = 5
        const val FB_SORT_FILENAME_ASC: Int = 0
        const val FB_SORT_FILENAME_DESC: Int = 1
        const val FB_SORT_FILENAME_NUM_ASC: Int = 6
        const val FB_SORT_FILENAME_NUM_DESC: Int = 7

        const val THEME_DEFAULT: Int = 0
        const val THEME_DARK: Int = 1
    }
}
