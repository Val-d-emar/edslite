package com.sovworks.eds.settings

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.settings.SettingsCommon.ExternalFileManagerInfo
import com.sovworks.eds.settings.SettingsCommon.InvalidSettingsPassword
import com.sovworks.eds.settings.SettingsCommon.LocationShortcutWidgetInfo
import java.util.Arrays

open class DefaultSettingsCommon : SettingsCommon {
    override fun getLocationSettingsString(locationId: String?): String {
        return ""
    }

    override fun setLocationSettingsString(locationId: String?, data: String?) {}

    override var storedLocations: String?
        get() = ""
        set(locations) {}

    override val maxTempFileSize: Int
        get() = 100

    override fun wipeTempFiles(): Boolean {
        return true
    }

    override val workDir: String?
        get() = ""

    override val lastViewedPromoVersion: Int
        get() = 0

    override val internalImageViewerMode: Int
        get() = SettingsCommon.Companion.USE_INTERNAL_IMAGE_VIEWER_VIRT_FS

    override fun getLocationShortcutWidgetInfo(widgetId: Int): LocationShortcutWidgetInfo? {
        return null
    }

    override fun setLocationShortcutWidgetInfo(widgetId: Int, info: LocationShortcutWidgetInfo?) {}

    override fun forceTempFiles(): Boolean {
        return true
    }

    override fun disableLargeSceenLayouts(): Boolean {
        return false
    }

    override val filesSortMode: Int
        get() = SettingsCommon.Companion.FB_SORT_FILENAME_ASC

    override val maxContainerInactivityTime: Int
        get() = -1

    override fun showPreviews(): Boolean {
        return true
    }

    override val extensionsMimeMapString: String?
        get() = ""

    override val isImageViewerFullScreenModeEnabled: Boolean
        get() = false

    override val isImageViewerAutoZoomEnabled: Boolean
        get() = false

    override fun neverSaveHistory(): Boolean {
        return false
    }

    override fun disableDebugLog(): Boolean {
        return false
    }

    override val visitedHintSections: List<String?>?
        get() = Arrays.asList(*arrayOfNulls(0))

    override val isHintDisabled: Boolean
        get() {
            if (GlobalConfig.isDebug()) return true
            val lv = lastViewedPromoVersion
            return lv > 124
        }

    override fun disableModifiedFilesBackup(): Boolean {
        return false
    }

    @get:Throws(InvalidSettingsPassword::class)
    override val settingsProtectionKey: SecureBuffer?
        get() = null

    override val currentSettingsVersion: Int
        get() = Settings.VERSION

    override val isFlagSecureEnabled: Boolean
        get() = false

    override fun alwaysForceClose(): Boolean {
        return false
    }

    override val currentTheme: Int
        get() = SettingsCommon.Companion.THEME_DEFAULT

    override val externalFileManagerInfo: ExternalFileManagerInfo?
        get() = null

    override fun dontUseContentProvider(): Boolean {
        return VERSION.SDK_INT < VERSION_CODES.N
    }
}
