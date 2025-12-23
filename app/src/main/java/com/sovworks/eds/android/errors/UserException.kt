package com.sovworks.eds.android.errors

import android.content.Context

open class UserException : Exception {
    private val _messageId: Int
    private val _args: Array<Any>?
    private var _context: Context? = null

    constructor(context: Context?, messageId: Int) : super(
        context?.getString(
            messageId
        ) ?: ""
    ) {
        _messageId = messageId
        _args = null
    }

    constructor(context: Context, messageId: Int, cause: Throwable?) : super(
        context.getString(
            messageId
        ), cause
    ) {
        _messageId = messageId
        _args = null
    }

    constructor(defaultMessage: String?, messageId: Int, vararg args: Any) : super(defaultMessage) {
        _messageId = messageId
        _args = args
    }

    protected constructor(message: String?) : super(message) {
        _messageId = 0
        _args = null
    }

    protected constructor() {
        _messageId = 0
        _args = null
    }

    fun setContext(context: Context?) {
        _context = context
    }

    override fun getLocalizedMessage(): String {
        if (_messageId != 0 && _context != null) {
            val args: Array<Any?> = _args ?: arrayOfNulls(0)
            return _context!!.getString(_messageId, *args)
        }
        return super.getLocalizedMessage()
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
