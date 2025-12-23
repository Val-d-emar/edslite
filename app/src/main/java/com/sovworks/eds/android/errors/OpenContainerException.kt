package com.sovworks.eds.android.errors

import android.content.Context
import com.sovworks.eds.android.R

class OpenContainerException(context: Context?) :
    UserException(context, R.string.err_failed_opening_container) {
    companion object {
        /**
         *
         */
        private const val serialVersionUID = 1L
    }
}
