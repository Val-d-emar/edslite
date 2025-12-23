package com.sovworks.eds.android.errors

import android.content.Context
import com.sovworks.eds.android.R

class InputOutputException : UserException {
    constructor(context: Context?) : super(context, R.string.err_input_output)

    constructor(context: Context?, cause: Throwable?) : super(
        context,
        R.string.err_input_output,
        cause
    )

    companion object {
        /**
         *
         */
        private const val serialVersionUID = 1L
    }
}
