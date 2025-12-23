package com.sovworks.eds.android.errors

import android.content.Context
import com.sovworks.eds.android.R

class NoFreeSpaceLeftException(context: Context?) :
    UserException(context, R.string.no_free_space_left) {
    companion object {
        /**
         *
         */
        private const val serialVersionUID = 1L
    }
}