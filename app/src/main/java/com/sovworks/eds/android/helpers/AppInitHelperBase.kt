package com.sovworks.eds.android.helpers

import android.annotation.SuppressLint
import com.sovworks.eds.android.locations.ContainerBasedLocation
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.UserSettingsCommon
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.settings.Settings
import com.trello.rxlifecycle2.components.RxActivity
import io.reactivex.Completable
import io.reactivex.CompletableEmitter

abstract class AppInitHelperBase
internal constructor(@JvmField val _activity: RxActivity, @JvmField val _initFinished: CompletableEmitter) {
    protected val _settings: UserSettings = UserSettings.getSettings(_activity)

    @SuppressLint("ApplySharedPref")
    fun convertLegacySettings() {
        var curSettingsVersion = _settings.currentSettingsVersion
        if (curSettingsVersion >= Settings.VERSION) return

        if (curSettingsVersion < 0) {
            if (_settings.lastViewedPromoVersion > 160) _settings.sharedPreferences.edit()
                .putInt(UserSettingsCommon.CURRENT_SETTINGS_VERSION, Settings.VERSION).commit()
            else curSettingsVersion = 1
        }

        if (curSettingsVersion < 2) updateSettingsV2()
        if (curSettingsVersion < 3) updateSettingsV3()
        _settings.getSharedPreferences
        ().edit
        ().putInt
        (UserSettings.CURRENT_SETTINGS_VERSION, com.sovworks.eds.settings.Settings.VERSION).commit
        ()
    }

    protected fun updateSettingsV2() {
        makeContainersVisible()
    }

    private fun updateSettingsV3() {
        convertEncAlgName()
    }

    private fun convertEncAlgName() {
        val lm = LocationsManager.getLocationsManager(_activity)
        for (l in lm.getLoadedLocations(false)) if (l is ContainerBasedLocation) {
            val externalSettings = l.externalSettings
            val encAlg = externalSettings.encEngineName ?: continue
            when (encAlg) {
                "aes-twofish-serpent-xts-plain64" -> {
                    externalSettings.encEngineName = "serpent-twofish-aes-xts-plain64"
                    l.saveExternalSettings()
                }

                "serpent-twofish-aes-xts-plain64" -> {
                    externalSettings.encEngineName = "aes-twofish-serpent-xts-plain64"
                    l.saveExternalSettings()
                }

                "twofish-aes-xts-plain64" -> {
                    externalSettings.encEngineName = "aes-twofish-xts-plain64"
                    l.saveExternalSettings()
                }

                "aes-serpent-xts-plain64" -> {
                    externalSettings.encEngineName = "serpent-aes-xts-plain64"
                    l.saveExternalSettings()
                }

                "serpent-twofish-xts-plain64" -> {
                    externalSettings.encEngineName = "twofish-serpent-xts-plain64"
                    l.saveExternalSettings()
                }
            }
        }
    }


    private fun makeContainersVisible() {
        val lm = LocationsManager.getLocationsManager(_activity)
        for (l in lm.getLoadedLocations(false)) if (l is ContainerBasedLocation && !l.externalSettings.isVisibleToUser) {
            l.externalSettings.isVisibleToUser = true
            l.saveExternalSettings()
        }
    }

    companion object {
        @JvmStatic
        fun createObservable(activity: RxActivity?): Completable {
            return Completable.create { emitter: CompletableEmitter? ->
                val initHelper = AppInitHelper(activity, emitter)
                initHelper.startInitSequence()
            }
        }
    }
}
