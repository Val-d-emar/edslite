package com.sovworks.eds.android.settings

import android.content.Context
import com.sovworks.eds.settings.DefaultSettings
import com.sovworks.eds.settings.Settings

class UserSettings(context: Context) : UserSettingsCommon(context, _defaultSettings),
    Settings {
    companion object {
        @Synchronized
        fun getSettings(context: Context): UserSettings {
            if (_instance == null) _instance = UserSettings(context)

            return _instance!!
        }

        @Synchronized
        fun closeSettings() {
            if (_instance != null) _instance!!.clearSettingsProtectionKey()
            _instance = null
        }

        private val _defaultSettings: Settings = DefaultSettings()

        private var _instance: UserSettings? = null
    }
}
