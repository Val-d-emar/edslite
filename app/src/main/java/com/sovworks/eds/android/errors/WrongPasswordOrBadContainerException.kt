package com.sovworks.eds.android.errors

import android.content.Context
import com.sovworks.eds.android.R

class WrongPasswordOrBadContainerException(context: Context?) :
    UserException(context, R.string.bad_container_file_or_wrong_password) {
    companion object {
        /**
         *
         */
        private const val serialVersionUID = 1L
    }
}
