package com.sovworks.eds.android.errors

import android.content.Context
import com.sovworks.eds.android.R

class SettingsFileException(context: Context?) :
    UserException(context, R.string.err_failed_processing_settings_file) {
    companion object {
        /**
         *
         */
        private const val serialVersionUID = 1L
    }
}
