package com.sovworks.eds.settings

import com.sovworks.eds.android.BuildConfig

open class GlobalConfigCommon {
    companion object {
        val isDebug: Boolean
            get() = BuildConfig.DEBUG

        const val FB_PREVIEW_WIDTH: Int = 40
        const val FB_PREVIEW_HEIGHT: Int = 40
        const val CLEAR_MASTER_PASS_INACTIVITY_TIMEOUT: Int = 20 * 60 * 1000
        const val SUPPORT_EMAIL: String = "eds@sovworks.com"
        const val HELP_URL: String = "https://sovworks.com/eds/managing-containers.php"
        const val EXFAT_MODULE_URL: String = "https://github.com/sovworks/edsexfat"
    }
}
