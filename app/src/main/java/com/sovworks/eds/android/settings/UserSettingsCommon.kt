package com.sovworks.eds.android.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import com.sovworks.eds.android.EdsApplication
import com.sovworks.eds.android.helpers.Util
import com.sovworks.eds.android.helpers.UtilBase.Companion.loadStringArrayFromString
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.crypto.SimpleCrypto
import com.sovworks.eds.settings.GlobalConfig
import com.sovworks.eds.settings.SettingsCommon
import com.sovworks.eds.settings.SettingsCommon.ExternalFileManagerInfo
import com.sovworks.eds.settings.SettingsCommon.InvalidSettingsPassword
import com.sovworks.eds.settings.SettingsCommon.LocationShortcutWidgetInfo
import org.json.JSONException
import java.security.SecureRandom

@SuppressLint("CommitPrefEdits", "ApplySharedPref")
abstract class UserSettingsCommon protected constructor(
    protected val _context: Context,
    private val _defaultSettings: SettingsCommon
) :
    SettingsCommon {
    override fun getLocationSettingsString(locationId: String): String? {
        return sharedPreferences.getString(
            LOCATION_SETTINGS_PREFIX + locationId,
            _defaultSettings.getLocationSettingsString(locationId)
        )
    }

    @SuppressLint("CommitPrefEdits")
    override fun setLocationSettingsString(locationId: String, data: String?) {
        val e = sharedPreferences.edit()
        if (data == null) e.remove(LOCATION_SETTINGS_PREFIX + locationId)
        else e.putString(LOCATION_SETTINGS_PREFIX + locationId, data)
        e.commit()
    }

    override fun getStoredLocations(): String? {
        return sharedPreferences.getString(
            LOCATIONS_LIST,
            _defaultSettings.storedLocations
        )
    }

    @SuppressLint("CommitPrefEdits")
    override fun setStoredLocations(locations: String) {
        val e = sharedPreferences.edit()
        e.putString(LOCATIONS_LIST, locations)
        e.commit()
    }

    override fun getMaxTempFileSize(): Int {
        return sharedPreferences.getInt(
            MAX_FILE_SIZE_TO_OPEN,
            _defaultSettings.maxTempFileSize
        )
    }

    override fun wipeTempFiles(): Boolean {
        return sharedPreferences.getBoolean(WIPE_TEMP_FILES, _defaultSettings.wipeTempFiles())
    }

    override fun showPreviews(): Boolean {
        return sharedPreferences.getBoolean(SHOW_PREVIEWS, _defaultSettings.showPreviews())
    }

    override fun getWorkDir(): String? {
        return sharedPreferences.getString(WORK_DIR, _defaultSettings.workDir)
    }

    override fun getLastViewedPromoVersion(): Int {
        return sharedPreferences.getInt(
            LAST_VIEWED_CHANGES,
            _defaultSettings.lastViewedPromoVersion
        )
    }

    override fun getInternalImageViewerMode(): Int {
        return sharedPreferences.getInt(
            USE_INTERNAL_IMAGE_VIEWER,
            _defaultSettings.internalImageViewerMode
        )
    }

    override fun getLocationShortcutWidgetInfo(widgetId: Int): LocationShortcutWidgetInfo {
        val data = sharedPreferences.getString(
            LOCATION_SHORTCUT_WIDGET_PREFIX + widgetId,
            null
        )
            ?: return _defaultSettings.getLocationShortcutWidgetInfo(widgetId)
        val i = LocationShortcutWidgetInfo()
        i.load(data)
        return i
    }

    @SuppressLint("CommitPrefEdits")
    override fun setLocationShortcutWidgetInfo(
        widgetId: Int,
        info: LocationShortcutWidgetInfo?
    ) {
        val editor = sharedPreferences.edit()
        if (info == null) editor.remove(LOCATION_SHORTCUT_WIDGET_PREFIX + widgetId)
        else editor.putString(LOCATION_SHORTCUT_WIDGET_PREFIX + widgetId, info.toString())
        editor.commit()
    }

    override fun disableLargeSceenLayouts(): Boolean {
        return sharedPreferences.getBoolean(
            DISABLE_WIDE_SCREEN_LAYOUTS,
            _defaultSettings.disableLargeSceenLayouts()
        )
    }

    override fun getFilesSortMode(): Int {
        return sharedPreferences.getInt(FILE_BROWSER_SORT_MODE, _defaultSettings.filesSortMode)
    }

    override fun getMaxContainerInactivityTime(): Int {
        return sharedPreferences.getInt(
            MAX_INACTIVITY_TIME,
            _defaultSettings.maxContainerInactivityTime
        )
    }

    override fun getExtensionsMimeMapString(): String? {
        return sharedPreferences.getString(
            EXTENSIONS_MIME,
            _defaultSettings.extensionsMimeMapString
        )
    }

    override fun isImageViewerFullScreenModeEnabled(): Boolean {
        return sharedPreferences.getBoolean(
            IMAGE_VIEWER_FULL_SCREEN_ENABLED,
            _defaultSettings.isImageViewerFullScreenModeEnabled
        )
    }

    override fun isImageViewerAutoZoomEnabled(): Boolean {
        return sharedPreferences.getBoolean(
            IMAGE_VIEWER_AUTO_ZOOM_ENABLED,
            _defaultSettings.isImageViewerAutoZoomEnabled
        )
    }

    override fun neverSaveHistory(): Boolean {
        return sharedPreferences.getBoolean(NEVER_SAVE_HISTORY, _defaultSettings.neverSaveHistory())
    }

    override fun disableDebugLog(): Boolean {
        return sharedPreferences.getBoolean(DISABLE_DEBUG_LOG, _defaultSettings.disableDebugLog())
    }

    override fun getVisitedHintSections(): List<String> {
        val s =
            sharedPreferences.getString(VISITED_HINT_SECTIONS, null)
                ?: return _defaultSettings.visitedHintSections

        return try {
            Util.loadStringArrayFromString(s)
        } catch (e: JSONException) {
            _defaultSettings.visitedHintSections
        }
    }

    override fun isHintDisabled(): Boolean {
        return sharedPreferences.getBoolean(DISABLE_HINTS, _defaultSettings.isHintDisabled)
    }

    override fun disableModifiedFilesBackup(): Boolean {
        return sharedPreferences.getBoolean(
            DISABLE_MODIFIED_FILES_BACKUP,
            _defaultSettings.disableModifiedFilesBackup()
        )
    }

    override fun isFlagSecureEnabled(): Boolean {
        return sharedPreferences.getBoolean(
            IS_FLAG_SECURE_ENABLED,
            _defaultSettings.isFlagSecureEnabled
        )
    }

    override fun alwaysForceClose(): Boolean {
        return sharedPreferences.getBoolean(FORCE_UNMOUNT, _defaultSettings.alwaysForceClose())
    }

    override fun getCurrentSettingsVersion(): Int {
        return sharedPreferences.getInt(CURRENT_SETTINGS_VERSION, -1)
    }

    @Throws(InvalidSettingsPassword::class)
    fun getProtectedString(key: String?): String? {
        val pd = getProtectedData(key)
        return if (pd == null) null else String(pd)
    }

    @Throws(InvalidSettingsPassword::class)
    fun getProtectedData(key: String?): ByteArray? {
        if (_resetProtectedSettings) return null
        val encryptedString = sharedPreferences.getString(key, null) ?: return null
        try {
            return SimpleCrypto.decrypt(settingsProtectionKey, encryptedString)
        } catch (e: Exception) {
            throw InvalidSettingsPassword()
        }
    }

    @SuppressLint("CommitPrefEdits")
    @Throws(InvalidSettingsPassword::class)
    fun setProtectedField(key: String?, value: String) {
        sharedPreferences.edit().putString(
            key,
            SimpleCrypto.encrypt(settingsProtectionKey, value.toByteArray())
        ).commit()
    }

    @SuppressLint("CommitPrefEdits")
    @Throws(InvalidSettingsPassword::class)
    fun setProtectedField(key: String?, value: ByteArray?) {
        sharedPreferences.edit().putString(
            key,
            SimpleCrypto.encrypt(settingsProtectionKey, value)
        ).commit()
    }

    @Synchronized
    @Throws(InvalidSettingsPassword::class)
    override fun getSettingsProtectionKey(): SecureBuffer {
        if (_settingsProtectionKey == null) {
            var isUser = false
            var encryptedString = sharedPreferences.getString(SETTINGS_PROTECTION_KEY_USER, null)
            if (encryptedString == null) {
                encryptedString = sharedPreferences.getString(SETTINGS_PROTECTION_KEY_AUTO, null)
                if (encryptedString == null) {
                    encryptedString = sharedPreferences.getString(SETTINGS_PROTECTION_KEY_OLD, null)
                    isUser = true
                }
            } else isUser = true
            if (encryptedString == null) saveSettingsProtectionKey()
            else {
                try {
                    val settingsPassword = settingsPassword
                    try {
                        _settingsProtectionKey = SecureBuffer(
                            SimpleCrypto.decryptWithPassword(
                                settingsPassword,
                                encryptedString
                            )
                        )
                        if (!isSettingsPasswordValid) {
                            clearSettingsProtectionKey()
                            if (isUser) throw InvalidSettingsPassword()
                            else saveSettingsProtectionKey()
                        }
                    } finally {
                        SecureBuffer.eraseData(settingsPassword)
                    }
                } catch (e: Exception) {
                    clearSettingsProtectionKey()
                    val e1 = InvalidSettingsPassword()
                    e1.initCause(e)
                    throw e1
                }
            }
        }
        return _settingsProtectionKey!!
    }

    val isSettingsPasswordValid: Boolean
        get() {
            val pf: String?
            try {
                pf = getProtectedString(SETTINGS_PROTECTION_KEY_CHECK)
            } catch (ignored: InvalidSettingsPassword) {
                return false
            }
            return CHECK_PHRASE == pf
        }

    override fun getCurrentTheme(): Int {
        return sharedPreferences.getInt(THEME, _defaultSettings.filesSortMode)
    }

    override fun getExternalFileManagerInfo(): ExternalFileManagerInfo? {
        val data =
            sharedPreferences.getString(EXTERNAL_FILE_MANAGER, null)
                ?: return _defaultSettings.externalFileManagerInfo
        val i = ExternalFileManagerInfo()
        try {
            i.load(data)
        } catch (e: JSONException) {
            return null
        }
        return i
    }

    override fun dontUseContentProvider(): Boolean {
        return sharedPreferences.getBoolean(
            DONT_USE_CONTENT_PROVIDER,
            _defaultSettings.dontUseContentProvider()
        )
    }

    override fun forceTempFiles(): Boolean {
        return sharedPreferences.getBoolean(FORCE_TEMP_FILES, _defaultSettings.forceTempFiles())
    }

    @Synchronized
    @Throws(InvalidSettingsPassword::class)
    fun saveSettingsProtectionKey() {
        if (_settingsProtectionKey == null) {
            val sr = SecureRandom()
            val k = ByteArray(32)
            sr.nextBytes(k)
            _settingsProtectionKey = SecureBuffer(k)
        }
        val key = _settingsProtectionKey!!.dataArray ?: throw InvalidSettingsPassword()
        try {
            var pass = userSettingsPassword
            if (pass != null) {
                try {
                    sharedPreferences.edit().putString(
                        SETTINGS_PROTECTION_KEY_USER,
                        SimpleCrypto.encryptWithPassword(pass, key)
                    ).remove
                    (SETTINGS_PROTECTION_KEY_AUTO).remove
                    (SETTINGS_PROTECTION_KEY_OLD).commit
                    ()
                } finally {
                    SecureBuffer.eraseData(pass)
                }
            } else {
                pass = autoSettingsPassword
                try {
                    sharedPreferences.edit().putString(
                        SETTINGS_PROTECTION_KEY_AUTO,
                        SimpleCrypto.encryptWithPassword(pass, key)
                    ).remove
                    (SETTINGS_PROTECTION_KEY_USER).remove
                    (SETTINGS_PROTECTION_KEY_OLD).commit
                    ()
                } finally {
                    SecureBuffer.eraseData(pass)
                }
            }
        } finally {
            SecureBuffer.eraseData(key)
        }
        _resetProtectedSettings = false
        setProtectedField(SETTINGS_PROTECTION_KEY_CHECK, CHECK_PHRASE)
    }

    @Synchronized
    fun clearSettingsProtectionKey() {
        if (_settingsProtectionKey != null) {
            _settingsProtectionKey!!.close()
            _settingsProtectionKey = null
        }
    }

    val sharedPreferences: SharedPreferences

    private var _settingsProtectionKey: SecureBuffer? = null
    private var _resetProtectedSettings = false

    init {
        sharedPreferences = if (GlobalConfig.isDebug())
            _context.getSharedPreferences("debug", 0)
        else
            _context.getSharedPreferences(PREFS_NAME, 0)
    }

    private val userSettingsPassword: ByteArray?
        get() {
            val mp: SecureBuffer = EdsApplication.getMasterPassword()
            if (mp != null) {
                val mpd = mp.dataArray
                if (mpd != null) return mpd
            }
            return null
        }

    private val autoSettingsPassword: ByteArray
        get() = Util.getDefaultSettingsPassword(_context)
            .toByteArray()

    private val settingsPassword: ByteArray
        get() {
            val res = userSettingsPassword
            return res ?: autoSettingsPassword
        }

    companion object {
        const val LOCATION_SETTINGS_PREFIX: String = "location_settings_"
        const val LOCATIONS_LIST: String = "locations_list"
        const val MAX_FILE_SIZE_TO_OPEN: String = "max_file_size_to_open"
        const val WIPE_TEMP_FILES: String = "wipe_temp_files"
        const val SHOW_PREVIEWS: String = "show_previews"
        const val WORK_DIR: String = "work_dir"
        const val LAST_VIEWED_CHANGES: String = "last_viewed_changes"
        const val USE_INTERNAL_IMAGE_VIEWER: String = "use_internal_image_viewer"
        const val LOCATION_SHORTCUT_WIDGET_PREFIX: String = "location_shortcut_widget_"
        const val DISABLE_WIDE_SCREEN_LAYOUTS: String = "disable_wide_screen_layouts"
        const val FILE_BROWSER_SORT_MODE: String = "file_browser_sort_mode"
        const val MAX_INACTIVITY_TIME: String = "max_inactivity_time"
        const val EXTENSIONS_MIME: String = "extensions_mime"
        const val IMAGE_VIEWER_FULL_SCREEN_ENABLED: String = "image_viewer_full_screen_enabled"
        const val IMAGE_VIEWER_AUTO_ZOOM_ENABLED: String = "image_viewer_auto_zoom_enabled"
        const val NEVER_SAVE_HISTORY: String = "never_save_history"
        const val DISABLE_DEBUG_LOG: String = "disable_debug_log"
        const val VISITED_HINT_SECTIONS: String = "visited_hint_sections"
        const val DISABLE_HINTS: String = "disable_hints"
        const val DISABLE_MODIFIED_FILES_BACKUP: String = "disable_modified_files_backup"
        const val IS_FLAG_SECURE_ENABLED: String = "is_flag_secure_enabled"
        const val FORCE_UNMOUNT: String = "force_unmount"
        const val CURRENT_SETTINGS_VERSION: String = "current_settings_version"
        const val SETTINGS_PROTECTION_KEY_OLD: String = "settings_protection_key"
        const val SETTINGS_PROTECTION_KEY_USER: String = "settings_protection_key_user"
        const val SETTINGS_PROTECTION_KEY_AUTO: String = "settings_protection_key_auto"
        const val THEME: String = "theme"
        const val EXTERNAL_FILE_MANAGER: String = "external_file_manager"
        const val DONT_USE_CONTENT_PROVIDER: String = "dont_use_content_provider"
        const val FORCE_TEMP_FILES: String = "force_temp_files"

        const val SETTINGS_PROTECTION_KEY_CHECK: String = "protection_key_check"

        const val PREFS_NAME: String = "com.sovworks.eds.PREFERENCES"


        fun isWideScreenLayout(settings: SettingsCommon, activity: Activity): Boolean {
            return !settings.disableLargeSceenLayouts() && activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }

        private const val CHECK_PHRASE = "valid pass"
    }
}