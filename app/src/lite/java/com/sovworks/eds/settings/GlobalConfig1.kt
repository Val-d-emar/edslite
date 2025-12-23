package com.sovworks.eds.settings

object GlobalConfig : GlobalConfigCommon() {
    val isTest: Boolean
        get() = false

    const val DONATIONS_URL: String = "https://sovworks.com/eds/donations.php"
    const val SOURCE_CODE_URL: String = "https://github.com/sovworks/edslite"
    const val FULL_VERSION_URL: String =
        "https://play.google.com/store/apps/details?id=com.sovworks.eds.android"
}
