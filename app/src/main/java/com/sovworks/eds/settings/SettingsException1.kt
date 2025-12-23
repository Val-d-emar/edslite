package com.sovworks.eds.settings

import java.io.IOException

class SettingsException : IOException {
    constructor() : super("Settings file error")

    constructor(cause: Throwable?) : super("Settings file error") {
        initCause(cause)
    }

    companion object {
        /**  */
        private const val serialVersionUID = 1L
    }
}
