package com.sovworks.eds.android.helpers

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.provider.Settings.Secure
import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.UserSettingsCommon.getCurrentTheme
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.crypto.SimpleCrypto.calcStringMD5
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.locations.Openable
import com.sovworks.eds.settings.SettingsCommon
import java.io.IOException

object Util : UtilBase() {
    @Throws(IOException::class)
    fun getPassword(
        args: Bundle, lm: LocationsManager?
    ): SecureBuffer? {
        return args.getParcelable(Openable.PARAM_PASSWORD)
    }

    fun setTheme(act: Activity) {
        val theme: Int = UserSettings.getSettings(act.applicationContext).getCurrentTheme()
        act.setTheme(if (theme == SettingsCommon.THEME_DARK) R.style.Theme_EDS_Dark else R.style.Theme_EDS)
    }

    fun getDefaultSettingsPassword(context: Context): String {
        try {
            return calcStringMD5(
                Secure.getString(context.contentResolver, Secure.ANDROID_ID)
            )
        } catch (e: Exception) {
            log(e)
        }
        return ""
    }
}
