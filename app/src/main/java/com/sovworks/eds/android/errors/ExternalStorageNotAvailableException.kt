package com.sovworks.eds.android.errors

import android.content.Context
import com.sovworks.eds.android.R

class ExternalStorageNotAvailableException(context: Context?) :
    UserException(context, R.string.err_external_storage_is_not_available) {
    companion object {
        /**
         *
         */
        private const val serialVersionUID = 1L
    }
}
