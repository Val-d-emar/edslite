package com.sovworks.eds.android.errors

import android.content.Context
import com.sovworks.eds.android.R

class ClosedContainerException(context: Context?) :
    UserException(context, R.string.err_target_container_is_closed) {
    companion object {
        /**
         *
         */
        private const val serialVersionUID = 4482745672661567457L
    }
}
